package com.homekept.technician;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for the staff-invite acceptance endpoints ({@code POST
 * /api/staff/invite/validate} and {@code POST /api/staff/invite/accept}): at most
 * {@value MAX_ATTEMPTS} attempts per IP within {@value WINDOW_SECONDS} seconds (10/IP/hour,
 * the same shape as {@code ActivationRateLimiter} — invite links leak via forwarded emails
 * just like activation links). IP resolved by {@code ClientIpResolver} (Cloudflare
 * {@code CF-Connecting-IP}).
 */
@Component
public class StaffInviteRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_ATTEMPTS = 10;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    public StaffInviteRateLimiter() {
        super(MAX_ATTEMPTS, WINDOW_SECONDS, 50_000);
    }
}
