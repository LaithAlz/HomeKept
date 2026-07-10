package com.homekept.identity;

import com.homekept.common.ClientIpResolver;
import com.homekept.identity.dto.ForgotPasswordRequest;
import com.homekept.identity.dto.LoginRequest;
import com.homekept.identity.dto.MeResponse;
import com.homekept.identity.dto.ResetPasswordRequest;
import com.homekept.identity.exception.RateLimitExceededException;
import com.homekept.identity.exception.TokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Auth endpoints: login, refresh, logout, me, forgot, reset.
 * DTOs cross in/out; entities never leave this layer.
 *
 * <p>Cookie handling is delegated to {@link CookieHelper}. The {@code Secure} flag is set
 * when either {@code APP_SECURE_COOKIES=true} (config) OR the incoming request was made
 * over HTTPS — {@code server.forward-headers-strategy: framework} makes
 * {@link HttpServletRequest#isSecure()} return {@code true} behind the TLS-terminating
 * proxy in production (Render / Cloudflare).
 *
 * <p>Logout is a public endpoint: a user with an expired access token can still revoke
 * their refresh tokens. The handler resolves the user from the refresh cookie directly.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieHelper cookieHelper;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;

    public AuthController(AuthService authService, CookieHelper cookieHelper,
                          ForgotPasswordRateLimiter forgotPasswordRateLimiter) {
        this.authService = authService;
        this.cookieHelper = cookieHelper;
        this.forgotPasswordRateLimiter = forgotPasswordRateLimiter;
    }

    /**
     * POST /api/auth/login
     * Rate-limited: 5 attempts per email per 15 minutes.
     * Returns the same 401 for unknown email as for wrong password (no enumeration).
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        AuthService.TokenPair tokens = authService.login(request.email(), request.password());
        cookieHelper.setAuthCookies(httpResponse, tokens.accessToken(), tokens.refreshToken(),
                httpRequest.isSecure());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/auth/refresh
     * Reads the refresh cookie, rotates it, and issues new cookies.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        String rawRefreshToken = cookieHelper.extractRefreshToken(httpRequest)
                .orElseThrow(() -> new TokenException(TokenException.Reason.NOT_FOUND));
        AuthService.TokenPair tokens = authService.refresh(rawRefreshToken);
        cookieHelper.setAuthCookies(httpResponse, tokens.accessToken(), tokens.refreshToken(),
                httpRequest.isSecure());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/auth/logout
     * Revokes all refresh tokens and clears both cookies.
     *
     * <p>This endpoint is public (no valid access token required) so a user whose access
     * token has expired can still invalidate their refresh tokens. User resolution order:
     * <ol>
     *   <li>If there is an authenticated principal (valid access cookie), use that user ID.</li>
     *   <li>Otherwise, validate the refresh cookie (if present) to find the user.</li>
     * </ol>
     * Always clears cookies and returns 204 regardless of whether a user was identified.
     * No information is leaked about whether the tokens were valid.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        try {
            if (authentication != null && authentication.isAuthenticated()) {
                // Happy path: valid access token present.
                Long userId = (Long) authentication.getPrincipal();
                authService.logout(userId);
            } else {
                // Access token absent or expired — try the refresh cookie.
                Optional<String> refreshToken = cookieHelper.extractRefreshToken(httpRequest);
                if (refreshToken.isPresent()) {
                    authService.logoutViaRefreshToken(refreshToken.get());
                }
            }
        } catch (Exception ignored) {
            // Silently swallow: always clear cookies and return 204, never leak token validity.
        }
        cookieHelper.clearAuthCookies(httpResponse, httpRequest.isSecure());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me
     * Returns the authenticated user's public profile.
     * The JwtAuthFilter has already validated the token; Spring Security provides the auth.
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.me(userId));
    }

    /**
     * POST /api/auth/forgot
     * Always returns 202, whether or not the email belongs to an account — no
     * enumeration (api-contract.md). Rate-limited: 5 attempts per IP per hour.
     */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request,
                                       HttpServletRequest httpRequest) {
        String ip = ClientIpResolver.resolve(httpRequest);
        if (!forgotPasswordRateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }
        authService.forgotPassword(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * POST /api/auth/reset
     * Consumes the reset token, sets the new password, and revokes all the user's refresh
     * tokens. Fresh auth cookies (auto-sign-in) are set only if the user is ACTIVE — see
     * {@link AuthService#resetPassword}. Always 200 either way: the password change itself
     * succeeds regardless of auto-sign-in eligibility.
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        Optional<AuthService.TokenPair> tokens = authService.resetPassword(request.token(), request.password());
        tokens.ifPresent(t -> cookieHelper.setAuthCookies(httpResponse, t.accessToken(), t.refreshToken(),
                httpRequest.isSecure()));
        return ResponseEntity.ok().build();
    }
}
