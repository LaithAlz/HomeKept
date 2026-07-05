package com.homekept.identity;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory per-IP rate limiter for {@code POST /api/auth/forgot}.
 *
 * <p>Allows at most {@value MAX_ATTEMPTS} attempts per IP within a
 * {@value WINDOW_SECONDS}-second window (5/IP/hour per api-contract.md).
 *
 * <p><b>IP resolution:</b> the controller passes an IP resolved by
 * {@code ClientIpResolver} (Cloudflare {@code CF-Connecting-IP}, not
 * spoofable through the Cloudflare edge, falling back to
 * {@code request.getRemoteAddr()} for local dev and health probes).
 *
 * <p><b>Note:</b> per-instance (MVP, single Render instance). Replace with
 * Bucket4j + Redis at Stage 3 (arch doc §10). Mirrors {@code ActivationRateLimiter}.
 *
 * <p><b>Bounded map:</b> capped at {@value MAX_KEYS} entries; expired entries
 * are purged before evicting the oldest.
 */
@Component
public class ForgotPasswordRateLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final long WINDOW_SECONDS = 60 * 60L; // 1 hour

    static final int MAX_KEYS = 50_000;

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    /**
     * Records an attempt for the given IP. Returns {@code true} if the attempt is
     * allowed, {@code false} if the rate limit has been exceeded.
     *
     * @param ip client IP (resolved by {@code ClientIpResolver}, preferring CF-Connecting-IP)
     * @return {@code true} if allowed, {@code false} if rate-limited
     */
    public boolean tryConsume(String ip) {
        if (ip == null || ip.isBlank()) {
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

        return entry.count.get() <= MAX_ATTEMPTS;
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
