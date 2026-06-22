package com.homekept.subscription;

import com.homekept.subscription.dto.CheckoutSessionRequest;
import com.homekept.subscription.dto.CheckoutSessionResponse;
import com.homekept.subscription.dto.PortalSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout and billing-portal endpoints (role: CUSTOMER).
 *
 * <p>Both endpoints resolve the subscriber from the authenticated user's JWT principal
 * (a {@code Long} user id set by {@link com.homekept.identity.JwtAuthFilter}).
 *
 * <p>DTOs cross the boundary; entities never leave this layer.
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * POST /api/checkout/session
     *
     * <p>Creates a Stripe Checkout Session and returns the hosted URL to redirect the
     * customer to. On success the customer pays on Stripe's side; the
     * {@code checkout.session.completed} webhook then activates the subscriber.
     *
     * @param request  the plan, billing cycle, and founding-rate flag
     * @param auth     injected by Spring Security — principal is the Long user id
     * @return {@code 200 { checkoutUrl }}
     */
    @PostMapping("/api/checkout/session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CheckoutSessionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        CheckoutSessionResponse response = checkoutService.createCheckoutSession(
                userId, request.planCode(), request.billingCycle(), request.foundingRate());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/billing/portal-session
     *
     * <p>Creates a Stripe Billing Portal session and returns the hosted URL. The customer
     * can use the portal to change their plan, update their payment method, or cancel.
     * State changes are delivered back via webhooks ({@code customer.subscription.updated},
     * {@code customer.subscription.deleted}).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     * @return {@code 200 { portalUrl }}
     */
    @PostMapping("/api/billing/portal-session")
    public ResponseEntity<PortalSessionResponse> createPortalSession(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        PortalSessionResponse response = checkoutService.createPortalSession(userId);
        return ResponseEntity.ok(response);
    }
}
