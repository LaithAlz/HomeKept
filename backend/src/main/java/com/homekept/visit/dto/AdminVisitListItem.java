package com.homekept.visit.dto;


import java.time.Instant;

/**
 * Summary DTO returned in the admin visit list.
 * {@code GET /api/admin/visits} — cursor-paginated, id-descending, optional status filter.
 * Also backs the admin Routes day view, which fetches the same endpoint filtered to
 * {@code status=SCHEDULED} and groups the rows by technician for a chosen day.
 *
 * <p>Carries customer PII ({@code customerFirstName}, {@code customerLastName},
 * {@code customerPhone}) and the property's street address/city, in addition to ids,
 * enums, and integer cents — safe only because {@code AdminVisitController} is
 * {@code @PreAuthorize("hasRole('ADMIN')")}, same caveat as {@code AdminSubscriberListItem}.
 * Never log these fields, and never reuse this DTO on a non-admin-gated endpoint.
 *
 * <p>{@code customerFirstName}/{@code customerLastName}/{@code customerPhone} are resolved
 * from the identity domain via {@code UserQueryService.findAdminContactsByIds} (one batched
 * query per page, never per-row — same pattern {@code AdminSubscriberListItem} uses);
 * {@code customerEmail} is deliberately omitted here (not needed by the list/Routes card;
 * {@code GET /api/admin/visits/{id}} carries it for the detail view instead).
 * {@code propertyStreetAddress}/{@code propertyCity} are resolved from the property domain
 * via a batched {@code PropertyService.findByIds} — also one query per page. All four are
 * {@code null} only if the referenced subscriber/property row is unexpectedly missing
 * (both FKs are {@code RESTRICT}, so this should not happen in practice).
 *
 * <p>Still leaves out {@code completionNotes} (free-text) and the nested {@code services}
 * list (heavy) that {@link AdminVisitResponse} carries — those belong to the single-visit
 * detail view ({@code AdminVisitDetail}).
 */
// Deliberately NOT @JsonInclude(NON_NULL). This is a pre-existing DTO with a live
// consumer: the admin Routes dispatch board reads `technicianId === null` to find
// unassigned visits, group them, sort them first, and flag them. Omitting the key instead
// of sending null turns every one of those checks into `undefined`, which silently renders
// "Technician #undefined" and a zeroed unassigned counter rather than failing loudly.
// It also matches the documented convention in api-contract.md: the booking, visit and
// technician DTOs send explicit nulls, and only the subscriber DTOs omit.
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
        Instant createdAt,
        String customerFirstName,
        String customerLastName,
        String customerPhone,
        String propertyStreetAddress,
        String propertyCity
) {}
