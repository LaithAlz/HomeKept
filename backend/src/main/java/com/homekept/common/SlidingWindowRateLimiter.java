package com.homekept.common;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared in-memory sliding-window rate limiter: allows at most {@code maxAttempts} calls
 * to {@link #tryConsume(String)} per key within a {@code windowSeconds}-second window.
 * Per-instance only (MVP, single Render instance); replace with Bucket4j + Redis at
 * Stage 3 (arch doc §10). Bounded to {@code maxKeys} distinct keys: once full, expired
 * entries are purged before the single oldest entry is evicted, guarding against a
 * memory DoS from unbounded key churn.
 */
public class SlidingWindowRateLimiter {

    private final int maxAttempts;
    private final long windowSeconds;
    private final int maxKeys;

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxAttempts, long windowSeconds, int maxKeys) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.maxKeys = maxKeys;
    }

    public boolean tryConsume(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        return consume(key);
    }

    public void reset(String key) {
        if (key != null) {
            remove(key);
        }
    }

    /** Records an attempt against an already-normalized, non-blank key. */
    protected boolean consume(String key) {
        Instant now = Instant.now();
        ensureCapacity(key, now);

        Entry entry = attempts.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                return new Entry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() <= maxAttempts;
    }

    /** Clears an already-normalized key. */
    protected void remove(String key) {
        attempts.remove(key);
    }

    private void ensureCapacity(String incomingKey, Instant now) {
        if (attempts.size() < maxKeys || attempts.containsKey(incomingKey)) {
            return;
        }
        // Purge expired entries first.
        attempts.entrySet().removeIf(e ->
                e.getValue().windowStart.plusSeconds(windowSeconds).isBefore(now));

        if (attempts.size() >= maxKeys) {
            // Still over cap — evict the single oldest window-start entry.
            attempts.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().windowStart))
                    .map(Map.Entry::getKey)
                    .ifPresent(attempts::remove);
        }
    }

    private record Entry(Instant windowStart, AtomicInteger count) {}
}
