package com.homekept.subscription;

import com.homekept.analytics.AnalyticsEvent;
import com.homekept.analytics.AnalyticsService;
import com.homekept.catalog.CatalogService;
import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;
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

    private final SubscriberRepository subscriberRepository;
    private final CatalogService catalogService;
    private final StripeService stripeService;
    private final AnalyticsService analytics;

    public CheckoutService(SubscriberRepository subscriberRepository,
                           CatalogService catalogService,
                           StripeService stripeService,
                           AnalyticsService analytics) {
        this.subscriberRepository = subscriberRepository;
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
     *   <li>If {@code foundingRate=true}: verifies slots are available AND the plan has
     *       a founding price — returns 409 if either condition fails.</li>
     *   <li>Calls {@link StripeService#createCheckoutSession} with a deterministic
     *       idempotency key.</li>
     * </ol>
     *
     * @param userId       the authenticated user's id (from the JWT principal)
     * @param planCode     the desired plan
     * @param billingCycle MONTHLY or ANNUAL
     * @param foundingRate whether to apply the founding-member rate
     * @return the Stripe checkout URL
     * @throws SubscriberNotFoundException      if the user has no subscriber row (404)
     * @throws FoundingRateExhaustedException   if founding slots are full or unavailable (409)
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Long userId, PlanCode planCode,
                                                         BillingCycle billingCycle,
                                                         boolean foundingRate) {
        Subscriber subscriber = subscriberRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "No subscriber row found for userId=" + userId));

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

        // Founding-rate slot reservation. The check-and-claim MUST be atomic and MUST happen
        // before the founding price id is committed to the Stripe session. Otherwise
        // concurrent checkouts each pass an unlocked availability check, all get billed the
        // founding price, and a later webhook flag-flip cannot claw the discount back (that
        // was the oversell bug). Under the transaction-scoped advisory lock we count
        // committed founding rows and, if a slot remains, durably reserve THIS row in the
        // same transaction; the next serialized checkout sees the higher count and is
        // rejected. Net effect: at most FOUNDING_CAP founding checkouts can ever be offered.
        // The reservation is SETTLED to billing reality by the webhook, which reconciles the
        // flag from the completed session's metadata (a customer who reserves founding then
        // completes a normal-price session is released back to non-founding there).
        // Tradeoff: a reserved-but-abandoned checkout holds its slot until that customer
        // activates or it is manually released (there is no PENDING_ACTIVATION cleanup job
        // yet) — acceptable at 15 slots with deliberate onboarding.
        boolean grantFounding = false;
        if (foundingRate) {
            if (!plan.hasFoundingPrice()) {
                throw new FoundingRateExhaustedException(
                        "Founding rate is not available on the " + planCode + " plan.");
            }
            subscriberRepository.lockFoundingCounter(); // serialises concurrent founding claims
            // Idempotent re-claim: if THIS row already holds a reservation it already counts
            // toward the cap, so skip the count check (which would otherwise 409 a customer
            // who already holds a slot, e.g. re-opening an expired checkout) and re-use it.
            if (!subscriber.isFoundingRate()
                    && subscriberRepository.countByFoundingRateTrue() >= FoundingRateAvailabilityImpl.FOUNDING_CAP) {
                throw new FoundingRateExhaustedException(
                        "All founding-member slots have been filled. Founding rate is no longer available.");
            }
            subscriber.setFoundingRate(true); // durable reservation, flushed at commit under the lock
            grantFounding = true;
        }

        // Deterministic idempotency key: same subscriber + plan + cycle always produces
        // the same key, so a retry of an in-flight checkout returns the same session URL.
        String idempotencyKey = StripeServiceImpl.sha256Hex(
                "checkout:" + subscriber.getId() + ":" + planCode.name() + ":" + billingCycle.name()
                        + ":" + grantFounding);

        log.info("checkout_started subscriberId={} planCode={} cycle={} foundingRate={}",
                subscriber.getId(), planCode, billingCycle, grantFounding);

        // Analytics (arch doc §5.7) — attributed to the customer, enum/flag props only, no
        // PII. capture is commit-gated + best-effort; wrap so it can never break checkout.
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("plan_code", planCode.name());
            props.put("billing_cycle", billingCycle.name());
            props.put("founding_rate", grantFounding);
            analytics.capture(userId, AnalyticsEvent.CHECKOUT_STARTED, props);
        } catch (RuntimeException e) {
            log.warn("analytics_checkout_started_failed subscriberId={}: {}", subscriber.getId(), e.toString());
        }

        String checkoutUrl = stripeService.createCheckoutSession(
                subscriber, plan, billingCycle, grantFounding, idempotencyKey);

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
        Subscriber subscriber = subscriberRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "No subscriber row found for userId=" + userId));

        if (subscriber.getStripeCustomerId() == null || subscriber.getStripeCustomerId().isBlank()) {
            throw new NoBillingAccountException(
                    "No billing account has been set up yet. Complete checkout first.");
        }

        String portalUrl = stripeService.createPortalSession(subscriber.getStripeCustomerId());
        return new PortalSessionResponse(portalUrl);
    }
}
