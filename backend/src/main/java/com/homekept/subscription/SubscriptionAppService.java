package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.identity.UserQueryService;
import com.homekept.property.Property;
import com.homekept.property.PropertyService;
import com.homekept.subscription.dto.AppAccountResponse;
import com.homekept.subscription.dto.AppSubscriptionResponse;
import com.homekept.visit.VisitQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Read-only customer app queries for the authenticated subscriber's plan/billing state and
 * account profile ({@code GET /api/app/subscription}, {@code GET /api/app/account}).
 *
 * <p>Split from {@link SubscriptionSelfServeService} (which handles the mutating
 * pause/resume/cancel actions and their Stripe/idempotency concerns) since these are plain
 * reads with a different shape of complexity: composing narrow lookups across domains.
 *
 * <h2>Domain boundaries</h2>
 * <p>Cross-domain reads go through service interfaces only, never repositories or entities:
 * <ul>
 *   <li>catalog → {@link CatalogService#findPlanTierSummary}</li>
 *   <li>identity → {@link UserQueryService#findProfileById}</li>
 *   <li>property → {@link PropertyService#findById}</li>
 *   <li>visit → {@link VisitQueryService#findNextScheduledVisitDate}</li>
 * </ul>
 *
 * <h2>Pre-subscription users</h2>
 * <p>A user with no matching {@link Subscriber} row gets {@link SubscriberNotFoundException}
 * (→ 404, via {@link SubscriberQueryService#resolveOwnedSubscriber}), matching how
 * {@code AppVisitController} and {@code AppHealthScoreController} treat the same case
 * (ownership-failure rule: not-found and not-yours both return 404). In practice
 * every CUSTOMER-role user has a subscriber row from the moment their account is created
 * (activation creates {@code User}, {@code Property}, and {@code Subscriber} together), so
 * this is a defensive guard rather than an expected steady-state response.
 */
@Service
public class SubscriptionAppService {

    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final UserQueryService userQueryService;
    private final PropertyService propertyService;
    private final VisitQueryService visitQueryService;

    public SubscriptionAppService(SubscriberQueryService subscriberQueryService,
                                  CatalogService catalogService,
                                  UserQueryService userQueryService,
                                  PropertyService propertyService,
                                  VisitQueryService visitQueryService) {
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.userQueryService = userQueryService;
        this.propertyService = propertyService;
        this.visitQueryService = visitQueryService;
    }

    /**
     * Returns the authenticated customer's plan/billing summary.
     *
     * @param userId     the authenticated user's id (JWT principal)
     * @param propertyId optional property to scope to (multi-property portfolio); see
     *                   {@link SubscriberQueryService#resolveOwnedSubscriber}
     * @throws SubscriberNotFoundException if the user has no matching subscriber (→ 404)
     */
    @Transactional(readOnly = true)
    public AppSubscriptionResponse getSubscription(Long userId, Long propertyId) {
        Subscriber subscriber = requireSubscriber(userId, propertyId);

        String planCode = null;
        String planDisplayName = null;
        Integer priceCents = null;

        var planSummary = catalogService.findPlanTierSummary(subscriber.getPlanTierId());
        if (planSummary.isPresent()) {
            var plan = planSummary.get();
            planCode = plan.code();
            planDisplayName = plan.displayName();
            priceCents = resolvePriceCents(subscriber, plan);
        }

        Instant nextVisitDate = visitQueryService.findNextScheduledVisitDate(subscriber.getId())
                .orElse(null);

        return new AppSubscriptionResponse(
                subscriber.getStatus().name(),
                planCode,
                planDisplayName,
                subscriber.getBillingCycle().name(),
                priceCents,
                subscriber.isFoundingRate(),
                subscriber.getFoundingRateExpiresAt(),
                subscriber.getCurrentPeriodStart(),
                subscriber.getCurrentPeriodEnd(),
                nextVisitDate
        );
    }

    /**
     * Returns the authenticated customer's account profile (name, email, service address).
     *
     * @param userId     the authenticated user's id (JWT principal)
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @throws SubscriberNotFoundException if the user has no matching subscriber (→ 404)
     */
    @Transactional(readOnly = true)
    public AppAccountResponse getAccount(Long userId, Long propertyId) {
        Subscriber subscriber = requireSubscriber(userId, propertyId);

        var profile = userQueryService.findProfileById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No user row for authenticated userId — should be impossible"));

        Property property = propertyService.findById(subscriber.getPropertyId());

        return new AppAccountResponse(
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                property != null ? property.getStreetAddress() : null,
                property != null ? property.getUnit() : null,
                property != null ? property.getCity() : null,
                property != null ? property.getPostalCode() : null
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Subscriber requireSubscriber(Long userId, Long propertyId) {
        return subscriberQueryService.resolveOwnedSubscriber(userId, propertyId);
    }

    /**
     * Resolves the price actually charged for the subscriber's billing cycle.
     * Founding rate (when active and set on the plan) takes precedence over the regular
     * monthly/annual price; founding rate is always billed monthly per docs/pricing-and-visits.md.
     */
    private Integer resolvePriceCents(Subscriber subscriber, CatalogService.PlanTierSummary plan) {
        if (subscriber.isFoundingRate() && plan.foundingMonthlyPriceCents() != null) {
            return plan.foundingMonthlyPriceCents();
        }
        return subscriber.getBillingCycle() == BillingCycle.ANNUAL
                ? plan.annualPriceCents()
                : plan.monthlyPriceCents();
    }
}
