package com.homekept.visit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/tech/visits/{id}/complete}.
 * Transitions the visit to COMPLETED and captures per-visit unit-economics data.
 *
 * <p>{@code materialsCostCents} is in integer cents — never float.
 */
public record TechCompleteVisitRequest(
        String completionNotes,

        @NotNull @Min(1) Integer actualDurationMinutes,

        /** Integer cents — at-cost materials used. Zero if no materials consumed. */
        @NotNull @Min(0) Integer materialsCostCents,

        /** Optional description of materials used (for the visit report). */
        String materialsNotes
) {}
