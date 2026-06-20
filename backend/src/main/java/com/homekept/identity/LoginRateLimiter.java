package com.homekept.identity;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory per-email rate limiter for the login endpoint.
 * Allows at most {@value MAX_ATTEMPTS} attempts per email within a
 * {@value WINDOW_SECONDS}-second window.
 *
 * <p><b>Note:</b> this limiter is per-instance (MVP, single Render instance).
 * At scale, replace with Bucket4j + Redis (arch doc Stage 3).
 *
 * <p><b>Key normalization:</b> keys are {@code email.strip().toLowerCase(Locale.ROOT)}
 * so {@code " Admin@X.com "} and {@code "admin@x.com"} map to the same bucket,
 * preventing whitespace-bypass attacks. The same normalization is applied on lookup
 * so it agrees with the normalization in AuthService.
 *
 * <p><b>Bounded map:</b> capped at {@value MAX_KEYS} entries. When the cap is reached,
 * a pass of expired entries is performed before evicting oldest if needed. This prevents
 * a memory DoS from an attacker submitting random email addresses.
 */
@Component
public class LoginRateLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final long WINDOW_SECONDS = 15 * 60L; // 15 minutes

    /** Maximum number of distinct email keys held in memory at once. */
    static final int MAX_KEYS = 10_000;

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    /**
     * Normalises an email key: strips surrounding whitespace and lowercases with Locale.ROOT.
     * Must match the normalization used in AuthService for the user lookup.
     */
    static String normalizeKey(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Records an attempt for the given email. Returns {@code true} if the attempt
     * is allowed, {@code false} if the limit has been exceeded.
     *
     * @param email the email address being attempted
     * @return {@code true} if allowed, {@code false} if rate-limited
     */
    public boolean tryConsume(String email) {
        String key = normalizeKey(email);
        Instant now = Instant.now();

        ensureCapacity(key, now);

        Entry entry = attempts.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                // New window
                return new Entry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() <= MAX_ATTEMPTS;
    }

    /** Resets the counter for an email (used when login succeeds to avoid penalising legit users). */
    public void reset(String email) {
        attempts.remove(normalizeKey(email));
    }

    /**
     * If the map is at capacity, first purge all expired entries (window has elapsed).
     * If still at capacity after purging, remove the oldest entry to make room.
     * This keeps the map bounded without a background thread.
     */
    private void ensureCapacity(String incomingKey, Instant now) {
        if (attempts.size() < MAX_KEYS || attempts.containsKey(incomingKey)) {
            return;
        }
        // Purge expired entries first.
        attempts.entrySet().removeIf(e ->
                e.getValue().windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now));

        if (attempts.size() >= MAX_KEYS) {
            // Still over cap — evict the single oldest window-start entry.
            attempts.entrySet().stream()
                    .min(java.util.Comparator.comparing(e -> e.getValue().windowStart))
                    .map(java.util.Map.Entry::getKey)
                    .ifPresent(attempts::remove);
        }
    }

    private record Entry(Instant windowStart, AtomicInteger count) {}
}
