package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.identity.UserProfileService;
import com.homekept.identity.UserQueryService;
import com.homekept.property.Property;
import com.homekept.property.PropertyService;
import com.homekept.subscription.dto.AppAccountResponse;
import com.homekept.subscription.dto.AppInvoiceResponse;
import com.homekept.subscription.dto.AppPaymentMethodResponse;
import com.homekept.subscription.dto.AppSubscriptionResponse;
import com.homekept.visit.VisitQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
 *   <li>identity → {@link UserQueryService#findProfileById}, {@link UserProfileService#updateProfile}</li>
 *   <li>property → {@link PropertyService#findById}</li>
 *   <li>visit → {@link VisitQueryService#findNextScheduledVisitDate}</li>
 * </ul>
 *
 * <p>{@link #listInvoices} and {@link #getDefaultPaymentMethod} reach Stripe through
 * {@link StripeService} only, and always resolve the subscriber's {@code stripeCustomerId}
 * first — no Stripe call is made at all when it is blank/null (a subscriber who hasn't
 * completed checkout yet), matching the graceful-degradation contract of those endpoints.
 *
 * <h2>Pre-subscription users</h2>
 * <p>A user with no {@link Subscriber} row gets {@link SubscriberNotFoundException} (→ 404),
 * matching how {@code AppVisitController} and {@code AppHealthScoreController} treat the
 * same case (ownership-failure rule: not-found and not-yours both return 404). In practice
 * every CUSTOMER-role user has a subscriber row from the moment their account is created
 * (activation creates {@code User}, {@code Property}, and {@code Subscriber} together), so
 * this is a defensive guard rather than an expected steady-state response.
 */
@Service
public class SubscriptionAppService {

    /** Max invoices returned by {@link #listInvoices} — api-contract.md caps the page at 24. */
    private static final int MAX_INVOICES = 24;

    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final UserQueryService userQueryService;
    private final UserProfileService userProfileService;
    private final PropertyService propertyService;
    private final VisitQueryService visitQueryService;
    private final StripeService stripeService;

    public SubscriptionAppService(SubscriberQueryService subscriberQueryService,
                                  CatalogService catalogService,
                                  UserQueryService userQueryService,
                                  UserProfileService userProfileService,
                                  PropertyService propertyService,
                                  VisitQueryService visitQueryService,
                                  StripeService stripeService) {
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.userQueryService = userQueryService;
        this.userProfileService = userProfileService;
        this.propertyService = propertyService;
        this.visitQueryService = visitQueryService;
        this.stripeService = stripeService;
    }

    /**
     * Returns the authenticated customer's plan/billing summary.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @throws SubscriberNotFoundException if the user has no subscriber row (→ 404)
     */
    @Transactional(readOnly = true)
    public AppSubscriptionResponse getSubscription(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);

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
                subscriber.getCurrentPeriodStart(),
                subscriber.getCurrentPeriodEnd(),
                nextVisitDate
        );
    }

    /**
     * Returns the authenticated customer's account profile (name, email, service address).
     *
     * @param userId the authenticated user's id (JWT principal)
     * @throws SubscriberNotFoundException if the user has no subscriber row (→ 404)
     */
    @Transactional(readOnly = true)
    public AppAccountResponse getAccount(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);

        var profile = userQueryService.findProfileById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No user row for authenticated userId — should be impossible"));

        Property property = propertyService.findById(subscriber.getPropertyId());

        return toAccountResponse(profile, property);
    }

    /**
     * Updates the authenticated customer's name/phone and returns the refreshed account
     * profile (same shape as {@link #getAccount}). Each parameter is optional: {@code null}
     * leaves the corresponding value unchanged. Email and the service property address are
     * not editable through this method.
     *
     * @param userId    the authenticated user's id (JWT principal)
     * @param firstName new first name, or {@code null} to leave unchanged
     * @param lastName  new last name, or {@code null} to leave unchanged
     * @param phone     new phone, or {@code null} to leave unchanged
     * @throws SubscriberNotFoundException if the user has no subscriber row (→ 404)
     * @throws com.homekept.identity.exception.InvalidAccountUpdateRequestException
     *         if a provided field fails validation (→ 400)
     */
    @Transactional
    public AppAccountResponse updateAccount(Long userId, String firstName, String lastName, String phone) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);

        UserQueryService.UserProfile profile =
                userProfileService.updateProfile(userId, firstName, lastName, phone);

        Property property = propertyService.findById(subscriber.getPropertyId());

        return toAccountResponse(profile, property);
    }

    /**
     * Returns the authenticated customer's billing history, newest first, capped at
     * {@value #MAX_INVOICES}.
     *
     * <p>Returns an empty list — never an error — when the subscriber has no Stripe
     * customer id yet (no checkout completed); {@link StripeService#listInvoices} itself
     * degrades gracefully (also empty, never an error) when Stripe isn't configured.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @throws SubscriberNotFoundException if the user has no subscriber row (→ 404)
     */
    @Transactional(readOnly = true)
    public List<AppInvoiceResponse> listInvoices(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);
        String stripeCustomerId = subscriber.getStripeCustomerId();
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return List.of();
        }

        return stripeService.listInvoices(stripeCustomerId, MAX_INVOICES).stream()
                .map(invoice -> new AppInvoiceResponse(
                        invoice.id(),
                        invoice.number(),
                        invoice.createdAt(),
                        invoice.amountPaidCents(),
                        invoice.currency(),
                        invoice.status(),
                        invoice.hostedInvoiceUrl(),
                        invoice.invoicePdf()))
                .toList();
    }

    /**
     * Returns the authenticated customer's default payment method (the card on file), or
     * {@code null} when there is none or the subscriber has no Stripe customer id yet.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @throws SubscriberNotFoundException if the user has no subscriber row (→ 404)
     */
    @Transactional(readOnly = true)
    public AppPaymentMethodResponse getDefaultPaymentMethod(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);
        String stripeCustomerId = subscriber.getStripeCustomerId();
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return null;
        }

        return stripeService.findDefaultPaymentMethod(stripeCustomerId)
                .map(pm -> new AppPaymentMethodResponse(
                        pm.brand(), pm.last4(), pm.expMonth(), pm.expYear()))
                .orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AppAccountResponse toAccountResponse(UserQueryService.UserProfile profile, Property property) {
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

    /**
     * Resolves the price actually charged for the subscriber's billing cycle.
     */
    private Integer resolvePriceCents(Subscriber subscriber, CatalogService.PlanTierSummary plan) {
        return subscriber.getBillingCycle() == BillingCycle.ANNUAL
                ? plan.annualPriceCents()
                : plan.monthlyPriceCents();
    }
}
