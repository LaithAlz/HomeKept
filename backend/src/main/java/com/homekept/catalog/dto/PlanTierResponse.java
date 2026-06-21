package com.homekept.catalog.dto;

import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;

import java.util.List;

/**
 * Response body for a single tier in {@code GET /api/catalog/plans}.
 *
 * <p>Shape matches the api-contract.md specification exactly. Money fields are integer
 * cents. {@code foundingMonthlyPriceCents} is nullable — only COMPLETE has a founding rate.
 *
 * <p>{@code foundingRateAvailable} is true when BOTH conditions hold:
 * <ol>
 *   <li>This tier has a seeded {@code founding_monthly_price_cents} ({@link PlanTier#hasFoundingPrice()}).</li>
 *   <li>The global founding-slot count is under 15 (passed in from {@link com.homekept.catalog.FoundingRateAvailability}).</li>
 * </ol>
 */
public record PlanTierResponse(
        PlanCode code,
        String displayName,
        int monthlyPriceCents,
        int annualPriceCents,
        int visitsPerYear,
        int includedPicksPerYear,
        int maxPremiumPicksPerYear,
        boolean foundingRateAvailable,
        Integer foundingMonthlyPriceCents,
        String description,
        List<ServiceSummary> services
) {
    /**
     * Maps a {@link PlanTier} entity (with its {@code planTierServices} eagerly loaded)
     * to the API response shape. Entities never cross the controller boundary.
     *
     * @param tier           the plan tier entity
     * @param slotsRemaining whether founding slots remain globally (from {@link com.homekept.catalog.FoundingRateAvailability})
     */
    public static PlanTierResponse from(PlanTier tier, boolean slotsRemaining) {
        List<ServiceSummary> services = tier.getPlanTierServices().stream()
                .map(pts -> new ServiceSummary(
                        pts.getService().getName(),
                        pts.getService().getTierClass(),
                        pts.getFrequencyPerYear()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        return new PlanTierResponse(
                tier.getCode(),
                tier.getDisplayName(),
                tier.getMonthlyPriceCents(),
                tier.getAnnualPriceCents(),
                tier.getVisitsPerYear(),
                tier.getIncludedPicksPerYear(),
                tier.getMaxPremiumPicksPerYear(),
                tier.hasFoundingPrice() && slotsRemaining,
                tier.getFoundingMonthlyPriceCents(),
                tier.getDescription(),
                services
        );
    }
}
