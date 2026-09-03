package com.homekept.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the shared {@link SlidingWindowRateLimiter} logic used by every
 * concrete rate limiter in the app. No Spring context.
 */
class SlidingWindowRateLimiterTest {

    @Test
    void exactlyMaxAttempts_areAllowed_thenExceedingIsDenied() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 60L, 100);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();
    }

    @Test
    void differentKeys_haveIndependentBuckets() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 60L, 100);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();
        assertThat(limiter.tryConsume("b")).isTrue();
    }

    @Test
    void nullOrBlankKey_failsOpen() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 60L, 100);

        assertThat(limiter.tryConsume(null)).isTrue();
        assertThat(limiter.tryConsume("")).isTrue();
        assertThat(limiter.tryConsume("   ")).isTrue();
    }

    @Test
    void reset_clearsTheBucket() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 60L, 100);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();

        limiter.reset("a");

        assertThat(limiter.tryConsume("a")).isTrue();
    }

    @Test
    void windowExpiry_allowsAgainAfterTheWindowElapses() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 1L, 100);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();

        Thread.sleep(1100);

        assertThat(limiter.tryConsume("a")).isTrue();
    }

    @Test
    void maxKeys_evictsOldestEntryWhenCapacityIsReached() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 3600L, 2);

        assertThat(limiter.tryConsume("k1")).isTrue();  // oldest bucket
        assertThat(limiter.tryConsume("k2")).isTrue();
        assertThat(limiter.tryConsume("k1")).isFalse(); // k1 already at cap within its window

        // A third distinct key pushes the map past maxKeys; since neither bucket has expired,
        // the oldest one (k1) is evicted to make room.
        assertThat(limiter.tryConsume("k3")).isTrue();

        // k1 was evicted, so it gets a fresh window and is allowed again.
        assertThat(limiter.tryConsume("k1")).isTrue();
    }
}
