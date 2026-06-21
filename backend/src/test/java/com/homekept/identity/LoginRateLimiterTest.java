package com.homekept.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoginRateLimiter}. No Spring context.
 */
class LoginRateLimiterTest {

    @Test
    void exactlyMaxAttempts_areAllowed() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryConsume("user@example.com"))
                    .as("attempt %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void exceedingMaxAttempts_isDenied() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryConsume("user@example.com");
        }
        assertThat(limiter.tryConsume("user@example.com")).isFalse();
    }

    @Test
    void differentEmails_haveIndependentCounters() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        // Exhaust one email
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryConsume("blocked@example.com");
        }
        assertThat(limiter.tryConsume("blocked@example.com")).isFalse();

        // Different email should still be allowed
        assertThat(limiter.tryConsume("other@example.com")).isTrue();
    }

    @Test
    void reset_clearsCounterAndAllowsAgain() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryConsume("user@example.com");
        }
        assertThat(limiter.tryConsume("user@example.com")).isFalse();

        limiter.reset("user@example.com");
        assertThat(limiter.tryConsume("user@example.com")).isTrue();
    }

    @Test
    void emailComparison_isCaseInsensitive() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        // Consume using uppercase
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryConsume("User@EXAMPLE.COM");
        }
        // Check using lowercase — must be the same bucket
        assertThat(limiter.tryConsume("user@example.com")).isFalse();
    }

    @Test
    void emailComparison_stripsWhitespace_preventsBypassAttack() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        // Exhaust the bucket using the canonical form.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            limiter.tryConsume("user@example.com");
        }
        assertThat(limiter.tryConsume("user@example.com")).isFalse();

        // Attempts with surrounding whitespace must hit THE SAME bucket, not get a fresh one.
        assertThat(limiter.tryConsume("  user@example.com  ")).isFalse();
        assertThat(limiter.tryConsume(" User@EXAMPLE.COM\t")).isFalse();
    }

    @Test
    void normalizeKey_stripsAndLowercases() {
        assertThat(LoginRateLimiter.normalizeKey(" Admin@X.COM "))
                .isEqualTo("admin@x.com");
        assertThat(LoginRateLimiter.normalizeKey("User@Example.Com"))
                .isEqualTo("user@example.com");
    }
}
