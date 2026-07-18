package com.homekept.visit.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/tech/visits/{id}/services/{visitServiceId}}.
 * Tick or untick a checklist item, optionally adding technician notes.
 */
public record TechPatchServiceRequest(
        boolean completed,
        @Size(max = 2000) String technicianNotes   // nullable
) {}
