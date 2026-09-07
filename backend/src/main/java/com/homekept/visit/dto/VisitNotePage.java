package com.homekept.visit.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/admin/visits/{id}/notes} and
 * {@code GET /api/tech/visits/{id}/notes} — a cursor-paginated page of a visit's notes,
 * newest first.
 *
 * <p>Not a bare array, deliberately: a fixed-cap, unpaginated log made every note past the
 * cap permanently unreachable through the API — a technician could post enough filler notes
 * to push an admin's note past the cap with no way for anyone to page further and find it,
 * which is worse than a delete endpoint would have been (at least a delete is attributable
 * and auditable). {@code nextCursor} is the explicit "more exists" signal this fixes: pass it
 * as {@code ?cursor=} to fetch the next page, or {@code null} when this page reached the end.
 *
 * @param notes      this page's notes, newest first
 * @param nextCursor the {@code id} to pass as {@code ?cursor=} for the next page, or
 *                   {@code null} if there is no next page
 */
public record VisitNotePage(
        List<VisitNoteItem> notes,
        Long nextCursor
) {}
