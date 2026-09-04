package com.homekept.catalog.dto;

import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;

import java.util.List;

/**
 * Response body for a single tier in {@code GET /api/catalog/plans}.
 *
 * <p>Shape matches the api-contract.md specification exactly. Money fields are integer
 * cents.
 */
public record PlanTierResponse(
        PlanCode code,
        String displayName,
        int monthlyPriceCents,
        int annualPriceCents,
        int visitsPerYear,
        int includedPicksPerYear,
        int maxPremiumPicksPerYear,
        String description,
        List<ServiceSummary> services
) {
    /**
     * Maps a {@link PlanTier} entity (with its {@code planTierServices} eagerly loaded)
     * to the API response shape. Entities never cross the controller boundary.
     *
     * @param tier the plan tier entity
     */
    public static PlanTierResponse from(PlanTier tier) {
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
                tier.getDescription(),
                services
        );
    }
}
