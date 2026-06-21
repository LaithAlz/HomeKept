package com.homekept.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the trustworthy client IP address from an incoming HTTP request.
 *
 * <p><b>Resolution order:</b>
 * <ol>
 *   <li>{@code CF-Connecting-IP} header — Cloudflare sets this at the edge and overwrites
 *       any value the client supplied. It is <em>not</em> a standard forwarded-for header,
 *       so {@code ForwardedHeaderFilter} does not strip or rewrite it. When present and
 *       non-blank, this is the authoritative client IP.</li>
 *   <li>{@code request.getRemoteAddr()} — used as a fallback when the request did not
 *       arrive via Cloudflare (local dev, health probes, etc.).</li>
 * </ol>
 *
 * <p><b>Abuse-resistance scope:</b> the rate limiter keyed on the result of this method
 * is only spoofing-resistant when the deployment is fronted by Cloudflare (per
 * {@code docs/decisions.md}, HomeKept's production path always is). A client behind
 * Cloudflare cannot forge {@code CF-Connecting-IP}; they <em>can</em> forge
 * {@code X-Forwarded-For}, which is intentionally ignored here.
 *
 * <p><b>Future hardening:</b> a trusted-proxy allowlist (only accept
 * {@code CF-Connecting-IP} from known Cloudflare CIDR ranges) or Cloudflare Turnstile
 * should be evaluated at Stage 3 (arch doc §10) when bot pressure warrants it.
 */
public final class ClientIpResolver {

    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private ClientIpResolver() {}

    /**
     * Returns the resolved client IP for the given request.
     *
     * @param request the incoming HTTP request
     * @return a non-null IP string; may be empty only if both the header and
     *         {@code getRemoteAddr()} return blank (should not occur in practice)
     */
    public static String resolve(HttpServletRequest request) {
        String cfIp = request.getHeader(CF_CONNECTING_IP);
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        return request.getRemoteAddr();
    }
}
