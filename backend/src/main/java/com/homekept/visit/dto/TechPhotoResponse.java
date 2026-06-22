package com.homekept.visit.dto;

import java.time.Instant;

/**
 * A confirmed photo attachment on a visit.
 * Returned after {@code POST /api/tech/visits/{id}/photos} succeeds.
 */
public record TechPhotoResponse(
        Long id,
        Long visitId,
        String storageKey,
        String caption,
        Instant takenAt,
        Instant createdAt
) {}
