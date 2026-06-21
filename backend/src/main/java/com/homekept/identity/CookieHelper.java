package com.homekept.identity;

import com.homekept.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Sets and clears the httpOnly auth cookies for the access token and refresh token.
 *
 * <p>Cookie attributes:
 * <ul>
 *   <li>{@code HttpOnly} — not readable by JavaScript (XSS protection)</li>
 *   <li>{@code Secure} — HTTPS only (dev profile adds spring.profiles.active=dev
 *       and this class checks the active profile to drop Secure for plain-HTTP localhost)</li>
 *   <li>{@code SameSite=Lax} — sent on same-site navigation, not on cross-site sub-resource
 *       requests. The frontend is served at homekept.ca and the API at api.homekept.ca —
 *       same registrable domain (homekept.ca) → same-site → cookies flow. CSRF risk
 *       is therefore low. See security-config comments for full CSRF rationale.</li>
 *   <li>Access cookie path={@code /api} so it's sent on all API calls.</li>
 *   <li>Refresh cookie path={@code /api/auth/refresh} — narrower path so the
 *       7-day token is only sent to the one endpoint that needs it.</li>
 * </ul>
 */
@Component
public class CookieHelper {

    static final String ACCESS_COOKIE  = "hk_access";
    static final String REFRESH_COOKIE = "hk_refresh";

    private final long accessMaxAge;
    private final long refreshMaxAge;
    /**
     * Config-level Secure flag. True when APP_SECURE_COOKIES=true.
     * Belt-and-suspenders alongside forward-headers-strategy which makes
     * HttpServletRequest.isSecure() return true behind the TLS-terminating proxy.
     */
    private final boolean secureCookiesConfig;

    public CookieHelper(AppProperties appProperties) {
        this.accessMaxAge       = appProperties.jwt().accessTokenExpirySeconds();
        this.refreshMaxAge      = appProperties.jwt().refreshTokenExpirySeconds();
        this.secureCookiesConfig = appProperties.secureCookies();
    }

    /**
     * Sets auth cookies. The Secure flag is set when either the config property
     * APP_SECURE_COOKIES is true OR the incoming request was made over HTTPS (after
     * forward-headers-strategy translates X-Forwarded-Proto in production).
     */
    public void setAuthCookies(HttpServletResponse response,
                               String accessToken,
                               String refreshToken,
                               boolean requestIsSecure) {
        boolean secure = secureCookiesConfig || requestIsSecure;
        response.addHeader("Set-Cookie", buildCookie(ACCESS_COOKIE, accessToken, "/api", (int) accessMaxAge, secure));
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, refreshToken, "/api/auth/refresh", (int) refreshMaxAge, secure));
    }

    public void clearAuthCookies(HttpServletResponse response, boolean requestIsSecure) {
        boolean secure = secureCookiesConfig || requestIsSecure;
        response.addHeader("Set-Cookie", buildCookie(ACCESS_COOKIE, "", "/api", 0, secure));
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, "", "/api/auth/refresh", 0, secure));
    }

    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return extractCookie(request, ACCESS_COOKIE);
    }

    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return extractCookie(request, REFRESH_COOKIE);
    }

    private Optional<String> extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    /**
     * Builds a Set-Cookie header string with all required attributes.
     * Spring's {@link Cookie} API does not expose SameSite, so we build the header manually.
     */
    private String buildCookie(String name, String value, String path, int maxAge, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value).append("; ");
        sb.append("Path=").append(path).append("; ");
        sb.append("Max-Age=").append(maxAge).append("; ");
        sb.append("HttpOnly; ");
        sb.append("SameSite=Lax");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
