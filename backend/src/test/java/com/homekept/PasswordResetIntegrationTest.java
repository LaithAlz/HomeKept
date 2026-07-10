package com.homekept;

import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.identity.AuthController;
import com.homekept.identity.ForgotPasswordRateLimiter;
import com.homekept.identity.PasswordResetToken;
import com.homekept.identity.PasswordResetTokenRepository;
import com.homekept.identity.PasswordResetTokenService;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the forgot/reset password flow (api-contract.md §Auth).
 * Runs against a real Postgres via Testcontainers (@ServiceConnection) and a recording
 * {@link com.homekept.notification.EmailSender} (no real SendGrid).
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/auth/forgot — existing email: 202, mints a token, sends one email</li>
 *   <li>POST /api/auth/forgot — unknown email: 202, identical response, sends no email
 *       (no enumeration)</li>
 *   <li>POST /api/auth/forgot — only the token hash is persisted, never the raw token</li>
 *   <li>POST /api/auth/forgot — rate limit: 6th attempt from the same IP → 429</li>
 *   <li>POST /api/auth/reset — happy path: sets the new password, revokes existing refresh
 *       tokens, sets fresh auth cookies, auto-signs-in an ACTIVE user</li>
 *   <li>POST /api/auth/reset — non-ACTIVE user: password is still updated, but no auto
 *       sign-in (no cookies set) — mirrors the login/refresh ACTIVE gate (#115)</li>
 *   <li>POST /api/auth/reset — a successful reset invalidates the user's other outstanding
 *       reset tokens, not just the one used (#115)</li>
 *   <li>POST /api/auth/reset — re-using a consumed token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — expired token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — garbage token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — password too short → 400</li>
 *   <li>POST /api/auth/forgot — known and unknown email both pad to the same fixed
 *       response-time budget, closing the enumeration-timing oracle regardless of whether
 *       the outbound SendGrid send is configured (#115, #120)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeEmailSenderConfig.class})
class PasswordResetIntegrationTest {

    private static final String FORGOT_URL  = "/api/auth/forgot";
    private static final String RESET_URL   = "/api/auth/reset";
    private static final String LOGIN_URL   = "/api/auth/login";
    private static final String REFRESH_URL = "/api/auth/refresh";

    // Must match src/test/resources/application.yml app.jwt.signing-key — used to hand-craft
    // an already-expired, correctly-signed fixture token (same idea as StripeWebhookIntegrationTest
    // hand-signing fixture payloads with the test webhook secret).
    private static final String TEST_SIGNING_KEY = "test-only-not-a-real-signing-key-placeholder-xx";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordResetTokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    @Autowired RecordingEmailSender email;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        email.reset();
        forgotPasswordRateLimiter.reset("127.0.0.1");
        createdUserIds.clear();
    }

    @AfterEach
    void tearDown() {
        // password_reset_tokens and refresh_tokens are ON DELETE CASCADE.
        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ── POST /api/auth/forgot ─────────────────────────────────────────────────

    @Test
    void forgot_existingEmail_returns202_andSendsOneResetEmail() throws Exception {
        createTestUser("forgot-known@test.local", "OldPassword1");

        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-known@test.local\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo("forgot-known@test.local");
        assertThat(email.sent.get(0).subject()).isEqualTo("Reset your HomeKept password");
        assertThat(email.sent.get(0).htmlBody()).contains("/reset-password?token=");
    }

    @Test
    void forgot_unknownEmail_returns202_sameStatusAsKnownEmail_andSendsNoEmail() throws Exception {
        // CRITICAL: identical status/body to the known-email case — no enumeration.
        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-unknown@test.local\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(email.sent).isEmpty();
    }

    @Test
    void forgot_existingEmail_persistsOnlyTheTokenHash_neverTheRawToken() throws Exception {
        User user = createTestUser("forgot-hash@test.local", "OldPassword1");

        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-hash@test.local\"}"))
                .andExpect(status().isAccepted());

        assertThat(email.sent).hasSize(1);
        String body = email.sent.get(0).htmlBody();
        String rawToken = extractTokenFromLink(body);

        // Raw token must not be discoverable via a hash lookup (i.e. it isn't stored raw).
        assertThat(tokenRepository.findByTokenHash(rawToken)).isEmpty();

        // The hash of the raw token must be exactly what's persisted, tied to this user.
        String hash = sha256Hex(rawToken);
        PasswordResetToken stored = tokenRepository.findByTokenHash(hash).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
        assertThat(stored.isConsumed()).isFalse();
    }

    @Test
    void forgot_rateLimitExceeded_returns429() throws Exception {
        for (int i = 0; i < ForgotPasswordRateLimiter.MAX_ATTEMPTS; i++) {
            forgotPasswordRateLimiter.tryConsume("127.0.0.1");
        }
        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-ratelimit@test.local\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void forgot_blankEmail_returns400() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void forgot_knownAndUnknownEmail_bothPadToTheSameConstantTimeBudget() throws Exception {
        // #115 finding 1 (fix-loop 1): a one-sided delay on only the unknown-email branch
        // would invert the oracle, because SendGrid is unconfigured by default (#120) — the
        // known-email branch's "send" is a few-ms log-and-skip in this test environment too
        // (RecordingEmailSender). The fix must pad BOTH branches to the same fixed budget, so
        // this test asserts both reach it, not just one side of a floor.
        createTestUser("forgot-timing-known@test.local", "OldPassword1");

        long knownElapsedMs = timeForgotRequest("forgot-timing-known@test.local");
        long unknownElapsedMs = timeForgotRequest("forgot-timing-unknown-budget@test.local");

        // Behavioral contract, not a precise/flaky wall-clock number: both branches must reach
        // (at least) the budget minus its jitter, with a small tolerance for scheduling noise.
        long minExpectedMs = AuthController.FORGOT_RESPONSE_BUDGET_MS - AuthController.FORGOT_RESPONSE_JITTER_MS - 20;
        assertThat(knownElapsedMs).isGreaterThanOrEqualTo(minExpectedMs);
        assertThat(unknownElapsedMs).isGreaterThanOrEqualTo(minExpectedMs);
    }

    // ── POST /api/auth/reset ──────────────────────────────────────────────────

    @Test
    void reset_happyPath_setsNewPassword_revokesOldRefreshTokens_andSetsCookies() throws Exception {
        User user = createTestUser("reset-happy@test.local", "OldPassword1");

        // Log in first to obtain a pre-existing refresh token that must be revoked by reset.
        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset-happy@test.local\",\"password\":\"OldPassword1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefreshToken = extractCookieValue(
                loginResult.getResponse().getHeaders("Set-Cookie"), "hk_refresh");

        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"));

        // New password takes effect.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword2", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isFalse();

        // Token is consumed.
        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isTrue();

        // The refresh token issued before the reset must now be revoked.
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void reset_nonActiveUser_updatesPassword_butDoesNotAutoSignIn() throws Exception {
        // Mirrors the login/refresh ACTIVE gate (#115 finding 2): the password change must
        // still happen, but a non-ACTIVE user must not be auto-signed-in via reset — that
        // would be a future login-lockout bypass once a suspend feature ships.
        User user = createTestUserWithStatus("reset-suspended@test.local", "OldPassword1", UserStatus.SUSPENDED);
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("hk_access"))
                .andExpect(cookie().doesNotExist("hk_refresh"));

        // Password is still changed even though the user isn't auto-signed-in.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword2", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isFalse();

        // The token is still consumed (single-use is unaffected by the status gate).
        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isTrue();
    }

    @Test
    void reset_activeUser_autoSignsIn_setsCookies() throws Exception {
        // Contrast case for reset_nonActiveUser_updatesPassword_butDoesNotAutoSignIn — an
        // ACTIVE user's behavior must be unchanged: fresh cookies ARE set.
        User user = createTestUser("reset-active@test.local", "OldPassword1");
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"));
    }

    @Test
    void reset_successfulReset_invalidatesTheUsersOtherOutstandingTokens() throws Exception {
        User user = createTestUser("reset-invalidate-others@test.local", "OldPassword1");
        PasswordResetTokenService.MintResult firstMint = tokenService.mint(user);
        PasswordResetTokenService.MintResult secondMint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + firstMint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk());

        // The second token, minted earlier for the same user and still within its 30-minute
        // window, must now be rejected — a successful reset retires ALL of that user's
        // outstanding tokens, not just the one used (#115 finding 3).
        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + secondMint.rawToken() + "\",\"password\":\"AnotherPassword3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        PasswordResetToken secondToken = tokenRepository.findById(secondMint.tokenId()).orElseThrow();
        assertThat(secondToken.isConsumed()).isTrue();
    }

    @Test
    void reset_consumedToken_returns400InvalidToken() throws Exception {
        User user = createTestUser("reset-reuse@test.local", "OldPassword1");
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk());

        // Second use of the same (now-consumed) token must be rejected.
        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"AnotherPassword3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void reset_expiredToken_returns400InvalidToken() throws Exception {
        User user = createTestUser("reset-expired@test.local", "OldPassword1");

        long pastExpEpoch = Instant.now().minusSeconds(60).getEpochSecond();
        String payload = "userId=" + user.getId() + "&nonce=deadbeefdeadbeef&exp=" + pastExpEpoch;
        String rawToken = buildSignedToken(payload);

        PasswordResetToken expiredToken = new PasswordResetToken(
                user, sha256Hex(rawToken), Instant.now().minusSeconds(60));
        tokenRepository.save(expiredToken);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        // The password must be unchanged.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void reset_garbageToken_returns400InvalidToken() throws Exception {
        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage.token\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void reset_passwordTooShort_returns400() throws Exception {
        User user = createTestUser("reset-short@test.local", "OldPassword1");
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        // The token must remain unconsumed and the password unchanged — validation fails
        // before the token is ever touched.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();

        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createTestUser(String email, String rawPassword) {
        return createTestUserWithStatus(email, rawPassword, UserStatus.ACTIVE);
    }

    private User createTestUserWithStatus(String email, String rawPassword, UserStatus status) {
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(rawPassword), "Test", "User",
                        Role.CUSTOMER, status));
        createdUserIds.add(user.getId());
        return user;
    }

    /** Times a POST /api/auth/forgot round-trip in milliseconds, wall-clock. */
    private long timeForgotRequest(String email) throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }

    /** Extracts the raw token query param from a {@code /reset-password?token=...} link. */
    private String extractTokenFromLink(String htmlBody) {
        int idx = htmlBody.indexOf("/reset-password?token=");
        assertThat(idx).isGreaterThan(-1);
        int start = idx + "/reset-password?token=".length();
        int end = start;
        while (end < htmlBody.length() && htmlBody.charAt(end) != '"' && htmlBody.charAt(end) != '&') {
            end++;
        }
        return htmlBody.substring(start, end);
    }

    /**
     * Hand-signs a fixture reset token matching PasswordResetTokenService's format, so a
     * PasswordResetToken row can be constructed directly for the expired-token test case.
     */
    private String buildSignedToken(String payload) throws Exception {
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmacBytes = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        String hmac = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        return encodedPayload + "." + hmac;
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }
}
