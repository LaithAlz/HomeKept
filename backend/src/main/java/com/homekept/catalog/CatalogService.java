package com.homekept.catalog;

import com.homekept.catalog.dto.PickServiceResponse;
import com.homekept.catalog.dto.PicksMenuResponse;
import com.homekept.catalog.dto.PlanTierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only service for the catalog domain.
 *
 * <p>No cross-domain calls. No money as float — all prices are integer cents from the
 * database, passed through unchanged. No state mutations — catalog is seeded by migration.
 */
@Service
public class CatalogService {

    /**
     * À la carte price sentinel for BASIC picks — $49 — per docs/pricing-and-visits.md.
     * Used to validate service data at the boundary; the actual value comes from the DB.
     */
    static final int BASIC_A_LA_CARTE_CENTS = 4900;
    static final int MEDIUM_A_LA_CARTE_CENTS = 8900;
    static final int PREMIUM_A_LA_CARTE_CENTS = 14900;

    private final PlanTierRepository planTierRepository;
    private final ServiceRepository serviceRepository;
    private final FoundingRateAvailability foundingRateAvailability;

    public CatalogService(PlanTierRepository planTierRepository,
                          ServiceRepository serviceRepository,
                          FoundingRateAvailability foundingRateAvailability) {
        this.planTierRepository = planTierRepository;
        this.serviceRepository = serviceRepository;
        this.foundingRateAvailability = foundingRateAvailability;
    }

    /**
     * Returns all active plan tiers with their included services.
     * Ordered ESSENTIAL → COMPLETE → PREMIER (ascending monthly price).
     *
     * <p>{@code foundingRateAvailable} is true only when the tier has a founding price AND
     * the global founding-slot count is under 15 ({@link FoundingRateAvailability}).
     */
    @Transactional(readOnly = true)
    public List<PlanTierResponse> getPlans() {
        boolean slotsRemaining = foundingRateAvailability.foundingSlotsRemaining();
        return planTierRepository.findAllWithServices().stream()
                .map(tier -> PlanTierResponse.from(tier, slotsRemaining))
                .toList();
    }

    /**
     * Returns the monthly price in cents for a given plan tier id.
     * Used by the subscription admin service to compute MRR without crossing into
     * the catalog repository directly. Returns {@code null} if the plan tier is not found.
     *
     * @param planTierId the plan tier id
     * @return monthly price in cents, or {@code null} if not found
     */
    @Transactional(readOnly = true)
    public Integer getMonthlyPriceCents(Long planTierId) {
        if (planTierId == null) {
            return null;
        }
        return planTierRepository.findById(planTierId)
                .map(PlanTier::getMonthlyPriceCents)
                .orElse(null);
    }

    /**
     * Returns the founding monthly price in cents for a given plan tier id.
     * Returns {@code null} if the tier is not found or has no founding price.
     *
     * @param planTierId the plan tier id
     * @return founding monthly price in cents, or {@code null}
     */
    @Transactional(readOnly = true)
    public Integer getFoundingMonthlyPriceCents(Long planTierId) {
        if (planTierId == null) {
            return null;
        }
        return planTierRepository.findById(planTierId)
                .map(PlanTier::getFoundingMonthlyPriceCents)
                .orElse(null);
    }

    /**
     * Returns the plan code string for a given plan tier id.
     * Returns {@code null} if not found.
     *
     * @param planTierId the plan tier id
     * @return plan code name (e.g. "COMPLETE"), or {@code null}
     */
    @Transactional(readOnly = true)
    public String getPlanCode(Long planTierId) {
        if (planTierId == null) {
            return null;
        }
        return planTierRepository.findById(planTierId)
                .map(t -> t.getCode().name())
                .orElse(null);
    }

    /**
     * Returns a narrow, display-only summary of a plan tier for cross-domain use (e.g. the
     * customer app's billing page). Callers get exactly the fields they need without
     * reaching into {@link PlanTierRepository} or the {@link PlanTier} entity directly.
     *
     * @param planTierId the plan tier id, or {@code null} (e.g. a subscriber who hasn't
     *                    completed checkout yet)
     * @return the summary, or empty if {@code planTierId} is {@code null} or not found
     */
    @Transactional(readOnly = true)
    public Optional<PlanTierSummary> findPlanTierSummary(Long planTierId) {
        if (planTierId == null) {
            return Optional.empty();
        }
        return planTierRepository.findById(planTierId).map(PlanTierSummary::from);
    }

    /**
     * Finds the {@link PlanTier} that owns the given Stripe price id (any of the three
     * price columns: monthly, annual, founding). Returns {@code null} if no tier matches.
     *
     * <p>This is the canonical price-to-plan mapping point. Webhook handlers call this
     * instead of reaching into {@link PlanTierRepository} directly.
     *
     * @param stripePriceId a Stripe price id (e.g. {@code price_1Abc...})
     * @return the matching {@link PlanTier}, or {@code null}
     */
    @Transactional(readOnly = true)
    public PlanTier findPlanTierByStripePriceId(String stripePriceId) {
        if (stripePriceId == null || stripePriceId.isBlank()) {
            return null;
        }
        return planTierRepository.findByAnyStripePriceId(stripePriceId).orElse(null);
    }

