package com.homekept.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/admin/plan-tiers/{planTierId}/services} — adds a
 * service to a plan tier's composition at the given frequency.
 *
 * <p>Both fields are boxed and required ({@code @NotNull}) so a missing field fails as a
 * clean 400 {@code VALIDATION_FAILED} with a field-level message rather than a generic
 * malformed-body error.
 */
public record AdminAddPlanTierServiceRequest(
        @NotNull(message = "serviceId is required") Long serviceId,

        @NotNull(message = "frequencyPerYear is required")
        @Positive(message = "frequencyPerYear must be positive")
        Integer frequencyPerYear
) {}
