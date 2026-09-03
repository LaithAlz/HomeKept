package com.homekept.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link LoginIpRateLimiter}'s configurable-cap constructor (backed by
 * {@code app.security.login-ip-max-attempts}). The shared sliding-window logic (cap
 * reached, window expiry, blank-key fail-open, MAX_KEYS eviction) is covered by
 * {@code SlidingWindowRateLimiterTest}. The end-to-end wiring (the login controller
 * actually enforcing the cap → 429) is covered by {@code LoginIpThrottleWiringTest}.
 */
class LoginIpRateLimiterTest {

    @Test
    void configurableCap_isEnforced() {
        LoginIpRateLimiter limiter = new LoginIpRateLimiter(3);

        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 1
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 2
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 3 == cap, still allowed
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse(); // 4 -> blocked
    }
}
