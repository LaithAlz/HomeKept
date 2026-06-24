package com.homekept.visit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body for {@code POST /api/admin/reschedule-requests/{id}/confirm}.
 *
 * <p>{@code scheduledFor}: the concrete time the admin is rescheduling the visit to —
 * typically one of the customer's proposed slots, but the admin may choose another after
 * coordinating. Drives the visit reschedule (RESCHEDULED old + new SCHEDULED visit).
 */
public record AdminConfirmRescheduleRequest(
        @NotNull(message = "scheduledFor is required")
        Instant scheduledFor,

        @Size(max = 1000, message = "Note must be at most 1000 characters")
        String adminNote
) {}
