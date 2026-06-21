package com.homekept.catalog.dto;

import com.homekept.catalog.Service;
import com.homekept.catalog.ServiceCategory;
import com.homekept.catalog.TierClass;

/**
 * A single pickable service in the {@code GET /api/catalog/picks} response.
 * {@code aLaCartePriceCents} is always non-null for picks (standing items are excluded).
 */
public record PickServiceResponse(
        Long id,
        String name,
        ServiceCategory category,
        int aLaCartePriceCents,
        String description,
        int defaultDurationMinutes
) {
    public static PickServiceResponse from(Service service) {
        return new PickServiceResponse(
                service.getId(),
                service.getName(),
                service.getCategory(),
                service.getALaCartePriceCents(),
                service.getDescription(),
                service.getDefaultDurationMinutes()
        );
    }
}
