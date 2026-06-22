package com.homekept.visit.dto;

/**
 * Response for {@code POST /api/tech/visits/{id}/photos/upload-url}.
 *
 * <p>The client uses {@code uploadUrl} to PUT the photo directly to R2 (15-minute window).
 * After the upload succeeds, the client confirms by calling
 * {@code POST /api/tech/visits/{id}/photos} with this {@code storageKey}.
 */
public record TechPhotoUploadUrlResponse(
        String uploadUrl,
        String storageKey
) {}
