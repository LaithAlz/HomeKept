package com.homekept.catalog.dto;

import com.homekept.catalog.TierClass;

/**
 * A service entry inside a plan-tier response — name, tier class, and how many times
 * per year it runs at this tier.
 *
 * <p>Matches the {@code services[]} shape in the {@code GET /api/catalog/plans} contract.
 */
public record ServiceSummary(
        String name,
        TierClass tierClass,
        int frequencyPerYear
) {}
