package com.homekept.visit.dto;

/**
 * Request body for {@code PATCH /api/tech/visits/{id}/services/{visitServiceId}}.
 * Tick or untick a checklist item, optionally adding technician notes.
 */
public record TechPatchServiceRequest(
        boolean completed,
        String technicianNotes   // nullable
) {}
