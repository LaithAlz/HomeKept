package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full visit detail for the customer app ({@code GET /api/app/visits/{id}}).
 *
 * <p>Includes checklist items and photos (signed download URLs, ~15-min TTL). Notes and
 * health-score are deferred to later slices.
 */
public record AppVisitDetail(
        Long id,
        String name,
        Instant scheduledFor,
        int durationMinutes,
        Integer actualDurationMinutes,   // nullable — filled at completion
        Integer materialsCostCents,      // nullable — filled at completion; integer cents
        String status,
        String type,
        String completionNotes,          // nullable
        Instant completedAt,             // nullable
        String technicianFirstName,      // nullable — technician slice not yet built
        List<VisitServiceItem> services,
        List<AppVisitPhoto> photos,      // empty if R2 unconfigured or no photos — never fabricated
        boolean hasPendingRescheduleRequest  // true iff a PENDING reschedule_request exists for this visit
) {}
