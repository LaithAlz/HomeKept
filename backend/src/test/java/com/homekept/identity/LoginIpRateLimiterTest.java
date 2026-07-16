package com.homekept.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast unit tests for {@link LoginIpRateLimiter}'s throttle logic — no Spring, no DB.
 * The end-to-end wiring (the login controller actually enforcing the cap → 429) is
 * covered by {@code LoginIpThrottleWiringTest}.
 */
class LoginIpRateLimiterTest {

    @Test
    void allowsUpToTheCap_thenBlocks() {
        LoginIpRateLimiter limiter = new LoginIpRateLimiter(3);
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 1
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 2
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();  // 3 == cap, still allowed
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse(); // 4 -> blocked
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse(); // stays blocked
    }

    @Test
    void differentIps_haveIndependentBuckets() {
        LoginIpRateLimiter limiter = new LoginIpRateLimiter(1);
        assertThat(limiter.tryConsume("1.1.1.1")).isTrue();
        assertThat(limiter.tryConsume("1.1.1.1")).isFalse(); // 1.1.1.1 exhausted
        assertThat(limiter.tryConsume("2.2.2.2")).isTrue();  // a different IP is unaffected
    }

    @Test
    void nullOrBlankIp_failsOpen() {
        LoginIpRateLimiter limiter = new LoginIpRateLimiter(1);
        assertThat(limiter.tryConsume(null)).isTrue();
        assertThat(limiter.tryConsume("")).isTrue();
        assertThat(limiter.tryConsume("   ")).isTrue();
    }

    @Test
    void reset_clearsTheBucket() {
        LoginIpRateLimiter limiter = new LoginIpRateLimiter(1);
        assertThat(limiter.tryConsume("9.9.9.9")).isTrue();
        assertThat(limiter.tryConsume("9.9.9.9")).isFalse();
        limiter.reset("9.9.9.9");
        assertThat(limiter.tryConsume("9.9.9.9")).isTrue(); // fresh window after reset
    }
}
