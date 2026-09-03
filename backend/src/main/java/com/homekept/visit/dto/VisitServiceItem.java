package com.homekept.visit.dto;

import com.homekept.visit.VisitService;

import java.util.Map;

/**
 * A single checklist item within a visit response.
 * No PII — IDs, enums, booleans only.
 */
public record VisitServiceItem(
        Long id,
        Long serviceId,
        String serviceName,
        String source,
        boolean completed,
        String technicianNotes   // nullable
) {
    /**
     * Maps a {@link VisitService} checklist row to its response DTO, resolving the display
     * name from a pre-loaded {@code serviceId -> name} map (batch-loaded by the caller via
     * {@code CatalogService#getServiceNamesByIds} to avoid an N+1 query).
     */
    public static VisitServiceItem from(VisitService vs, Map<Long, String> nameById) {
        return new VisitServiceItem(
                vs.getId(),
                vs.getServiceId(),
                nameById.getOrDefault(vs.getServiceId(), "Unknown service"),
                vs.getSource().name(),
                vs.isCompleted(),
                vs.getTechnicianNotes()
        );
    }
}
