package com.homekept;

import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.common.Hashing;
import com.homekept.identity.AuthController;
import com.homekept.identity.ForgotPasswordRateLimiter;
import com.homekept.identity.PasswordResetToken;
import com.homekept.identity.PasswordResetTokenRepository;
import com.homekept.identity.PasswordResetTokenService;
import com.homekept.identity.Role;
import com.homekept.identity.TokenPurpose;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

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
 *       tokens, sets fresh auth cookies, signs in an ACTIVE user, and returns
 *       {@code { "signedIn": true }}</li>
 *   <li>POST /api/auth/reset — non-ACTIVE (SUSPENDED) user: rejected outright, 400
 *       INVALID_TOKEN, password unchanged, token consumption rolled back — closes an
 *       account-takeover path (a SUSPENDED account's password must never change via a reset
 *       that also doesn't sign anyone in)</li>
 *   <li>POST /api/auth/reset — PENDING_ACTIVATION user: rejected the same way — a password
 *       reset for an account that has never had a real password is meaningless, and this is
 *       the other half of the takeover path the V13 migration closes</li>
 *   <li>POST /api/auth/reset — a STAFF_INVITE-purpose token is rejected with the same
 *       INVALID_TOKEN as a malformed token — purpose is checked at the token-service root,
 *       not as a special case here</li>
 *   <li>POST /api/auth/reset — a successful reset invalidates the user's other outstanding
 *       PASSWORD_RESET tokens, not just the one used (#115)</li>
 *   <li>POST /api/auth/reset — re-using a consumed token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — expired token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — garbage token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/auth/reset — password too short → 400</li>
 *   <li>POST /api/auth/reset — rate limit: 6th attempt from the same IP → 429 (previously
 *       this endpoint had no throttle at all)</li>
 *   <li>POST /api/auth/forgot — known and unknown email both pad to the same fixed
 *       response-time budget, closing the enumeration-timing oracle regardless of whether
 *       the outbound SendGrid send is configured (#115, #120)</li>
 * </ul>
 */
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final String FORGOT_URL  = "/api/auth/forgot";
    private static final String RESET_URL   = "/api/auth/reset";
    private static final String REFRESH_URL = "/api/auth/refresh";

    // Must match src/test/resources/application.yml app.jwt.signing-key — used to hand-craft
    // an already-expired, correctly-signed fixture token (same idea as StripeWebhookIntegrationTest
    // hand-signing fixture payloads with the test webhook secret).
    private static final String TEST_SIGNING_KEY = "test-only-not-a-real-signing-key-placeholder-xx";

    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordResetTokenService tokenService;
    @Autowired ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    @Autowired com.homekept.identity.ResetPasswordRateLimiter resetPasswordRateLimiter;
    @Autowired RecordingEmailSender email;

    @BeforeEach
    void setUp() {
        email.reset();
        forgotPasswordRateLimiter.reset("127.0.0.1");
        resetPasswordRateLimiter.reset("127.0.0.1");
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
        String hash = Hashing.sha256Hex(rawToken);
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
                .andExpect(jsonPath("$.signedIn").value(true))
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
    void reset_suspendedUser_isRejected_passwordUnchanged_tokenNotConsumed() throws Exception {
        // Account-takeover fix: a reset must never change a SUSPENDED account's password —
        // previously this succeeded (200, password changed) with signedIn:false, which was
        // half of a real takeover path (the other half: a resent staff invite reaching a
        // wrong account and being redeemable here instead of at /api/staff/invite/accept).
        User user = createTestUserWithStatus("reset-suspended@test.local", "OldPassword1", UserStatus.SUSPENDED);
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        // Password is unchanged.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();

        // The token's consumption is rolled back with the rest of the failed transaction —
        // a rejected attempt doesn't burn the token (mirrors the staff-invite accept guard).
        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void reset_pendingActivationUser_isRejected_passwordUnchanged_tokenNotConsumed() throws Exception {
        // The other non-ACTIVE case: a PENDING_ACTIVATION account (e.g. an invited-but-not-
        // yet-accepted technician) has never had a real password — a PASSWORD_RESET token
        // for one (possible since /api/auth/forgot doesn't check status) must be rejected too.
        User user = createTestUserWithStatus(
                "reset-pending@test.local", "OldPassword1", UserStatus.PENDING_ACTIVATION);
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();

        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void reset_staffInviteToken_isRejected_passwordUnchanged_tokenNotConsumed() throws Exception {
        // The account-takeover path itself: a STAFF_INVITE token (long-lived, 7 days, minted
        // for an account with no password yet) must never be redeemable here instead of at
        // /api/staff/invite/accept, regardless of the resolved account's status.
        User tech = userRepository.save(new User(
                "staff-invite-at-reset@test.local",
                passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                "Tech", "User", Role.TECHNICIAN, UserStatus.PENDING_ACTIVATION));
        PasswordResetTokenService.MintResult mint = tokenService.mintStaffInvite(tech.getId());

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);

        // Never even reaches the consumed/expired checks for the wrong purpose — the token
        // is untouched either way.
        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void reset_rateLimitExceeded_returns429() throws Exception {
        // Previously the only public auth-mutating endpoint with no throttle at all.
        for (int i = 0; i < com.homekept.identity.ResetPasswordRateLimiter.MAX_ATTEMPTS; i++) {
            resetPasswordRateLimiter.tryConsume("127.0.0.1");
        }
        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage.token\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void reset_activeUser_autoSignsIn_setsCookies() throws Exception {
        // Contrast case for reset_suspendedUser_isRejected/reset_pendingActivationUser_isRejected
        // — an ACTIVE user's behavior must be unchanged: fresh cookies ARE set.
        User user = createTestUser("reset-active@test.local", "OldPassword1");
        PasswordResetTokenService.MintResult mint = tokenService.mint(user);

        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"NewPassword2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(true))
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
                user, Hashing.sha256Hex(rawToken), Instant.now().minusSeconds(60), TokenPurpose.PASSWORD_RESET);
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
        return userRepository.save(
                new User(email, passwordEncoder.encode(rawPassword), "Test", "User",
                        Role.CUSTOMER, status));
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

}
