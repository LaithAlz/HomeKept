package com.homekept.visit.dto;

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
) {}
