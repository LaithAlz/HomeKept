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
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * Fixed wall-clock budget (ms) that {@code POST /api/auth/forgot} pads BOTH the
     * found-email and not-found-email branches to, so response time can't reveal whether an
     * email belongs to an account (#115 finding 1). Chosen comfortably above a real SendGrid
     * send; public so the integration test can assert against the real value instead of a
     * hardcoded duplicate.
     */
    public static final long FORGOT_RESPONSE_BUDGET_MS = 500;

    /** Light jitter applied to the target itself, so the padded response isn't a constant. */
    public static final long FORGOT_RESPONSE_JITTER_MS = 50;

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
     *
     * <p>Constant-time response (#115 finding 1): after {@link AuthService#forgotPassword}
     * returns — i.e. after its {@code @Transactional} block has committed and released its
     * pooled DB connection — this method pads the response to a shared fixed wall-clock
     * budget on BOTH the found-email and not-found-email paths, via {@link #padToBudget}.
     * A one-sided delay on only the unknown-email branch would not work here: SendGrid is
     * unconfigured by default (#120), so the found-email branch's outbound send currently
     * log-and-skips in a few ms too, and a one-sided sleep would make the unknown-email
     * branch the slow one instead — a clean, worse oracle. Padding both branches to the same
     * budget is required regardless of whether the outbound send is actually configured.
     */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request,
                                       HttpServletRequest httpRequest) {
        String ip = ClientIpResolver.resolve(httpRequest);
        if (!forgotPasswordRateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }
        long startNanos = System.nanoTime();
        authService.forgotPassword(request.email());
        padToBudget(startNanos);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Sleeps out the remainder of a fixed (lightly jittered) response-time budget, measured
     * from {@code startNanos}. Called only after the triggering service call has returned —
     * never from inside an {@code @Transactional} method — so the sleep never pins a pooled
     * Hikari connection (a DoS amplifier: the pool defaults to 10 connections).
     *
     * <p><b>Tradeoff/limitations:</b> this ties up a request-handling thread for up to
     * {@value #FORGOT_RESPONSE_BUDGET_MS}ms per request. The complete fix is asynchronous
     * email dispatch (arch doc Stage 2 note on {@code SendGridEmailSender}, shared infra with
     * #89), which would let both branches return immediately instead of padding on the
     * request thread; that infra isn't in place yet. This budget also only fully hides timing
     * while the real work finishes under budget — a real send that happens to take longer
     * than {@value #FORGOT_RESPONSE_BUDGET_MS}ms would show through, since no padding is
     * applied once elapsed time already exceeds the target.
     */
    private void padToBudget(long startNanos) {
        long jitter = ThreadLocalRandom.current()
                .nextLong(-FORGOT_RESPONSE_JITTER_MS, FORGOT_RESPONSE_JITTER_MS + 1);
        long targetMs = FORGOT_RESPONSE_BUDGET_MS + jitter;
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        long remainingMs = targetMs - elapsedMs;
        if (remainingMs > 0) {
            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
