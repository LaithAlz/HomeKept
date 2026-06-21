package com.homekept.catalog.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/catalog/picks}.
 * Grouped by tier class with the à la carte price for that group.
 * Money is integer cents.
 *
 * <p>Groups are always present even if empty (defensive: the seed always populates all three).
 */
public record PicksMenuResponse(
        PickGroup basic,
        PickGroup medium,
        PickGroup premium
) {
    public record PickGroup(
            int aLaCartePriceCents,
            List<PickServiceResponse> services
    ) {}
}
