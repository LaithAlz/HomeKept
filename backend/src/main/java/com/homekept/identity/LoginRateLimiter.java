package com.homekept.identity;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Per-email limiter for {@code POST /api/auth/login}: at most {@value MAX_ATTEMPTS}
 * attempts per email within {@value WINDOW_SECONDS} seconds. Keys are normalized via
 * {@link #normalizeKey} (stripped, lowercased) to match AuthService's lookup and prevent
 * a whitespace/case bypass, and reset on a successful login so legitimate users aren't
 * penalised by earlier failed attempts.
 */
@Component
public class LoginRateLimiter extends SlidingWindowRateLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final long WINDOW_SECONDS = 15 * 60L; // 15 minutes

    public LoginRateLimiter() {
        super(MAX_ATTEMPTS, WINDOW_SECONDS, 10_000);
    }

    /**
     * Normalises an email key: strips surrounding whitespace and lowercases with Locale.ROOT.
     * Must match the normalization used in AuthService for the user lookup.
     */
    static String normalizeKey(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean tryConsume(String email) {
        return consume(normalizeKey(email));
    }

    @Override
    public void reset(String email) {
        remove(normalizeKey(email));
    }
}
