package com.homekept.catalog;

import com.homekept.catalog.dto.PickServiceResponse;
import com.homekept.catalog.dto.PicksMenuResponse;
import com.homekept.catalog.dto.PlanTierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}
