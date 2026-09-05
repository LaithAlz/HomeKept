package com.homekept.technician;

import com.homekept.AbstractIntegrationTest;
import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.common.Hashing;
import com.homekept.identity.ForgotPasswordRateLimiter;
import com.homekept.identity.PasswordResetToken;
import com.homekept.identity.PasswordResetTokenRepository;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminTechnicianController}
 * ({@code GET /api/admin/technicians}, {@code POST /api/admin/technicians},
 * {@code POST /api/admin/technicians/{id}/invite}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET list as ADMIN → 200 array; includes an invited technician with identity fields
 *       resolved and {@code invitedAt} populated.</li>
 *   <li>GET list as CUSTOMER → 403; anonymous → 401.</li>
 *   <li>POST (invite) as ADMIN → 201; user created PENDING_ACTIVATION with a
 *       technician_profile; sends exactly one invite email.</li>
 *   <li>POST duplicate email → 409, no second user/profile/email created.</li>
 *   <li>POST validation (blank fields) → 400.</li>
 *   <li>POST as CUSTOMER → 403; anonymous → 401.</li>
 *   <li>Resend invalidates the previous token (old link stops working) and sends a new
 *       email.</li>
 *   <li>Resend for an unknown profile id → 404.</li>
 *   <li>Resend as CUSTOMER → 403; anonymous → 401.</li>
 *   <li>Resend against an already-ACTIVE (or SUSPENDED) technician → 409, no new token
 *       minted, no new email sent — the account-takeover fix (resolve-before-touch).</li>
 *   <li>Resend does not consume a PASSWORD_RESET-purpose token the same person separately
 *       holds — invalidateAllForUser is scoped to STAFF_INVITE only.</li>
 *   <li>The roster's {@code invitedAt} is unaffected by that same person requesting a
 *       password reset — latestInviteAtByUserIds is scoped to STAFF_INVITE only.</li>
 * </ul>
 */
class AdminTechnicianIntegrationTest extends AbstractIntegrationTest {

    private static final String TECHNICIANS_URL = "/api/admin/technicians";

    @Autowired TechnicianProfileRepository techProfileRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired RecordingEmailSender email;
    @Autowired StaffInviteRateLimiter staffInviteRateLimiter;
    @Autowired ForgotPasswordRateLimiter forgotPasswordRateLimiter;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();
        email.reset();
        // Shared singletons across the whole (cached) Spring test context — reset so this
        // class's own /api/staff/invite/validate and /api/auth/forgot calls never trip a
        // cap left over from another test class's run in the same JVM.
        staffInviteRateLimiter.reset("127.0.0.1");
        forgotPasswordRateLimiter.reset("127.0.0.1");

        User adminUser = userRepository.save(new User(
                "admin-tech-admin-" + nano + "@test.local",
                passwordEncoder.encode("Admin1234!"),
                "Admin", "Tech",
                Role.ADMIN, UserStatus.ACTIVE));
        adminToken = loginAs(adminUser.getEmail(), "Admin1234!");

        User customerUser = userRepository.save(new User(
                "admin-tech-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "Tech",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Cust1234!");
    }

    // ── GET /api/admin/technicians — list ─────────────────────────────────────

    @Test
    void listTechnicians_asAdmin_returns200WithArray() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listTechnicians_includesInvitedTechnicianWithIdentityFieldsAndInvitedAt() throws Exception {
        String inviteEmail = "invited-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Priya", "Sharma", inviteEmail);
        Long profileId = idFrom(inviteResult);

        MvcResult result = mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        java.util.List<String> firstNames = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].firstName");
        java.util.List<String> emails = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].email");
        java.util.List<String> roles = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].role");
        java.util.List<String> userStatuses = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].userStatus");
        java.util.List<String> invitedAts = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].invitedAt");

        assertThat(firstNames).containsExactly("Priya");
        assertThat(emails).containsExactly(inviteEmail);
        assertThat(roles).containsExactly("TECHNICIAN");
        assertThat(userStatuses).containsExactly("PENDING_ACTIVATION");
        assertThat(invitedAts).hasSize(1);
        assertThat(invitedAts.get(0)).isNotNull();
    }

    @Test
    void listTechnicians_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTechnicians_anonymous_returns401() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/technicians — happy path ──────────────────────────────

    @Test
    void createTechnician_asAdmin_returns201_createsPendingUserAndProfile_sendsOneEmail() throws Exception {
        String inviteEmail = "create-happy-" + System.nanoTime() + "@test.local";

        MvcResult result = mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("Jordan", "Lee", inviteEmail, "(905) 555-0100")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.firstName").value("Jordan"))
                .andExpect(jsonPath("$.lastName").value("Lee"))
                .andExpect(jsonPath("$.email").value(inviteEmail))
                .andExpect(jsonPath("$.userStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.invitedAt").exists())
                .andReturn();

        Long profileId = idFrom(result);
        Long userId = idFrom(result, "$.userId");

        // The technician_profile row exists, linked to the new user, with no cost/status/hire
        // date captured at invite time (per the issue: those belong on a later edit screen).
        TechnicianProfile persisted = techProfileRepository.findById(profileId).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getFullyLoadedHourlyCostCents()).isNull();
        assertThat(persisted.getEmployeeStatus()).isNull();
        assertThat(persisted.getHireDate()).isNull();

        // The user is TECHNICIAN, PENDING_ACTIVATION, and cannot log in yet (unusable password).
        User created = userRepository.findById(userId).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(created.getPhone()).isEqualTo("(905) 555-0100");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteEmail + "\",\"password\":\"anything12345\"}"))
                .andExpect(status().isUnauthorized());

        // Exactly one invite email was sent, containing no password/role. (A raw-id
        // substring check isn't meaningful here: small ids like "1" trivially collide with
        // unrelated boilerplate digits in the template, e.g. "initial-scale=1" — the "no
        // ids in the email" guarantee instead comes from EmailTemplates.staffInvite's
        // signature never accepting one, verified by inspection.)
        assertThat(email.sent).hasSize(1);
        RecordingEmailSender.Sent sent = email.sent.get(0);
        assertThat(sent.toEmail()).isEqualTo(inviteEmail);
        assertThat(sent.subject()).isEqualTo("Set up your HomeKept staff account");
        assertThat(sent.htmlBody()).contains("/staff/activate?token=");
        assertThat(sent.htmlBody()).doesNotContain("TECHNICIAN");
    }

    // ── POST /api/admin/technicians — validation ──────────────────────────────

    @Test
    void createTechnician_blankFirstName_returns400() throws Exception {
        String body = """
                { "firstName": "", "lastName": "Lee", "email": "blank-first@test.local" }
                """;
        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createTechnician_invalidEmail_returns400() throws Exception {
        String body = """
                { "firstName": "Jordan", "lastName": "Lee", "email": "not-an-email" }
                """;
        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ── POST /api/admin/technicians — duplicate email → 409 ───────────────────

    @Test
    void createTechnician_duplicateEmail_returns409_andCurratedMessage() throws Exception {
        String inviteEmail = "duplicate-" + System.nanoTime() + "@test.local";

        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("First", "Attempt", inviteEmail, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("Second", "Attempt", inviteEmail, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message")
                        .value("An account already exists for that email address."));

        // No second user or profile was created for the conflicting invite.
        assertThat(userRepository.findByEmailIgnoreCase(inviteEmail)).isPresent();
        assertThat(email.sent).hasSize(1);
    }

    @Test
    void createTechnician_duplicateEmail_isCaseInsensitive_returns409() throws Exception {
        String inviteEmail = "case-" + System.nanoTime() + "@test.local";

        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("First", "Attempt", inviteEmail, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("Second", "Attempt", inviteEmail.toUpperCase(), null)))
                .andExpect(status().isConflict());
    }

    // ── POST /api/admin/technicians — role gating ─────────────────────────────

    @Test
    void createTechnician_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("Jordan", "Lee", "role-gate@test.local", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTechnician_anonymous_returns401() throws Exception {
        mockMvc.perform(post(TECHNICIANS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("Jordan", "Lee", "anon-gate@test.local", null)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/technicians/{id}/invite — resend ──────────────────────

    @Test
    void resendInvite_invalidatesThePreviousToken_soTheOldLinkNoLongerWorks() throws Exception {
        String inviteEmail = "resend-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Sam", "Rivera", inviteEmail);
        Long profileId = idFrom(inviteResult);

        String firstToken = extractTokenFromLink(email.sent.get(0).htmlBody());

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isAccepted());

        // A second (different) invite email was sent.
        assertThat(email.sent).hasSize(2);
        String secondToken = extractTokenFromLink(email.sent.get(1).htmlBody());
        assertThat(secondToken).isNotEqualTo(firstToken);

        // The old link no longer validates.
        mockMvc.perform(post("/api/staff/invite/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + firstToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("USED"));

        // The new link does validate.
        mockMvc.perform(post("/api/staff/invite/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + secondToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.firstName").value("Sam"));
    }

    @Test
    void resendInvite_unknownProfileId_returns404() throws Exception {
        mockMvc.perform(post(TECHNICIANS_URL + "/999999/invite")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendInvite_asCustomer_returns403() throws Exception {
        MvcResult inviteResult = invite("Alex", "Kim", "resend-role-" + System.nanoTime() + "@test.local");
        Long profileId = idFrom(inviteResult);

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite")
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendInvite_anonymous_returns401() throws Exception {
        MvcResult inviteResult = invite("Robin", "Diaz", "resend-anon-" + System.nanoTime() + "@test.local");
        Long profileId = idFrom(inviteResult);

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/technicians/{id}/invite — account-takeover fix ────────

    @Test
    void resendInvite_activeTechnician_returns409_noNewTokenMinted() throws Exception {
        // The account-takeover path this closes: an admin clicking "Resend" on a roster row
        // that (per the issue) can render off a stale cached userStatus must not mail a
        // fresh, redeemable password-setting link to an account that has already accepted.
        String inviteEmail = "resend-active-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Casey", "Nguyen", inviteEmail);
        Long profileId = idFrom(inviteResult);

        String rawToken = extractTokenFromLink(email.sent.get(0).htmlBody());
        mockMvc.perform(post("/api/staff/invite/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isConflict());

        // No second (resend) email was sent — only the original invite.
        assertThat(email.sent).hasSize(1);
    }

    @Test
    void resendInvite_suspendedTechnician_returns409_noNewTokenMinted() throws Exception {
        // Same guard, different ineligible state: resending must not be a way to reactivate
        // a suspended technician's ability to sign in either.
        String inviteEmail = "resend-suspended-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Drew", "Okafor", inviteEmail);
        Long profileId = idFrom(inviteResult);

        User tech = userRepository.findByEmailIgnoreCase(inviteEmail).orElseThrow();
        tech.setStatus(UserStatus.SUSPENDED);
        userRepository.save(tech);

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isConflict());

        assertThat(email.sent).hasSize(1);
    }

    @Test
    void resendInvite_doesNotConsumeACustomerPurposeToken() throws Exception {
        // Point 5: invalidateAllForUser must be scoped to STAFF_INVITE only. Without that
        // scoping, resending an invite would also silently burn a password-reset token the
        // same person separately (and legitimately) requested.
        String inviteEmail = "resend-scoped-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Jamie", "Alvarez", inviteEmail);
        Long profileId = idFrom(inviteResult);

        mockMvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteEmail + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(email.sent).hasSize(2);
        String resetRawToken = extractResetTokenFromLink(email.sent.get(1).htmlBody());

        mockMvc.perform(post(TECHNICIANS_URL + "/" + profileId + "/invite")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isAccepted());

        // The customer-side (PASSWORD_RESET) token is still unconsumed after the resend,
        // which only invalidates STAFF_INVITE tokens for this user.
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(Hashing.sha256Hex(resetRawToken))
                .orElseThrow();
        assertThat(resetToken.isConsumed()).isFalse();
    }

    @Test
    void theRostersInvitedAt_isUnaffectedByAPasswordReset() throws Exception {
        // Point 6: latestInviteAtByUserIds must be scoped to STAFF_INVITE only — a
        // technician's own password-reset request must never show up on the roster as an
        // invite that was never actually (re)sent.
        String inviteEmail = "invited-at-scoped-" + System.nanoTime() + "@test.local";
        MvcResult inviteResult = invite("Morgan", "Singh", inviteEmail);
        Long profileId = idFrom(inviteResult);
        String originalInvitedAt = com.jayway.jsonpath.JsonPath.read(
                inviteResult.getResponse().getContentAsString(), "$.invitedAt");

        mockMvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteEmail + "\"}"))
                .andExpect(status().isAccepted());

        MvcResult rosterResult = mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String body = rosterResult.getResponse().getContentAsString();
        java.util.List<String> invitedAts = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].invitedAt");

        assertThat(invitedAts).containsExactly(originalInvitedAt);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String inviteBody(String firstName, String lastName, String email, String phone) {
        String phoneField = phone != null ? ", \"phone\": \"" + phone + "\"" : "";
        return "{ \"firstName\": \"" + firstName + "\", \"lastName\": \"" + lastName
                + "\", \"email\": \"" + email + "\"" + phoneField + " }";
    }

    /** Invites a technician via the admin POST endpoint and returns the raw MvcResult. */
    private MvcResult invite(String firstName, String lastName, String email) throws Exception {
        return mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(firstName, lastName, email, null)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Extracts the raw token query param from a {@code /staff/activate?token=...} link. */
    private String extractTokenFromLink(String htmlBody) {
        return extractTokenFromLink(htmlBody, "/staff/activate?token=");
    }

    /** Extracts the raw token query param from a {@code /reset-password?token=...} link. */
    private String extractResetTokenFromLink(String htmlBody) {
        return extractTokenFromLink(htmlBody, "/reset-password?token=");
    }

    private String extractTokenFromLink(String htmlBody, String marker) {
        int idx = htmlBody.indexOf(marker);
        assertThat(idx).isGreaterThan(-1);
        int start = idx + marker.length();
        int end = start;
        while (end < htmlBody.length() && htmlBody.charAt(end) != '"' && htmlBody.charAt(end) != '&') {
            end++;
        }
        return htmlBody.substring(start, end);
    }
}
