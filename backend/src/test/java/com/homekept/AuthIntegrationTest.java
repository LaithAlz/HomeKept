package com.homekept;

import com.homekept.identity.AdminSeeder;
import com.homekept.identity.AuthService;
import com.homekept.identity.JwtService;
import com.homekept.identity.LoginRateLimiter;
import com.homekept.identity.RefreshTokenRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the identity/auth vertical slice.
 * Runs against a real Postgres instance via Testcontainers (@ServiceConnection).
 *
 * <p>No class-level @Transactional — MockMvc HTTP calls execute in their own
 * transactions. Test data is cleaned up in @AfterEach to avoid cross-test pollution.
 *
 * <p>Covers:
 * <ul>
 *   <li>Login happy path sets both cookies</li>
 *   <li>Login with wrong password → 401, same message as unknown email</li>
 *   <li>Login with unknown email → 401, same message (no enumeration)</li>
 *   <li>Login with SUSPENDED user → 401, same generic message (no status enumeration)</li>
 *   <li>Login with PENDING_ACTIVATION user → 401</li>
 *   <li>Rate limit: 6th attempt → 429</li>
 *   <li>Refresh rotates: old refresh rejected after use</li>
 *   <li>Logout revokes all → subsequent refresh fails</li>
 *   <li>Logout without a live access token (expired) works via refresh cookie</li>
 *   <li>Error envelopes include request_id field</li>
 *   <li>GET /api/auth/me without cookie → 401</li>
 *   <li>GET /api/auth/me with expired/garbage token → 401</li>
 *   <li>GET /api/auth/me returns correct shape after login</li>
 *   <li>Seed admin (env-driven via AdminSeeder) can log in</li>
 *   <li>GET /api/health → 200, no auth required</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired LoginRateLimiter rateLimiter;
    @Autowired AdminSeeder adminSeeder;

    private static final String LOGIN_URL   = "/api/auth/login";
    private static final String REFRESH_URL = "/api/auth/refresh";
    private static final String LOGOUT_URL  = "/api/auth/logout";
    private static final String ME_URL      = "/api/auth/me";
    private static final String HEALTH_URL  = "/api/health";

    /** Track user IDs created per test for cleanup. */
    private final List<Long> createdUserIds = new ArrayList<>();

    // Credentials that match src/test/resources/application.yml admin-seed config.
    private static final String SEED_ADMIN_EMAIL    = "seed-admin@test.local";
    private static final String SEED_ADMIN_PASSWORD = "test-only-placeholder-admin-pw";

    @BeforeEach
    void setUp() {
        rateLimiter.reset("test@example.com");
        rateLimiter.reset("nobody@example.com");
        rateLimiter.reset(SEED_ADMIN_EMAIL);
        rateLimiter.reset("ratelimit@example.com");
        rateLimiter.reset("suspended@example.com");
        rateLimiter.reset("pending@example.com");
        createdUserIds.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up in FK order: refresh tokens are ON DELETE CASCADE so deleting the user cleans them up
        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ── /api/health ────────────────────────────────────────────────────────────

    @Test
    void healthEndpoint_isPublicAndReturnsUp() throws Exception {
        mockMvc.perform(get(HEALTH_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Test
    void login_happyPath_setsBothCookies() throws Exception {
        createTestUser("test@example.com", "Secret123", Role.CUSTOMER);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertThat(cookies).anyMatch(c -> c.startsWith("hk_access="));
                    assertThat(cookies).anyMatch(c -> c.startsWith("hk_refresh="));
                    assertThat(cookies).allMatch(c -> c.contains("HttpOnly"));
                    assertThat(cookies).allMatch(c -> c.contains("SameSite=Lax"));
                });
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        createTestUser("test@example.com", "CorrectPassword", Role.CUSTOMER);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"WrongPassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownEmail_returns401_sameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                // CRITICAL: same message — no enumeration
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"anything\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.request_id").isNotEmpty());
    }

    @Test
    void login_malformedJsonBody_returns400_notServerError() throws Exception {
        // Truncated JSON. Previously fell through to the catch-all → 500; now a client 400.
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.request_id").isNotEmpty());
    }

    @Test
    void login_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void login_suspendedUser_returns401_sameMessageAsWrongPassword() throws Exception {
        // A SUSPENDED user with a valid password must receive the same generic 401 —
        // no status enumeration, no hint that the account exists.
        createTestUserWithStatus("suspended@example.com", "ValidPass1", Role.CUSTOMER, UserStatus.SUSPENDED);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"suspended@example.com\",\"password\":\"ValidPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.error.request_id").isNotEmpty());
    }

    @Test
    void login_pendingActivationUser_returns401() throws Exception {
        createTestUserWithStatus("pending@example.com", "ValidPass1", Role.CUSTOMER, UserStatus.PENDING_ACTIVATION);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"ValidPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void errorEnvelope_containsRequestId() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.request_id").isNotEmpty());
    }

    @Test
    void errorEnvelope_echoesXRequestIdHeader() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "test-req-123")
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.request_id").value("test-req-123"));
    }

    @Test
    void login_rateLimitExceeded_returns429() throws Exception {
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            rateLimiter.tryConsume("ratelimit@example.com");
        }
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ratelimit@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    // ── Seed admin ─────────────────────────────────────────────────────────────

    /**
     * Verifies that AdminSeeder ran on startup and that the seeded admin can log in.
     * Credentials come from src/test/resources/application.yml (app.admin-seed.*),
     * which are bound from ADMIN_SEED_EMAIL / ADMIN_SEED_PASSWORD in production.
     * No plaintext credential is committed anywhere in the codebase.
     */
    @Test
    void seedAdmin_canLogIn() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + SEED_ADMIN_EMAIL + "\",\"password\":\"" + SEED_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertThat(cookies).anyMatch(c -> c.startsWith("hk_access="));
                    assertThat(cookies).anyMatch(c -> c.startsWith("hk_refresh="));
                });
    }

    /**
     * Verifies that AdminSeeder is idempotent: a second call to {@code run()} with the
     * same email must leave the existing user untouched (no duplicate, no exception).
     */
    @Test
    void seedAdmin_isIdempotent() throws Exception {
        // Verify the seeded admin exists from startup.
        assertThat(userRepository.findByEmailIgnoreCase(SEED_ADMIN_EMAIL)).isPresent();

        // Run the seeder a second time — must be a no-op.
        adminSeeder.run(null);

        // Still exactly one user with that email.
        long count = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(SEED_ADMIN_EMAIL))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    @Test
    void refresh_rotatesCookies_andOldRefreshIsRejected() throws Exception {
        createTestUser("test@example.com", "Secret123", Role.CUSTOMER);

        // Login via HTTP to get a real refresh token in the DB
        var loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractCookieValue(loginResult.getResponse().getHeaders("Set-Cookie"), "hk_refresh");
        assertThat(refreshToken).isNotBlank();

        // Rotate: use the refresh token to get new cookies
        var refreshResult = mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        var newCookies = refreshResult.getResponse().getHeaders("Set-Cookie");
        assertThat(newCookies).anyMatch(c -> c.startsWith("hk_access="));
        assertThat(newCookies).anyMatch(c -> c.startsWith("hk_refresh="));

        // The OLD refresh token must now be rejected (revoked)
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void refresh_noRefreshCookie_returns401() throws Exception {
        mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_garbageToken_returns401() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", "garbage-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Test
    void logout_revokesAll_subsequentRefreshFails() throws Exception {
        createTestUser("test@example.com", "Secret123", Role.CUSTOMER);

        // Login to get both tokens
        var loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var setCookieHeaders = loginResult.getResponse().getHeaders("Set-Cookie");
        String accessToken  = extractCookieValue(setCookieHeaders, "hk_access");
        String refreshToken = extractCookieValue(setCookieHeaders, "hk_refresh");

        // Logout using the access token
        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("hk_access", accessToken)))
                .andExpect(status().isNoContent());

        // Subsequent refresh must fail — all tokens were revoked
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void logout_withoutAccessToken_revokesViaRefreshCookie_andReturns204() throws Exception {
        // Simulates the case where the access token has expired but the user still
        // has a valid refresh cookie. Logout must work and revoke the refresh token.
        createTestUser("test@example.com", "Secret123", Role.CUSTOMER);

        var loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractCookieValue(
                loginResult.getResponse().getHeaders("Set-Cookie"), "hk_refresh");

        // Logout with ONLY the refresh cookie (no access cookie — simulates expiry).
        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("hk_refresh", refreshToken)))
                .andExpect(status().isNoContent());

        // Subsequent refresh with the now-revoked token must fail.
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("hk_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void logout_noTokensAtAll_returns204() throws Exception {
        // Logout with no cookies at all should still return 204 gracefully.
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isNoContent());
    }

    // ── Auth hardening (security-audit fixes) ────────────────────────────────────

    @Test
    void login_refreshCookie_isScopedToApiAuthPath_soLogoutReceivesIt() throws Exception {
        // The refresh cookie must reach /api/auth/logout (to revoke server-side), not only
        // /api/auth/refresh. Assert the Set-Cookie Path is the widened /api/auth.
        createTestUser("test@example.com", "Secret123", Role.CUSTOMER);

        var result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var setCookies = result.getResponse().getHeaders("Set-Cookie");

        // The LIVE refresh cookie (non-zero Max-Age) must be scoped to /api/auth so logout receives it.
        String liveRefresh = setCookies.stream()
                .filter(c -> c.startsWith("hk_refresh="))
                .filter(c -> !c.contains("Max-Age=0"))
                .findFirst().orElseThrow();
        assertThat(liveRefresh).contains("Path=/api/auth;");
        assertThat(liveRefresh).doesNotContain("Path=/api/auth/refresh");

        // Transitional back-compat: a Max-Age=0 clear is also emitted at the legacy
        // /api/auth/refresh path so a pre-deploy old-path cookie can't shadow the new one.
        assertThat(setCookies).anyMatch(c ->
                c.startsWith("hk_refresh=") && c.contains("Path=/api/auth/refresh") && c.contains("Max-Age=0"));
    }

    // ── /api/auth/me ───────────────────────────────────────────────────────────

    @Test
    void me_withoutCookie_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("hk_access", "not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withExpiredOrInvalidToken_returns401() throws Exception {
        // Signature won't match our key so the filter rejects it
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZXhwIjoxfQ.invalidsig";
        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("hk_access", fakeToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returnsCorrectShape() throws Exception {
        User user = createTestUser("test@example.com", "Secret123", Role.ADMIN);

        // Login via HTTP — access token is in the response cookie
        var loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(
                loginResult.getResponse().getHeaders("Set-Cookie"), "hk_access");

        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("hk_access", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User createTestUser(String email, String rawPassword, Role role) {
        return createTestUserWithStatus(email, rawPassword, role, UserStatus.ACTIVE);
    }

    private User createTestUserWithStatus(String email, String rawPassword, Role role, UserStatus status) {
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(rawPassword), "Test", "User", role, status));
        createdUserIds.add(user.getId());
        return user;
    }

    /**
     * Extracts a cookie value from Set-Cookie header strings.
     * Each header looks like: {@code name=value; Path=/; HttpOnly; ...}
     */
    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }
}
