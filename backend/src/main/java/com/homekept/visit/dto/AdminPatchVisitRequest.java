package com.homekept.visit.dto;

import jakarta.validation.constraints.Future;

import java.time.Instant;

/**
 * Request body for {@code PATCH /api/admin/visits/{id}}.
 *
 * <p>Supports three operations (all fields are optional — apply only what is present):
 * <ul>
 *   <li>Reschedule: provide {@code scheduledFor} — updates the visit's {@code scheduledFor}
 *       (and {@code technicianUserId}, when also supplied) IN PLACE and records a
 *       {@code RESCHEDULED} {@code visit_event}. No replacement visit is created.</li>
 *   <li>Cancel: provide {@code status = "CANCELLED"}.</li>
 *   <li>Assign technician: provide {@code technicianUserId}.</li>
 * </ul>
 *
 * <p>When both {@code scheduledFor} and {@code status = "CANCELLED"} are supplied,
 * the service rejects the request with a 400 (ambiguous intent).
 *
 * <p>{@code scheduledFor}, when present, must be in the future — {@code @Future} validates
 * only non-null values, so it does not interfere with the other two operations, which omit
 * this field entirely. A missing bound here previously let a reschedule land on a past date,
 * which (like a reschedule pushed far into the future) falls outside the scheduler's
 * lookahead window; the in-place reschedule model no longer depends on the visit's position
 * relative to that window for correctness (see {@code VisitSchedulingService}'s
 * {@code templateOccurrenceYear}-keyed guard), but a past-dated reschedule is nonsensical on
 * its own terms regardless, so it is rejected at the boundary rather than merely tolerated.
 */
public record AdminPatchVisitRequest(
        String status,             // "CANCELLED" — drives state machine cancellation
        @Future(message = "scheduledFor must be in the future")
        Instant scheduledFor,      // new date/time — triggers reschedule flow
        Long technicianUserId      // assign or reassign technician
) {}
