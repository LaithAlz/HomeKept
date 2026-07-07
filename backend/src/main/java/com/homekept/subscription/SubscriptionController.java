package com.homekept.subscription;

import com.homekept.subscription.dto.AppAccountResponse;
import com.homekept.subscription.dto.AppSubscriptionResponse;
import com.homekept.subscription.dto.CancelSubscriptionRequest;
import com.homekept.subscription.dto.SubscriptionActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing subscription reads + self-serve lifecycle actions (role: CUSTOMER).
 *
 * <p>Resolves the subscriber from the authenticated user's JWT principal (a {@code Long}
 * user id set by {@link com.homekept.identity.JwtAuthFilter}) — a customer can only act on
 * their own subscription. Plan change and payment-method updates stay on the Stripe billing
 * portal ({@link CheckoutController#createPortalSession}).
 *
 * <p>Each self-serve action triggers Stripe; the resulting status transition is applied by
 * the Stripe webhook ({@link StripeWebhookService}). Responses report the current
 * (pre-webhook) status. The read endpoints ({@link #getSubscription}, {@link #getAccount})
 * are plain queries composed by {@link SubscriptionAppService}.
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
public class SubscriptionController {

    private final SubscriptionSelfServeService selfServeService;
    private final SubscriptionAppService subscriptionAppService;

    public SubscriptionController(SubscriptionSelfServeService selfServeService,
                                  SubscriptionAppService subscriptionAppService) {
        this.selfServeService = selfServeService;
        this.subscriptionAppService = subscriptionAppService;
    }

    /**
     * GET /api/app/subscription?propertyId= — the authenticated customer's plan/billing summary.
     *
     * <p>Returns 404 if the authenticated user has no matching subscriber (ownership rule —
     * matches {@code GET /api/app/visits} and {@code GET /api/app/health-score}).
     *
     * @param propertyId optional property to scope to (multi-property portfolio); see
     *                   {@link com.homekept.subscription.SubscriberQueryService#resolveOwnedSubscriber}
     * @param auth       injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/subscription")
    public ResponseEntity<AppSubscriptionResponse> getSubscription(
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.getSubscription(userId, propertyId));
    }

    /**
     * GET /api/app/account?propertyId= — the authenticated customer's profile (name, email,
     * service property address) for the settings page.
     *
     * <p>Returns 404 if the authenticated user has no matching subscriber (ownership rule).
     * Never returns decrypted property access notes — technician-only.
     *
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param auth       injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/account")
    public ResponseEntity<AppAccountResponse> getAccount(
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.getAccount(userId, propertyId));
    }

    /**
     * POST /api/app/subscription/pause?propertyId= — pause billing (eligible from ACTIVE).
     *
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param auth       injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/pause")
    public ResponseEntity<SubscriptionActionResponse> pause(
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.pause(userId, propertyId));
    }

    /**
     * POST /api/app/subscription/resume?propertyId= — resume billing (eligible from PAUSED).
     *
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param auth       injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/resume")
    public ResponseEntity<SubscriptionActionResponse> resume(
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.resume(userId, propertyId));
    }

    /**
     * POST /api/app/subscription/cancel?propertyId= — cancel at period end; records the
     * churn reason.
     *
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param request    the required cancellation reason (churn data)
     * @param auth       injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/cancel")
    public ResponseEntity<SubscriptionActionResponse> cancel(
            @RequestParam(required = false) Long propertyId,
            @Valid @RequestBody CancelSubscriptionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.cancel(userId, propertyId, request.reason()));
    }
}
