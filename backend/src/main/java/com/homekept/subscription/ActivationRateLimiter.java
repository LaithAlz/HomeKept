package com.homekept.subscription;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for the activation endpoints ({@code POST /api/activation/validate}
 * and {@code POST /api/activation/complete}): at most {@value MAX_ATTEMPTS} attempts
 * per IP within {@value WINDOW_SECONDS} seconds (10/IP/hour per api-contract.md). IP
 * resolved by {@code ClientIpResolver} (Cloudflare {@code CF-Connecting-IP}).
 */
@Component
public class ActivationRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_ATTEMPTS = 10;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    public ActivationRateLimiter() {
        super(MAX_ATTEMPTS, WINDOW_SECONDS, 50_000);
    }
}
