package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Response after a technician completes a visit
 * ({@code POST /api/tech/visits/{id}/complete}).
 */
public record TechCompleteVisitResponse(
        Long id,
        String status,
        Instant completedAt,
        Integer actualDurationMinutes,
        Integer materialsCostCents
) {}
