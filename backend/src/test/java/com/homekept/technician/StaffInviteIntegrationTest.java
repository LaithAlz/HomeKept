package com.homekept.technician;

import com.homekept.AbstractIntegrationTest;
import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.common.Hashing;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the staff (technician) invite acceptance flow
 * ({@code POST /api/staff/invite/validate}, {@code POST /api/staff/invite/accept}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>validate — valid pending-technician invite → 200 {valid:true, firstName}</li>
 *   <li>validate — garbage token → {valid:false, reason:"INVALID"}</li>
 *   <li>validate — expired token → {valid:false, reason:"EXPIRED"}</li>
 *   <li>validate — consumed (already-accepted) token → {valid:false, reason:"USED"}</li>
 *   <li>validate — a customer's password-reset token never validates here (purpose-scoped
 *       lookup closes the shared-table leak at the root)</li>
 *   <li>accept — happy path: 201, sets auth cookies, flips ACTIVE, consumes the token, and
 *       the technician can log in with the new password afterwards</li>
 *   <li>accept — on an already-ACTIVE user → rejected, password unchanged</li>
 *   <li>accept — on a SUSPENDED user → rejected, status unchanged, password unchanged</li>
 *   <li>accept — reusing an already-consumed token → rejected</li>
 *   <li>accept — password too short → 400, token not consumed</li>
 * </ul>
 */
class StaffInviteIntegrationTest extends AbstractIntegrationTest {

    private static final String VALIDATE_URL = "/api/staff/invite/validate";
    private static final String ACCEPT_URL = "/api/staff/invite/accept";

    // Must match src/test/resources/application.yml app.jwt.signing-key — used to hand-craft
    // an already-expired, correctly-signed fixture STAFF_INVITE token (mintStaffInvite owns
    // its own 7-day TTL and can't mint a pre-expired one, same idea as
    // PasswordResetIntegrationTest's expired-token fixture).
    private static final String TEST_SIGNING_KEY = "test-only-not-a-real-signing-key-placeholder-xx";

    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordResetTokenService tokenService;
    @Autowired RecordingEmailSender email;
    @Autowired StaffInviteRateLimiter staffInviteRateLimiter;

    @BeforeEach
    void setUp() {
        email.reset();
        // The rate limiter is a singleton bean living across the whole (cached) Spring test
        // context, not per-test — reset it so this class's ~10 tests (several posting to
        // validate/accept more than once) never trip the 10/IP/hour cap on each other, and
        // so an earlier test class hitting the same endpoints doesn't leave it primed.
        staffInviteRateLimiter.reset("127.0.0.1");
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    void validate_pendingTechnicianInvite_returns200WithValidTrueAndFirstName() throws Exception {
        User tech = createPendingTechnician("Priya", "invite-validate@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                // Never leaks the email or role.
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void validate_garbageToken_returns200WithInvalidReason() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"this.is.garbage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID"));
    }

    @Test
    void validate_expiredToken_returns200WithExpiredReason() throws Exception {
        User tech = createPendingTechnician("Expired", "invite-expired@test.local");

        long pastExpEpoch = Instant.now().minusSeconds(60).getEpochSecond();
        String payload = "userId=" + tech.getId() + "&nonce=deadbeefdeadbeef&exp=" + pastExpEpoch;
        String rawToken = buildSignedToken(payload);
        tokenRepository.save(new PasswordResetToken(
                tech, Hashing.sha256Hex(rawToken), Instant.now().minusSeconds(60), TokenPurpose.STAFF_INVITE));

        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
    }

    @Test
    void validate_consumedToken_returns200WithUsedReason() throws Exception {
        User tech = createPendingTechnician("Consumed", "invite-consumed@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("USED"));
    }

    @Test
    void validate_customerPasswordResetToken_neverValidatesHere() throws Exception {
        // A customer's ordinary forgot-password token (PASSWORD_RESET purpose) must not also
        // work as a staff invite (STAFF_INVITE purpose), and must not leak that customer's
        // first name via this endpoint — closed at the root by the purpose-scoped lookup in
        // PasswordResetTokenService.validate.
        User customer = userRepository.save(new User(
                "customer-reset-" + System.nanoTime() + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Priya", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        PasswordResetTokenService.MintResult mint = tokenService.mint(customer);

        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID"));
    }

    // ── accept — happy path ────────────────────────────────────────────────────

    @Test
    void accept_happyPath_returns201_setsCookies_flipsActive_consumesToken_allowsLogin() throws Exception {
        String inviteEmail = "accept-happy-" + System.nanoTime() + "@test.local";
        User tech = createPendingTechnician("Sam", inviteEmail);
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        MvcResult result = mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(tech.getId()))
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"))
                .andReturn();
        assertThat(idFrom(result, "$.userId")).isEqualTo(tech.getId());

        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("newpassword1", reloaded.getPasswordHash())).isTrue();

        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isTrue();

        // The technician can now log in with the chosen password.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteEmail + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"));
    }

    // ── accept — critical guard ────────────────────────────────────────────────

    @Test
    void accept_alreadyActiveUser_isRejected_passwordUnchanged() throws Exception {
        // A still-valid token minted before the technician accepted, but the account has
        // since become ACTIVE some other way (e.g. accepted already via another tab) — must
        // not be replayable to silently change the password again.
        User tech = createPendingTechnician("Already", "invite-already-active@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        tech.setStatus(UserStatus.ACTIVE);
        String originalHash = tech.getPasswordHash();
        userRepository.save(tech);

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalHash);

        // The token consumption is rolled back with the rest of the failed transaction, so
        // the token is not left in a burned state by a rejected attempt.
        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void accept_suspendedUser_isRejected_statusAndPasswordUnchanged() throws Exception {
        // The critical guard: without it, a still-valid invite token would be a way to
        // reactivate a SUSPENDED account.
        User tech = createPendingTechnician("Suspended", "invite-suspended@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        tech.setStatus(UserStatus.SUSPENDED);
        String originalHash = tech.getPasswordHash();
        userRepository.save(tech);

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalHash);

        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void accept_consumedToken_secondAcceptIsRejected() throws Exception {
        User tech = createPendingTechnician("Reuse", "invite-reuse@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"anotherpassword2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        // The original password from the first accept remains in effect.
        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpassword1", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("anotherpassword2", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    void accept_passwordTooShort_returns400_tokenNotConsumed() throws Exception {
        User tech = createPendingTechnician("Short", "invite-short@test.local");
        PasswordResetTokenService.MintResult mint = mintInvite(tech);

        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(tech.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);

        PasswordResetToken token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void accept_garbageToken_returns400InvalidToken() throws Exception {
        mockMvc.perform(post(ACCEPT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage.token\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createPendingTechnician(String firstName, String email) {
        return userRepository.save(new User(
                email,
                passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                firstName, "Technician",
                Role.TECHNICIAN, UserStatus.PENDING_ACTIVATION));
    }

    private PasswordResetTokenService.MintResult mintInvite(User user) {
        return tokenService.mintStaffInvite(user.getId());
    }

    /**
     * Hand-signs a fixture token matching PasswordResetTokenService's format, so a
     * PasswordResetToken row can be constructed directly for the expired-token test case
     * (mintStaffInvite owns its own 7-day TTL and cannot mint a pre-expired token).
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
