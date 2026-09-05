package com.homekept.technician;

import com.homekept.common.ClientIpResolver;
import com.homekept.identity.CookieHelper;
import com.homekept.identity.exception.RateLimitExceededException;
import com.homekept.technician.dto.StaffInviteAcceptRequest;
import com.homekept.technician.dto.StaffInviteAcceptResponse;
import com.homekept.technician.dto.StaffInviteValidateRequest;
import com.homekept.technician.dto.StaffInviteValidateResponse;
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
 * Public staff-invite acceptance endpoints for the technician onboarding flow — the
 * TECHNICIAN-role counterpart to {@code ActivationController}'s customer magic-link flow.
 *
 * <p>Both endpoints are IP rate-limited (10/IP/hour) via {@link StaffInviteRateLimiter}
 * using the IP resolved by {@link ClientIpResolver} (CF-Connecting-IP preferred).
 *
 * <p>These endpoints are listed in the SecurityConfig public allowlist — no JWT required.
 * The role granted on accept is always server-set to TECHNICIAN; it is never read from the
 * request or the token.
 */
@RestController
@RequestMapping("/api/staff/invite")
public class StaffInviteController {

    private final StaffInviteService staffInviteService;
    private final StaffInviteRateLimiter rateLimiter;
    private final CookieHelper cookieHelper;

    public StaffInviteController(StaffInviteService staffInviteService,
                                 StaffInviteRateLimiter rateLimiter,
                                 CookieHelper cookieHelper) {
        this.staffInviteService = staffInviteService;
        this.rateLimiter = rateLimiter;
        this.cookieHelper = cookieHelper;
    }

    /**
     * POST /api/staff/invite/validate
     * Validates the invite token without consuming it. Returns the invited technician's
     * first name on success. Rate-limited: 10 attempts/IP/hour.
     */
    @PostMapping("/validate")
    public ResponseEntity<StaffInviteValidateResponse> validate(
            @Valid @RequestBody StaffInviteValidateRequest request,
            HttpServletRequest httpRequest) {

        String ip = ClientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }

        return ResponseEntity.ok(staffInviteService.validate(request.token()));
    }

    /**
     * POST /api/staff/invite/accept
     * Consumes the invite token, sets the chosen password, flips the technician
     * PENDING_ACTIVATION -> ACTIVE, and signs them in. Rate-limited: 10 attempts/IP/hour.
     */
    @PostMapping("/accept")
    public ResponseEntity<StaffInviteAcceptResponse> accept(
            @Valid @RequestBody StaffInviteAcceptRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ip = ClientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException();
        }

        StaffInviteService.AcceptResult result =
                staffInviteService.accept(request.token(), request.password());

        // Set auth cookies exactly as ActivationController does for the customer flow.
        cookieHelper.setAuthCookies(httpResponse,
                result.accessToken(), result.refreshToken(),
                httpRequest.isSecure());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StaffInviteAcceptResponse(result.userId()));
    }
}
