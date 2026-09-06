package com.homekept.catalog.dto;

import com.homekept.catalog.TierClass;

/**
 * A service entry inside a plan-tier response — id, name, tier class, and how many
 * times per year it runs at this tier.
 *
 * <p>Matches the {@code services[]} shape in the {@code GET /api/catalog/plans} contract.
 * {@code id} was added alongside the admin catalog-editing endpoints so the admin console
 * can target this composition row (e.g. {@code PATCH}/{@code DELETE
 * /api/admin/plan-tiers/{planTierId}/services/{serviceId}}) without a separate lookup —
 * purely additive, existing consumers that ignore the field are unaffected.
 */
public record ServiceSummary(
        Long id,
        String name,
        TierClass tierClass,
        int frequencyPerYear
) {}
