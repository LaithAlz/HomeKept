package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/tech/visits/{id}/photos/upload-url}.
 * The content type is validated server-side to be an image MIME type.
 */
public record TechPhotoUploadUrlRequest(
        @NotBlank String contentType
) {}
