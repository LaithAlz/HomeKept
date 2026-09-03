package com.homekept.booking;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for {@code POST /api/bookings/walkthrough}: at most
 * {@value MAX_SUBMISSIONS} submissions per IP within {@value WINDOW_SECONDS} seconds
 * (3/IP/hour per api-contract.md and arch doc §5.1). IP resolved by
 * {@code ClientIpResolver} (Cloudflare {@code CF-Connecting-IP}), never a raw
 * client-supplied {@code X-Forwarded-For}, which would be spoofable.
 */
@Component
public class BookingRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_SUBMISSIONS = 3;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    public BookingRateLimiter() {
        super(MAX_SUBMISSIONS, WINDOW_SECONDS, 50_000);
    }
}
