package com.homekept.booking;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory per-IP rate limiter for {@code POST /api/bookings/walkthrough}.
 * Allows at most {@value MAX_SUBMISSIONS} submissions per IP within a
 * {@value WINDOW_SECONDS}-second window (3/IP/hour per api-contract.md and arch doc §5.1).
 *
 * <p><b>IP resolution:</b> the controller passes an IP resolved by {@code ClientIpResolver}
 * (the Cloudflare-set {@code CF-Connecting-IP} header — which a client cannot forge through
 * Cloudflare — falling back to {@code getRemoteAddr()}). The limiter deliberately does NOT
 * key on a raw client-supplied {@code X-Forwarded-For}, which would be spoofable.
 *
 * <p><b>Note:</b> per-instance (MVP, single Render instance). Replace with Bucket4j +
 * Redis at Stage 3 (arch doc).
 *
 * <p><b>Bounded map:</b> capped at {@value MAX_KEYS} entries. When the cap is reached,
 * expired entries are purged before evicting the oldest — mirrors {@code LoginRateLimiter}.
 */
@Component
public class BookingRateLimiter {

    public static final int MAX_SUBMISSIONS = 3;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    static final int MAX_KEYS = 50_000;

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    /**
     * Records an attempt for the given IP. Returns {@code true} if the attempt is
     * allowed, {@code false} if the rate limit has been exceeded.
     *
     * @param ip client IP address (resolved by {@code ClientIpResolver}, preferring CF-Connecting-IP)
     * @return {@code true} if allowed, {@code false} if rate-limited
     */
    public boolean tryConsume(String ip) {
        if (ip == null || ip.isBlank()) {
            // Unknown IP — allow but log nothing (belt-and-suspenders)
            return true;
        }
        Instant now = Instant.now();
        ensureCapacity(ip, now);

        Entry entry = attempts.compute(ip, (k, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Entry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() <= MAX_SUBMISSIONS;
    }

    /** Resets the counter for an IP (used in tests). */
    public void reset(String ip) {
        if (ip != null) {
            attempts.remove(ip);
        }
    }

    private void ensureCapacity(String incomingKey, Instant now) {
        if (attempts.size() < MAX_KEYS || attempts.containsKey(incomingKey)) {
            return;
        }
        attempts.entrySet().removeIf(e ->
                e.getValue().windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now));

        if (attempts.size() >= MAX_KEYS) {
            attempts.entrySet().stream()
                    .min(java.util.Comparator.comparing(e -> e.getValue().windowStart))
                    .map(java.util.Map.Entry::getKey)
                    .ifPresent(attempts::remove);
        }
    }

    private record Entry(Instant windowStart, AtomicInteger count) {}
}
