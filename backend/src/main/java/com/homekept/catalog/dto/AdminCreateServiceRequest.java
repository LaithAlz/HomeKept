package com.homekept.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/services}.
 *
 * <p>{@code category} and {@code tierClass} are plain strings validated with
 * {@code @Pattern} against the exact set the {@code service} table's CHECK constraints
 * allow (V2__catalog.sql), so a bad value fails as a 400 {@code VALIDATION_FAILED} at the
 * DTO boundary rather than reaching Postgres and surfacing as a 500.
 *
 * <p>{@code aLaCartePriceCents} is nullable — {@code null} for a standing item that runs
 * free with every visit and is never sold à la carte; when present it must be a positive
 * integer cent amount (never a float).
 *
 * <p>{@code isFreeWithEveryVisit} is a boxed {@link Boolean} (Jackson 3 rejects a missing
 * primitive outright) so an omitted value can default to {@code false} in the service layer
 * rather than failing the whole request.
 */
public record AdminCreateServiceRequest(
        @NotBlank @Size(max = 200) String name,

        @NotBlank
        @Pattern(regexp = "HVAC|PLUMBING|EXTERIOR|SMART_HOME",
                 message = "category must be one of HVAC, PLUMBING, EXTERIOR, SMART_HOME")
        String category,

        @NotBlank
        @Pattern(regexp = "BASIC|MEDIUM|PREMIUM",
                 message = "tierClass must be one of BASIC, MEDIUM, PREMIUM")
        String tierClass,

        @Positive(message = "defaultDurationMinutes must be positive")
        int defaultDurationMinutes,

        @Positive(message = "aLaCartePriceCents must be positive")
        Integer aLaCartePriceCents,

        @NotBlank String description,

        Boolean isFreeWithEveryVisit
) {}
