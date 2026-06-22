package com.homekept.technician.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/admin/technicians}.
 *
 * <p>Role assignment to TECHNICIAN is the founder's concern (done at the identity layer
 * before calling this endpoint) — this endpoint only creates the
 * {@code technician_profile} row for an existing user who already has the TECHNICIAN role.
 *
 * <p>{@code fullyLoadedHourlyCostCents} is in integer cents. Set a notional value from
 * day 1 so per-visit unit economics are real numbers from the first visit.
 */
public record CreateTechnicianRequest(
        @NotNull Long userId,

        /**
         * Fully-loaded hourly cost in integer cents (salary + benefits + overhead).
         * Nullable at onboarding time — update via a separate admin endpoint or DB patch
         * once the cost is known.
         */
        @Positive Integer fullyLoadedHourlyCostCents,

        String employeeStatus,

        LocalDate hireDate
) {}
