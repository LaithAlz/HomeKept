package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;
import com.homekept.subscription.dto.CheckoutSessionResponse;
import com.homekept.subscription.dto.PortalSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CheckoutService(SubscriberRepository subscriberRepository,
                           CatalogService catalogService,
                           StripeService stripeService) {
        this.subscriberRepository = subscriberRepository;
        this.catalogService = catalogService;
        this.stripeService = stripeService;
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
    @Transactional(readOnly = true)
    public CheckoutSessionResponse createCheckoutSession(Long userId, PlanCode planCode,
                                                         BillingCycle billingCycle,
                                                         boolean foundingRate) {
        Subscriber subscriber = subscriberRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "No subscriber row found for userId=" + userId));

        PlanTier plan = catalogService.findPlanTierByCode(planCode);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown planCode: " + planCode);
        }

        if (foundingRate) {
            if (!plan.hasFoundingPrice()) {
                throw new FoundingRateExhaustedException(
                        "Founding rate is not available on the " + planCode + " plan.");
            }
            if (!catalogService.isFoundingRateAvailable()) {
                throw new FoundingRateExhaustedException(
                        "All founding-member slots have been filled. Founding rate is no longer available.");
            }
        }

        // Deterministic idempotency key: same subscriber + plan + cycle always produces
        // the same key, so a retry of an in-flight checkout returns the same session URL.
        String idempotencyKey = StripeServiceImpl.sha256Hex(
                "checkout:" + subscriber.getId() + ":" + planCode.name() + ":" + billingCycle.name()
                        + ":" + foundingRate);

        log.info("checkout_started subscriberId={} planCode={} cycle={} foundingRate={}",
                subscriber.getId(), planCode, billingCycle, foundingRate);

        String checkoutUrl = stripeService.createCheckoutSession(
                subscriber, plan, billingCycle, foundingRate, idempotencyKey);

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