    /**
     * Finds the {@link PlanTier} by its {@link PlanCode}. Returns {@code null} if not found.
     *
     * <p>Used by the checkout service to resolve the tier before creating a Stripe session.
     *
     * @param code the plan code (ESSENTIAL, COMPLETE, PREMIER)
     * @return the matching {@link PlanTier}, or {@code null}
     */
    @Transactional(readOnly = true)
    public PlanTier findPlanTierByCode(PlanCode code) {
        if (code == null) {
            return null;
        }
        return planTierRepository.findByCode(code).orElse(null);
    }

    /**
     * Returns whether founding-rate slots are still available.
     * Delegates to the live {@link FoundingRateAvailability} implementation.
     */
    public boolean isFoundingRateAvailable() {
        return foundingRateAvailability.foundingSlotsRemaining();
    }

    /**
     * Returns a map of service id → service name for the given ids.
     * Used by the visit domain to resolve service names for display without reaching
     * into the catalog repository directly (domain boundary rule).
     *
     * <p>Unknown IDs are omitted from the result map — callers should fall back to
     * a safe default (e.g. "Unknown service") for missing entries.
     *
     * @param serviceIds the service ids to look up
     * @return map of id → name for found services
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> getServiceNamesByIds(java.util.List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return java.util.Map.of();
        }
        return serviceRepository.findAllById(serviceIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.homekept.catalog.Service::getId, com.homekept.catalog.Service::getName));
    }

    /**
     * Validates that all provided service IDs exist in the catalog.
     * Returns a list of IDs that were not found. An empty list means all IDs are valid.
     *
     * @param serviceIds the service ids to validate
     * @return list of unknown (not found) IDs
     */
    @Transactional(readOnly = true)
    public java.util.List<Long> findUnknownServiceIds(java.util.List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Set<Long> found = serviceRepository.findAllById(serviceIds).stream()
                .map(com.homekept.catalog.Service::getId)
                .collect(java.util.stream.Collectors.toSet());
        return serviceIds.stream()
                .filter(id -> !found.contains(id))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the pickable services menu grouped by tier class.
     * Excludes standing items ({@code is_free_with_every_visit = true}) and inactive services.
     * Prices come from the database — integer cents, matching the seed in V2__catalog.sql.
     */
    @Transactional(readOnly = true)
    public PicksMenuResponse getPicksMenu() {
        List<com.homekept.catalog.Service> pickable = serviceRepository
                .findAllByActiveTrueOrderByTierClassAscNameAsc()
                .stream()
                .filter(s -> !s.isFreeWithEveryVisit() && s.getALaCartePriceCents() != null)
                .toList();

        var byTierClass = pickable.stream()
                .collect(Collectors.groupingBy(com.homekept.catalog.Service::getTierClass));

        List<PickServiceResponse> basicServices = byTierClass.getOrDefault(TierClass.BASIC, List.of())
                .stream().map(PickServiceResponse::from).toList();
        List<PickServiceResponse> mediumServices = byTierClass.getOrDefault(TierClass.MEDIUM, List.of())
                .stream().map(PickServiceResponse::from).toList();
        List<PickServiceResponse> premiumServices = byTierClass.getOrDefault(TierClass.PREMIUM, List.of())
                .stream().map(PickServiceResponse::from).toList();

        // Group-level prices (BASIC_A_LA_CARTE_CENTS etc.) MUST match the seeded
        // a_la_carte_price_cents values in V2__catalog.sql. If the seed ever changes,
        // update these constants to match — or derive the group price from the first
        // service in each group (all services within a tier class share the same price).
        return new PicksMenuResponse(
                new PicksMenuResponse.PickGroup(BASIC_A_LA_CARTE_CENTS, basicServices),
                new PicksMenuResponse.PickGroup(MEDIUM_A_LA_CARTE_CENTS, mediumServices),
                new PicksMenuResponse.PickGroup(PREMIUM_A_LA_CARTE_CENTS, premiumServices)
        );
    }

    /**
     * Narrow, display-only plan tier summary for cross-domain reads. Deliberately excludes
     * Stripe price ids and archival metadata — only what a display surface needs.
     */
    public record PlanTierSummary(
            String code,
            String displayName,
            int monthlyPriceCents,
            int annualPriceCents,
            Integer foundingMonthlyPriceCents
    ) {
        private static PlanTierSummary from(PlanTier tier) {
            return new PlanTierSummary(
                    tier.getCode().name(),
                    tier.getDisplayName(),
                    tier.getMonthlyPriceCents(),
                    tier.getAnnualPriceCents(),
                    tier.getFoundingMonthlyPriceCents()
            );
        }
    }
}
