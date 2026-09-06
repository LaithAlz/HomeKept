package com.homekept.catalog.dto;

import com.homekept.catalog.PlanCode;
import com.homekept.catalog.PlanTier;

import java.util.List;

/**
 * Response body for a single tier in {@code GET /api/catalog/plans}.
 *
 * <p>Shape matches the api-contract.md specification exactly. Money fields are integer
 * cents.
 *
 * <p>{@code id} was added alongside the admin catalog-editing endpoints so the admin
 * console can address a plan tier for the composition endpoints ({@code POST}/{@code PATCH}/
 * {@code DELETE /api/admin/plan-tiers/{planTierId}/services...}) — this endpoint was
 * otherwise the only place the frontend ever saw a plan tier at all, and it had no way to
 * recover the numeric id (plan tier ids are not stable/guessable: the September 2026
 * repositioning deleted and later re-inserted the ESSENTIAL row, so it does not keep its
 * original id). Purely additive — existing consumers that ignore the field are unaffected.
 */
public record PlanTierResponse(
        Long id,
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
                        pts.getService().getId(),
                        pts.getService().getName(),
                        pts.getService().getTierClass(),
                        pts.getFrequencyPerYear()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        return new PlanTierResponse(
                tier.getId(),
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
