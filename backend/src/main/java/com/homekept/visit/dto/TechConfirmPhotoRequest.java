package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/tech/visits/{id}/photos}.
 *
 * <p>Called after the client has successfully PUT the photo to R2 using the signed URL
 * returned by {@code POST /api/tech/visits/{id}/photos/upload-url}.
 */
public record TechConfirmPhotoRequest(
        @NotBlank @Size(max = 1024) String storageKey,
        @Size(max = 1000) String caption   // nullable
) {}
