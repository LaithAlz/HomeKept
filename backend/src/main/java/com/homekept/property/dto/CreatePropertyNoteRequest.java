package com.homekept.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/properties/{propertyId}/notes} and
 * {@code POST /api/tech/properties/{propertyId}/notes}.
 *
 * <p>{@code body} is the only client-supplied field. The note's author is ALWAYS the
 * authenticated principal (admin or technician, whichever role the endpoint is behind) —
 * there is no {@code authorUserId} field on this request, and no controller may accept one.
 *
 * <p>{@code body} must be plaintext operational notes only. Never accept a lockbox code,
 * alarm code, or key location here — those belong exclusively in the encrypted
 * {@code access_notes} column, which has no write endpoint at all (it is captured
 * elsewhere and this request type must never be repurposed for it).
 */
public record CreatePropertyNoteRequest(
        @NotBlank(message = "Note body is required")
        @Size(max = 2000, message = "Note must be at most 2000 characters")
        String body
) {}
