package com.homekept;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wiring test proving {@code POST /api/auth/login} actually enforces the per-IP throttle
 * (LoginIpRateLimiter) and returns 429 once the cap is exceeded. Runs in its own context
 * with the cap overridden to 2 via {@link TestPropertySource}, so it neither depends on
 * nor pollutes the main integration context (where the cap is effectively unlimited).
 *
 * <p>Uses failed logins for an unknown email: the per-IP check runs BEFORE the credential
 * compare, so the first two attempts 401 (throttle allows them) and the third 429s.
 */
@TestPropertySource(properties = "app.security.login-ip-max-attempts=2")
class LoginIpThrottleWiringTest extends AbstractIntegrationTest {

    @Test
    void loginEndpoint_enforcesPerIpThrottle_429AfterCap() throws Exception {
        // Same unknown email each time so the per-email limiter (5/15min) never trips — this
        // isolates the per-IP throttle (cap=2 here). The per-IP check increments and runs
        // before the credential check, so attempts 1-2 reach the 401, attempt 3 is 429'd.
        String body = "{\"email\":\"spray-target@example.com\",\"password\":\"nope\"}";

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
