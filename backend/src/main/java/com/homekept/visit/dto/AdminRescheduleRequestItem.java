package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Admin-facing view of a reschedule request (list + confirm/decline responses).
 * Includes the subscriber id, admin note, and the confirmed replacement visit id.
 */
public record AdminRescheduleRequestItem(
        Long id,
        Long visitId,
        Long subscriberId,
        String status,
        List<Instant> preferredDates,
        String adminNote,
        Long confirmedVisitId,
        Instant createdAt
) {}
