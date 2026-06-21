package com.homekept.booking.dto;

import java.time.Instant;

/**
 * Request DTO for {@code PATCH /api/admin/bookings/{id}}.
 * Both fields are optional (partial update):
 * <ul>
 *   <li>{@code status} — desired next status; validated through the state machine</li>
 *   <li>{@code scheduledFor} — sets the confirmed walk-through date/time</li>
 * </ul>
 */
public record AdminPatchBookingRequest(
        String status,
        Instant scheduledFor
) {}
