package com.homekept.subscription;

import com.homekept.common.ClientIpResolver;
import com.homekept.identity.CookieHelper;
import com.homekept.identity.exception.RateLimitExceededException;
import com.homekept.subscription.dto.ActivationCompleteRequest;
import com.homekept.subscription.dto.ActivationCompleteResponse;
import com.homekept.subscription.dto.ActivationValidateRequest;
import com.homekept.subscription.dto.ActivationValidateResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public activation endpoints for the magic-link subscriber onboarding flow.
 *
 * <p>Both endpoints are IP rate-limited (10/IP/hour) via {@link ActivationRateLimiter}
 * using the IP resolved by {@link ClientIpResolver} (CF-Connecting-IP preferred).
 *
 * <p>These endpoints are listed in the SecurityConfig public allowlist — no JWT required.
 */
@RestController
@RequestMapping("/api/activation")
public class ActivationController {

    private final ActivationService activationService;
    private final ActivationRateLimiter rateLimiter;
    private final CookieHelper cookieHelper;

    public ActivationController(ActivationService activationService,
                                ActivationRateLimiter rateLimiter,
                                CookieHelper cookieHelper) {
        this.activationService = activationService;
        this.rateLimiter = rateLimiter;
        this.cookieHelper = cookieHelper;
    }

    /**
     * POST /api/activation/validate
     * Validates the magic-link token without consuming it.
     * Returns the prospective subscriber's first name on success.
     * Rate-limited: 10 attempts/IP/hour.
     */
    @PostMapping("/validate")
    public ResponseEntity<ActivationValidateResponse> validate(
            @Valid @RequestBody ActivationValidateRequest request,
            HttpServletRequest httpRequest) {

        String ip = ClientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }

        ActivationValidateResponse response = activationService.validate(request.token());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/activation/complete
     * Completes the activation flow: creates User, Property, Subscriber; consumes the token;
     * sets auth cookies; returns 201 with userId and next="CHECKOUT".
     * Rate-limited: 10 attempts/IP/hour.
     */
    @PostMapping("/complete")
    public ResponseEntity<ActivationCompleteResponse> complete(
            @Valid @RequestBody ActivationCompleteRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ip = ClientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }

        ActivationService.ActivationCompleteResult result =
                activationService.complete(request.token(), request.password());

        // Set auth cookies exactly as AuthController does for login
        cookieHelper.setAuthCookies(httpResponse,
                result.accessToken(), result.refreshToken(),
                httpRequest.isSecure());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ActivationCompleteResponse(result.userId(), "CHECKOUT"));
    }
}
