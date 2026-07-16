package com.homekept.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory per-IP rate limiter for {@code POST /api/auth/login}.
 *
 * <p>Complements {@link LoginRateLimiter} (which is keyed per-email). The per-email
 * limiter does nothing against <b>credential stuffing / password spraying</b>: an
 * attacker trying one common password across thousands of <em>different</em> accounts
 * from one IP never trips a per-email bucket. This limiter caps total login attempts
 * per source IP within a {@link #WINDOW_SECONDS}-second window. The cap defaults to
 * {@value #DEFAULT_MAX_ATTEMPTS} and is overridable via
 * {@code app.security.login-ip-max-attempts} (set very high in the test profile so the
 * shared test-client IP can't throttle the integration suite).
 *
 * <p>The cap is deliberately looser than the per-email limit (5/15min): a single IP can
 * legitimately be a household or small office behind NAT with several users, so we allow
 * more attempts per IP while still throttling a spray to a crawl. Every attempt counts
 * (success or failure) — there is no per-IP success reset, since one IP maps to many
 * users; a generous cap keeps legitimate shared IPs unaffected.
 *
 * <p><b>IP resolution:</b> the controller passes an IP resolved by
 * {@code ClientIpResolver} (Cloudflare {@code CF-Connecting-IP}, not spoofable through
 * the Cloudflare edge, falling back to {@code request.getRemoteAddr()} for local dev).
 * This control only binds if the origin is not directly reachable bypassing Cloudflare.
 *
 * <p><b>Note:</b> per-instance (MVP, single Render instance). Replace with Bucket4j +
 * Redis at Stage 3 (arch doc §10). Structure mirrors {@link ForgotPasswordRateLimiter}.
 *
 * <p><b>Bounded map:</b> capped at {@value MAX_KEYS} entries; expired entries are purged
 * before evicting the oldest, preventing a memory DoS from spoofed-key churn.
 */
@Component
public class LoginIpRateLimiter {

    /** Default cap when {@code app.security.login-ip-max-attempts} is not set. */
    public static final int DEFAULT_MAX_ATTEMPTS = 20;
    public static final long WINDOW_SECONDS = 15 * 60L; // 15 minutes

    static final int MAX_KEYS = 50_000;

    private final int maxAttempts;
    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public LoginIpRateLimiter(
            @Value("${app.security.login-ip-max-attempts:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Records an attempt for the given IP. Returns {@code true} if allowed, {@code false}
     * if the per-IP cap has been exceeded. A null/blank IP is allowed (fails open) so a
     * missing header can never lock out a whole instance.
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

        return entry.count.get() <= maxAttempts;
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
