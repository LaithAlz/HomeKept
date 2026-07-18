package com.homekept.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code POST /api/admin/visits}.
 *
 * <p>{@code technicianUserId} is optional — admin may assign a technician later.
 * {@code serviceIds} is optional — if omitted the visit is created with only the
 * template's standing items (when a templateId can be inferred) or no services.
 */
public record AdminCreateVisitRequest(
        @NotNull Long subscriberId,
        @NotNull Instant scheduledFor,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
        List<Long> serviceIds,           // optional; if provided, added as source=TEMPLATE or EXTRA
        Long technicianUserId            // optional
) {}
