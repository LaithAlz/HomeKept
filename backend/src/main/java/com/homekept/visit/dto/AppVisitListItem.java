package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Summary of a visit for the customer-facing paginated list
 * ({@code GET /api/app/visits}).
 *
 * <p>Matches the API contract shape: id, name (template name or type), scheduledFor,
 * durationMinutes, status, type, technicianFirstName, services (checklist summary).
 */
public record AppVisitListItem(
        Long id,
        String name,
        Instant scheduledFor,
        int durationMinutes,
        String status,
        String type,
        String technicianFirstName,   // nullable — technician slice not yet built
        List<VisitServiceItem> services
) {}
