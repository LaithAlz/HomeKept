package com.homekept.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code PATCH /api/admin/plan-tiers/{planTierId}/services/{serviceId}} —
 * changes an existing composition row's frequency. {@code frequencyPerYear} is the only
 * field this action supports, so it is required, not optional.
 */
public record AdminUpdatePlanTierServiceRequest(
        @NotNull(message = "frequencyPerYear is required")
        @Positive(message = "frequencyPerYear must be positive")
        Integer frequencyPerYear
) {}
