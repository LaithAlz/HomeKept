package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Visit representation returned by admin endpoints.
 * No PII — IDs, enums, timestamps, cents only.
 */
public record AdminVisitResponse(
        Long id,
        Long subscriberId,
        Long propertyId,
        Long technicianId,           // nullable
        Long visitTemplateId,        // nullable
        Instant scheduledFor,
        int durationMinutes,
        Integer actualDurationMinutes,
        Integer materialsCostCents,  // integer cents; nullable
        String status,
        String type,
        String completionNotes,
        Instant completedAt,
        Instant createdAt,
        List<VisitServiceItem> services
) {}
