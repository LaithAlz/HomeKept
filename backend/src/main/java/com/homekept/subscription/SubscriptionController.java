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
     * GET /api/app/subscription — the authenticated customer's plan/billing summary.
     *
     * <p>Returns 404 if the authenticated user has no subscriber row (ownership rule —
     * matches {@code GET /api/app/visits} and {@code GET /api/app/health-score}).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/subscription")
    public ResponseEntity<AppSubscriptionResponse> getSubscription(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.getSubscription(userId));
    }

    /**
     * GET /api/app/account — the authenticated customer's profile (name, email, service
     * property address) for the settings page.
     *
     * <p>Returns 404 if the authenticated user has no subscriber row (ownership rule).
     * Never returns decrypted property access notes — technician-only.
     *
     * @param auth injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/account")
    public ResponseEntity<AppAccountResponse> getAccount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.getAccount(userId));
    }

    /**
     * POST /api/app/subscription/pause — pause billing (eligible from ACTIVE).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/pause")
    public ResponseEntity<SubscriptionActionResponse> pause(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.pause(userId));
    }

    /**
     * POST /api/app/subscription/resume — resume billing (eligible from PAUSED).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/resume")
    public ResponseEntity<SubscriptionActionResponse> resume(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.resume(userId));
    }

    /**
     * POST /api/app/subscription/cancel — cancel at period end; records the churn reason.
     *
     * @param request the required cancellation reason (churn data)
     * @param auth    injected by Spring Security — principal is the Long user id
     * @return {@code 200 { status, currentPeriodEnd }}
     */
    @PostMapping("/api/app/subscription/cancel")
    public ResponseEntity<SubscriptionActionResponse> cancel(
            @Valid @RequestBody CancelSubscriptionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(selfServeService.cancel(userId, request.reason()));
    }
}
