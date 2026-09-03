package com.homekept.identity;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for {@code POST /api/auth/forgot}: at most {@value MAX_ATTEMPTS}
 * attempts per IP within {@value WINDOW_SECONDS} seconds (5/IP/hour per api-contract.md).
 * IP resolved by {@code ClientIpResolver} (Cloudflare {@code CF-Connecting-IP}).
 */
@Component
public class ForgotPasswordRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    public ForgotPasswordRateLimiter() {
        super(MAX_ATTEMPTS, WINDOW_SECONDS, 50_000);
    }
}
