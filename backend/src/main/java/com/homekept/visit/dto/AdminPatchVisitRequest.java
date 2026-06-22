package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Request body for {@code PATCH /api/admin/visits/{id}}.
 *
 * <p>Supports three operations (all fields are optional — apply only what is present):
 * <ul>
 *   <li>Reschedule: provide {@code scheduledFor} — state machine marks old visit RESCHEDULED
 *       and creates a new SCHEDULED row.</li>
 *   <li>Cancel: provide {@code status = "CANCELLED"}.</li>
 *   <li>Assign technician: provide {@code technicianUserId}.</li>
 * </ul>
 *
 * <p>When both {@code scheduledFor} and {@code status = "CANCELLED"} are supplied,
 * the service rejects the request with a 400 (ambiguous intent).
 */
public record AdminPatchVisitRequest(
        String status,             // "CANCELLED" — drives state machine cancellation
        Instant scheduledFor,      // new date/time — triggers reschedule flow
        Long technicianUserId      // assign or reassign technician
) {}
