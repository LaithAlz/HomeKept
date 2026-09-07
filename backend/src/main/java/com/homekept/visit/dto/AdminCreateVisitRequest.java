package com.homekept.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code POST /api/admin/visits}.
 *
 * <p>{@code technicianUserId} is optional — admin may assign a technician later. When
 * present it must resolve to a real user with the TECHNICIAN role (validated via the
 * identity domain's service, never its repository — see
 * {@code VisitAdminService#requireValidTechnician}) or the request is rejected with 400;
 * {@code visit.technician_id} doubles as an authorization principal for that visit's and
 * its property's operational notes, so an unvalidated id would be a standing-access bug.
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
