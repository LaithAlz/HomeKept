package com.homekept.identity;

import com.homekept.common.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-IP limiter for {@code POST /api/auth/login}, complementing {@link LoginRateLimiter}:
 * catches credential stuffing / password spraying (one IP hitting many different accounts)
 * that a per-email cap can't see. Cap defaults to {@value DEFAULT_MAX_ATTEMPTS} and is
 * overridable via {@code app.security.login-ip-max-attempts} (set very high in the test
 * profile). Deliberately looser than the per-email limit since one IP may be a shared
 * household or office behind NAT; every attempt counts (no success reset, since one IP
 * maps to many users).
 */
@Component
public class LoginIpRateLimiter extends SlidingWindowRateLimiter {

    /** Default cap when {@code app.security.login-ip-max-attempts} is not set. */
    public static final int DEFAULT_MAX_ATTEMPTS = 20;
    public static final long WINDOW_SECONDS = 15 * 60L; // 15 minutes

    public LoginIpRateLimiter(
            @Value("${app.security.login-ip-max-attempts:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        super(maxAttempts, WINDOW_SECONDS, 50_000);
    }
}
