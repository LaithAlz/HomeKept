package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/tech/visits/{id}/photos}.
 *
 * <p>Called after the client has successfully PUT the photo to R2 using the signed URL
 * returned by {@code POST /api/tech/visits/{id}/photos/upload-url}.
 */
public record TechConfirmPhotoRequest(
        @NotBlank String storageKey,
        String caption   // nullable
) {}
