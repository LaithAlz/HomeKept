package com.homekept.identity;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for {@code POST /api/auth/reset}: at most {@value MAX_ATTEMPTS} attempts per
 * IP within {@value WINDOW_SECONDS} seconds (5/IP/hour — same shape as
 * {@link ForgotPasswordRateLimiter}). Before this, reset was the only public
 * auth-mutating endpoint with no throttle at all: an attacker holding (or brute-forcing) a
 * raw token could hammer it without limit. IP resolved by {@code ClientIpResolver}
 * (Cloudflare {@code CF-Connecting-IP}).
 */
@Component
public class ResetPasswordRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    public ResetPasswordRateLimiter() {
        super(MAX_ATTEMPTS, WINDOW_SECONDS, 50_000);
    }
}
