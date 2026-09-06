package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/visits/{id}/notes} and
 * {@code POST /api/tech/visits/{id}/notes}.
 *
 * <p>{@code body} is the only client-supplied field. The note's author is ALWAYS the
 * authenticated principal (admin or technician, whichever role the endpoint is behind) —
 * there is no {@code authorUserId} field on this request, and no controller may accept one.
 */
public record CreateVisitNoteRequest(
        @NotBlank(message = "Note body is required")
        @Size(max = 2000, message = "Note must be at most 2000 characters")
        String body
) {}
