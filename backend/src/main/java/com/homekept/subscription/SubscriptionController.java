package com.homekept.subscription;

import com.homekept.subscription.dto.AppAccountResponse;
import com.homekept.subscription.dto.AppAccountUpdateRequest;
import com.homekept.subscription.dto.AppInvoiceResponse;
import com.homekept.subscription.dto.AppPaymentMethodResponse;
import com.homekept.subscription.dto.AppSubscriptionResponse;
import com.homekept.subscription.dto.CancelSubscriptionRequest;
import com.homekept.subscription.dto.SubscriptionActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

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
     * PATCH /api/app/account — updates the authenticated customer's first name, last name,
     * and/or phone. Each field is optional; a field omitted or {@code null} leaves the
     * corresponding value unchanged. Email and the service property address are not
     * editable here (see {@link AppAccountUpdateRequest}).
     *
     * <p>Returns 404 if the authenticated user has no subscriber row (ownership rule),
     * 400 if a provided field fails validation.
     *
     * @param auth injected by Spring Security — principal is the Long user id
     */
    @PatchMapping("/api/app/account")
    public ResponseEntity<AppAccountResponse> updateAccount(
            @RequestBody AppAccountUpdateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.updateAccount(
                userId, request.firstName(), request.lastName(), request.phone()));
    }

    /**
     * GET /api/app/billing/invoices — the authenticated customer's billing history, newest
     * first, capped at 24. Reads through {@link StripeService#listInvoices}; returns an
     * empty list (never an error) when the subscriber has no Stripe customer id yet, or when
     * Stripe isn't configured.
     *
     * <p>Returns 404 if the authenticated user has no subscriber row (ownership rule).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/billing/invoices")
    public ResponseEntity<List<AppInvoiceResponse>> listInvoices(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(subscriptionAppService.listInvoices(userId));
    }

    /**
     * GET /api/app/billing/payment-method — the authenticated customer's default card on
     * file, or {@code null} when there is none or the subscriber has no Stripe customer id
     * yet. The response is wrapped in an {@link Optional} purely so Spring actually invokes
     * the JSON message converter for a "no result" response — a bare {@code null} return
     * value from a {@code ResponseEntity<T>} controller method writes an empty body instead
     * of the literal JSON {@code null} the API contract specifies.
     *
     * <p>Returns 404 if the authenticated user has no subscriber row (ownership rule).
     *
     * @param auth injected by Spring Security — principal is the Long user id
     */
    @GetMapping("/api/app/billing/payment-method")
    public ResponseEntity<Optional<AppPaymentMethodResponse>> getDefaultPaymentMethod(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(Optional.ofNullable(subscriptionAppService.getDefaultPaymentMethod(userId)));
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
