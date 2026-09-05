package com.homekept;

import com.homekept.identity.ChangePasswordRateLimiter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/auth/change-password} (api-contract.md §Auth).
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy path: sets the new password, revokes the caller's existing refresh tokens,
 *       and issues fresh cookies that keep the caller signed in.</li>
 *   <li>The old password no longer works for login after a successful change.</li>
 *   <li>Wrong current password → 400.</li>
 *   <li>New password too short → 400.</li>
 *   <li>New password same as current → 400.</li>
 *   <li>Any authenticated role (not just CUSTOMER) may call this.</li>
 *   <li>Anonymous → 401.</li>
 *   <li>Rate limit: 11th attempt from the same IP within the window → 429.</li>
 * </ul>
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
class ChangePasswordIntegrationTest extends AbstractIntegrationTest {

    private static final String CHANGE_PASSWORD_URL = "/api/auth/change-password";
    private static final String REFRESH_URL = "/api/auth/refresh";

    @Autowired ChangePasswordRateLimiter changePasswordRateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        changePasswordRateLimiter.reset("127.0.0.1");
    }

    @Test
    void changePassword_happyPath_setsNewPassword_revokesOldRefreshTokens_andKeepsCallerSignedIn() throws Exception {
        User user = createTestUser("change-happy@test.local", "OldPassword1");

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"change-happy@test.local\",\"password\":\"OldPassword1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = extractCookieValue(
                loginResult.getResponse().getHeaders("Set-Cookie"), "hk_access");
        String oldRefreshToken = extractCookieValue(
                loginResult.getResponse().getHeaders("Set-Cookie"), "hk_refresh");

        MvcResult changeResult = mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"))
                .andReturn();

        // New password takes effect; old password no longer matches.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword2", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isFalse();

        // Old password can no longer log in.
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"change-happy@test.local\",\"password\":\"OldPassword1\"}"))
                .andExpect(status().isUnauthorized());

        // The refresh token issued before the change must now be revoked.
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));

        // The caller's freshly-issued cookies from the change-password response still work.
        String newRefreshToken = extractCookieValue(
                changeResult.getResponse().getHeaders("Set-Cookie"), "hk_refresh");
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", newRefreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400_andDoesNotChangePassword() throws Exception {
        User user = createTestUser("change-wrongcurrent@test.local", "OldPassword1");
        String accessToken = loginAndGetAccessToken("change-wrongcurrent@test.local", "OldPassword1");

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword9\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_newPasswordTooShort_returns400_andDoesNotChangePassword() throws Exception {
        User user = createTestUser("change-tooshort@test.local", "OldPassword1");
        String accessToken = loginAndGetAccessToken("change-tooshort@test.local", "OldPassword1");

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_newPasswordSameAsCurrent_returns400() throws Exception {
        createTestUser("change-samepassword@test.local", "OldPassword1");
        String accessToken = loginAndGetAccessToken("change-samepassword@test.local", "OldPassword1");

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"OldPassword1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_adminRole_succeeds() throws Exception {
        // "Any authenticated role" — not CUSTOMER-only.
        User admin = userRepository.save(new User(
                "change-admin@test.local", passwordEncoder.encode("OldPassword1"),
                "Admin", "Test", Role.ADMIN, UserStatus.ACTIVE));
        String accessToken = loginAndGetAccessToken("change-admin@test.local", "OldPassword1");

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword2", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_anonymous_returns401() throws Exception {
        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_rateLimitExceeded_returns429() throws Exception {
        String accessToken = loginAndGetAccessToken(
                createTestUser("change-ratelimit@test.local", "OldPassword1").getEmail(), "OldPassword1");

        for (int i = 0; i < ChangePasswordRateLimiter.MAX_ATTEMPTS; i++) {
            changePasswordRateLimiter.tryConsume("127.0.0.1");
        }

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .cookie(new Cookie("hk_access", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createTestUser(String email, String rawPassword) {
        return userRepository.save(new User(
                email, passwordEncoder.encode(rawPassword), "Test", "User",
                Role.CUSTOMER, UserStatus.ACTIVE));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(loginResult.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }
}
