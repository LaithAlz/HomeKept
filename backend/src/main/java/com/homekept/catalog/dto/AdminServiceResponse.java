package com.homekept.catalog.dto;

import com.homekept.catalog.Service;
import com.homekept.catalog.ServiceCategory;
import com.homekept.catalog.TierClass;

import java.time.Instant;

/**
 * Full admin-facing representation of a catalog service — unlike {@link PickServiceResponse}
 * (the public picks menu), this includes {@code active} (so the console can show archived
 * services) and the audit timestamps.
 *
 * <p>Backs {@code GET /api/admin/services}, {@code POST /api/admin/services},
 * {@code PATCH /api/admin/services/{id}}, {@code POST /api/admin/services/{id}/archive},
 * and {@code POST /api/admin/services/{id}/restore}.
 */
public record AdminServiceResponse(
        Long id,
        String name,
        ServiceCategory category,
        TierClass tierClass,
        int defaultDurationMinutes,
        Integer aLaCartePriceCents,
        String description,
        boolean isFreeWithEveryVisit,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminServiceResponse from(Service service) {
        return new AdminServiceResponse(
                service.getId(),
                service.getName(),
                service.getCategory(),
                service.getTierClass(),
                service.getDefaultDurationMinutes(),
                service.getALaCartePriceCents(),
                service.getDescription(),
                service.isFreeWithEveryVisit(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }
}
