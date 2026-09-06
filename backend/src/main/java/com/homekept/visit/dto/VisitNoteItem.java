package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Response item for {@code GET}/{@code POST /api/admin/visits/{id}/notes} and
 * {@code GET}/{@code POST /api/tech/visits/{id}/notes} — a visit's threaded operational
 * log, newest first. No role prefix: this shape is shared by the admin console and the
 * technician app (mirrors how {@code VisitServiceItem} is shared).
 *
 * <p>Unlike {@code VisitEventItem} (which deliberately leaves {@code byUserId} unresolved —
 * see that class's javadoc), a visit note's whole point is a readable log of who said what,
 * so the author's name IS resolved here, via the identity domain's
 * {@code UserQueryService.findSummariesByIds} (batched for the list, never per-row) — never
 * by reaching into the identity domain's repository or entity directly. This is internal
 * staff data (the author is always an admin or a technician), not customer PII.
 *
 * @param id               the visit_note row id
 * @param body             the note text
 * @param createdAt        when the note was written
 * @param authorUserId     the identity-domain user id of the author (the authenticated
 *                         principal at write time — never taken from request input)
 * @param authorFirstName  the author's first name, or {@code null} if the user record could
 *                         not be resolved
 * @param authorLastName   the author's last name, or {@code null} if the user record could
 *                         not be resolved
 */
public record VisitNoteItem(
        Long id,
        String body,
        Instant createdAt,
        Long authorUserId,
        String authorFirstName,
        String authorLastName
) {}
