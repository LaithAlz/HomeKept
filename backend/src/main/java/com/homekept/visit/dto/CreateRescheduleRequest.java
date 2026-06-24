package com.homekept.visit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Body for {@code POST /api/app/visits/{id}/reschedule-request}.
 *
 * <p>{@code preferredDates}: 1–5 proposed start times (timeslots). Each becomes a
 * {@link com.homekept.visit.RescheduleRequestSlot} row. The admin picks one (or negotiates
 * another) when confirming.
 */
public record CreateRescheduleRequest(
        @NotEmpty(message = "At least one preferred date is required")
        @Size(max = 5, message = "At most 5 preferred dates may be proposed")
        List<@NotNull(message = "Preferred dates must not be null") Instant> preferredDates
) {}
