package com.homekept.subscription;

import com.homekept.analytics.AnalyticsEvent;
import com.homekept.analytics.AnalyticsService;
import com.homekept.catalog.CatalogService;
import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;
import com.homekept.common.Hashing;
import com.homekept.subscription.dto.CheckoutSessionResponse;
import com.homekept.subscription.dto.PortalSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrates Stripe checkout and billing-portal session creation for the
 * {@code CUSTOMER} role.
 *
 * <p>Domain boundary: this service is in {@code subscription} and calls {@code catalog}
 * only via {@link CatalogService} — never its repository or entities directly.
 *
 * <p>Money: no arithmetic here. Integer cents live in the DB and are resolved by
 * {@link PlanTier} getters. Stripe price ids are strings — never integers.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final StripeService stripeService;
    private final AnalyticsService analytics;

    public CheckoutService(SubscriberQueryService subscriberQueryService,
                           CatalogService catalogService,
                           StripeService stripeService,
                           AnalyticsService analytics) {
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.stripeService = stripeService;
        this.analytics = analytics;
    }

    /**
     * Creates a Stripe Checkout Session for the authenticated subscriber.
     *
     * <ol>
     *   <li>Resolves the subscriber by {@code userId} — 404 if none exists yet.</li>
     *   <li>Validates the plan code and billing cycle.</li>
     *   <li>Fails closed with 409 {@code PLAN_NOT_PURCHASABLE} if the plan has no Stripe
     *       price id configured for the requested cycle yet — never falls through to
     *       Stripe with a blank price id, and never charges a stale price.</li>
     *   <li>Calls {@link StripeService#createCheckoutSession} with a deterministic
     *       idempotency key.</li>
     * </ol>
     *
     * @param userId       the authenticated user's id (from the JWT principal)
     * @param planCode     the desired plan
     * @param billingCycle MONTHLY or ANNUAL
     * @return the Stripe checkout URL
     * @throws SubscriberNotFoundException  if the user has no subscriber row (404)
     * @throws PlanNotPurchasableException  if the plan has no Stripe price id yet (409)
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Long userId, PlanCode planCode,
                                                         BillingCycle billingCycle) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);

        // Only a brand-new, unpaid subscription may check out. CANCELLED is terminal (a
        // returning customer is a NEW subscriber row — see SubscriberStatus), and an
        // ACTIVE/PAUSED/PAYMENT_ISSUE subscriber already has a live Stripe subscription; a
        // second checkout would create a duplicate subscription that the webhook cannot
        // activate (only PENDING_ACTIVATION → ACTIVE is legal), i.e. Stripe charges the
        // customer with no provisioning. Plan/billing changes go through the billing portal.
        if (subscriber.getStatus() != SubscriberStatus.PENDING_ACTIVATION) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.ACTIVE);
        }

        PlanTier plan = catalogService.findPlanTierByCode(planCode);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown planCode: " + planCode);
        }

        // Fail closed: resolve the intended Stripe price id for this cycle BEFORE ever
        // calling Stripe. A blank price id (e.g. COMPLETE's ids cleared pending new Stripe
        // prices) must return 409, never reach Stripe, and never fall back to a stale price.
        String priceId = switch (billingCycle) {
            case MONTHLY -> plan.getStripePriceIdMonthly();
            case ANNUAL  -> plan.getStripePriceIdAnnual();
        };
        if (priceId == null || priceId.isBlank()) {
            log.warn("checkout_blocked_no_price subscriberId={} planCode={} billingCycle={}",
                    subscriber.getId(), planCode, billingCycle);

            // Analytics (arch doc §5.7) — enum/flag props only, no PII. capture() is itself
            // commit-gated and best-effort. Lets the funnel show blocked checkouts, not just
            // silent 409s in the client.
            Map<String, Object> blockedProps = new LinkedHashMap<>();
            blockedProps.put("plan_code", planCode.name());
            blockedProps.put("billing_cycle", billingCycle.name());
            blockedProps.put("reason", "no_price");
            analytics.capture(userId, AnalyticsEvent.CHECKOUT_BLOCKED, blockedProps);

            throw new PlanNotPurchasableException();
        }

        // Deterministic idempotency key: same subscriber + plan + cycle + resolved Stripe
        // price id. Including the price id means a reprice (new price id swapped into the
        // plan tier) mints a fresh key instead of colliding with a stale key and returning
        // Stripe's idempotency_error for the key's 24h retention window.
        String idempotencyKey = Hashing.sha256Hex(
                "checkout:" + subscriber.getId() + ":" + planCode.name() + ":" + billingCycle.name()
                        + ":" + priceId);

        log.info("checkout_started subscriberId={} planCode={} cycle={}",
                subscriber.getId(), planCode, billingCycle);

        // Analytics (arch doc §5.7) — attributed to the customer, enum/flag props only, no
        // PII. capture() is itself commit-gated and best-effort.
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plan_code", planCode.name());
        props.put("billing_cycle", billingCycle.name());
        analytics.capture(userId, AnalyticsEvent.CHECKOUT_STARTED, props);

        String checkoutUrl = stripeService.createCheckoutSession(
                subscriber, plan, billingCycle, idempotencyKey);

        return new CheckoutSessionResponse(checkoutUrl);
    }

    /**
     * Creates a Stripe Billing Portal session for the authenticated subscriber.
     *
     * <p>The subscriber must have a Stripe customer id (set by
     * {@code checkout.session.completed}) — returns 409 if not set yet.
     *
     * @param userId the authenticated user's id
     * @return the Stripe portal URL
     * @throws SubscriberNotFoundException if the user has no subscriber row (404)
     * @throws NoBillingAccountException   if no Stripe customer id exists yet (409)
     */
    @Transactional(readOnly = true)
    public PortalSessionResponse createPortalSession(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);

        if (subscriber.getStripeCustomerId() == null || subscriber.getStripeCustomerId().isBlank()) {
            throw new NoBillingAccountException(
                    "No billing account has been set up yet. Complete checkout first.");
        }

        String portalUrl = stripeService.createPortalSession(subscriber.getStripeCustomerId());
        return new PortalSessionResponse(portalUrl);
    }
}
