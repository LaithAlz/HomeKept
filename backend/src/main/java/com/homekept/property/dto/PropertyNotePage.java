package com.homekept.property.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/admin/properties/{propertyId}/notes} and
 * {@code GET /api/tech/properties/{propertyId}/notes} — a cursor-paginated page of a
 * property's notes, newest first. Mirrors {@code com.homekept.visit.dto.VisitNotePage} —
 * same shape, same reason it exists (see that class's javadoc: an unpaginated, fixed-cap log
 * makes everything past the cap permanently unreachable).
 *
 * @param notes      this page's notes, newest first
 * @param nextCursor the {@code id} to pass as {@code ?cursor=} for the next page, or
 *                   {@code null} if there is no next page
 */
public record PropertyNotePage(
        List<PropertyNoteItem> notes,
        Long nextCursor
) {}
