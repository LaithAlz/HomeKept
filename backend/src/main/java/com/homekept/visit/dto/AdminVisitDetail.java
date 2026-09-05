package com.homekept.visit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Full detail response for {@code GET /api/admin/visits/{id}} — everything an operator
 * needs when they open one visit: the visit itself, who the customer is, where the
 * property is, the assigned technician's name, the checklist, and any notes/photos
 * captured so far.
 *
 * <p>Carries customer PII ({@code customerFirstName}, {@code customerLastName},
 * {@code customerEmail}, {@code customerPhone}) in addition to ids, enums, integer cents,
 * and timestamps — safe only because {@code AdminVisitController} is
 * {@code @PreAuthorize("hasRole('ADMIN')")}, same caveat as {@code AdminSubscriberDetail}.
 * Never log these fields; never reuse this DTO on a non-admin-gated endpoint.
 *
 * <p>{@code customer*} fields are resolved from the identity domain via
 * {@code UserQueryService.findAdminContactById} (the subscription domain's subscriber
 * record supplies the {@code userId}); {@code technicianFirstName}/{@code
 * technicianLastName} from {@code UserQueryService.findSummariesByIds} (the same
 * staff-identity lookup the technician roster uses) — {@code null} until a technician is
 * assigned. {@code property} is resolved from the property domain's {@code
 * PropertyService} — access notes are never decrypted here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminVisitDetail(
        Long id,
        Long subscriberId,
        Long technicianId,           // nullable — unassigned
        String technicianFirstName,  // nullable — unassigned
        String technicianLastName,   // nullable — unassigned
        Long visitTemplateId,        // nullable
        String name,                 // resolved display name — see Visit#resolveDisplayName
        Instant scheduledFor,
        int durationMinutes,
        Integer actualDurationMinutes,   // nullable — filled at completion
        Integer materialsCostCents,      // nullable — filled at completion; integer cents
        String status,
        String type,
        String completionNotes,      // nullable
        String materialsNotes,       // nullable
        Instant completedAt,         // nullable
        Instant createdAt,
        List<VisitServiceItem> services,
        List<AppVisitPhoto> photos,  // empty if R2 unconfigured or no photos — never fabricated
        AdminVisitPropertySummary property,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        String customerPhone
) {}
