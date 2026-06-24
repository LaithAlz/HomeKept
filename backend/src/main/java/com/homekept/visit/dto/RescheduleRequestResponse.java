package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Customer-facing view of a reschedule request
 * (response of {@code POST /api/app/visits/{id}/reschedule-request}).
 */
public record RescheduleRequestResponse(
        Long id,
        Long visitId,
        String status,
        List<Instant> preferredDates,
        Instant createdAt
) {}
