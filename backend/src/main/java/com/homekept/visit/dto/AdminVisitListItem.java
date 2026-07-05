package com.homekept.visit.dto;

import com.homekept.visit.Visit;

import java.time.Instant;

/**
 * Summary DTO returned in the admin visit list.
 * {@code GET /api/admin/visits} — cursor-paginated, id-descending, optional status filter.
 *
 * <p>No PII — {@code subscriberId} and {@code technicianId} are bare cross-domain ids,
 * matching {@link AdminVisitResponse} and {@link AdminRescheduleRequestItem}. Leaves out
 * {@code completionNotes} (free-text) and the nested {@code services} list (heavy) that
 * {@link AdminVisitResponse} carries — those belong to a future single-visit detail view.
 */
public record AdminVisitListItem(
        Long id,
        Long subscriberId,
        Long propertyId,
        Long technicianId,          // nullable — unassigned
        Instant scheduledFor,
        int durationMinutes,
        Integer actualDurationMinutes,
        Integer materialsCostCents, // integer cents; nullable
        String status,
        String type,
        Instant completedAt,
        Instant createdAt
) {
    public static AdminVisitListItem from(Visit v) {
        return new AdminVisitListItem(
                v.getId(),
                v.getSubscriberId(),
                v.getPropertyId(),
                v.getTechnicianId(),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getActualDurationMinutes(),
                v.getMaterialsCostCents(),
                v.getStatus().name(),
                v.getType().name(),
                v.getCompletedAt(),
                v.getCreatedAt()
        );
    }
}
