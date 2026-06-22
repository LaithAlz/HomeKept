package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Response after a technician marks a visit INCOMPLETE
 * ({@code POST /api/tech/visits/{id}/incomplete}).
 *
 * <p>Includes the id of the auto-created follow-up visit so the client can surface it.
 */
public record TechIncompleteVisitResponse(
        Long id,
        String status,
        Long followUpVisitId,
        Instant followUpScheduledFor
) {}
