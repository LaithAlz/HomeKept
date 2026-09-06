package com.homekept.property.dto;

import java.time.Instant;

/**
 * Response item for {@code GET}/{@code POST /api/admin/properties/{propertyId}/notes} and
 * {@code GET}/{@code POST /api/tech/properties/{propertyId}/notes} — a property's threaded
 * notes, newest first. No role prefix: this shape is shared by the admin console and the
 * technician app, mirroring {@code com.homekept.visit.dto.VisitNoteItem}.
 *
 * <p>The author's name IS resolved here (via the identity domain's
 * {@code UserQueryService.findSummariesByIds}, batched for the list, never per-row) since a
 * note's whole point is a readable log of who observed what. Internal staff data (the author
 * is always an admin or a technician), not customer PII.
 *
 * <p>{@code body} is always plaintext. It is NEVER the encrypted {@code access_notes}
 * column — this response type never carries access notes in any form.
 *
 * @param id               the property_note row id
 * @param body             the note text
 * @param createdAt        when the note was written
 * @param authorUserId     the identity-domain user id of the author (the authenticated
 *                         principal at write time — never taken from request input)
 * @param authorFirstName  the author's first name, or {@code null} if the user record could
 *                         not be resolved
 * @param authorLastName   the author's last name, or {@code null} if the user record could
 *                         not be resolved
 */
public record PropertyNoteItem(
        Long id,
        String body,
        Instant createdAt,
        Long authorUserId,
        String authorFirstName,
        String authorLastName
) {}
