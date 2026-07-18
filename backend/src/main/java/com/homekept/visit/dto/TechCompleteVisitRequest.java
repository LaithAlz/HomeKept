package com.homekept.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/tech/visits/{id}/complete}.
 * Transitions the visit to COMPLETED and captures per-visit unit-economics data.
 *
 * <p>{@code materialsCostCents} is in integer cents — never float. Upper bounds keep a
 * technician (semi-trusted) from poisoning unit-economics reporting with absurd values.
 */
public record TechCompleteVisitRequest(
        @Size(max = 2000) String completionNotes,

        @NotNull @Min(1) @Max(1440) Integer actualDurationMinutes,   // <= 24h

        /** Integer cents — at-cost materials used. Zero if no materials consumed. */
        @NotNull @Min(0) @Max(1_000_000) Integer materialsCostCents, // <= $10,000

        /** Optional description of materials used (for the visit report). */
        @Size(max = 2000) String materialsNotes
) {}
