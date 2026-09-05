package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Admin-facing view of a reschedule request (list + confirm/decline responses).
 * Includes the subscriber id, admin note, and {@code confirmedVisitId}.
 *
 * <p>{@code confirmedVisitId} is set on confirm and is now simply equal to {@code visitId}
 * — reschedule updates the visit in place rather than creating a replacement, so there is
 * no longer a distinct "new visit" id to report. The field name and null-while-PENDING
 * semantics are unchanged; only what it points at has changed.
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
