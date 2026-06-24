package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Response DTO for a {@code Flag}.
 */
public record FlagResponse(
        Long id,
        Long subscriberId,
        Long originVisitId,
        String body,
        String severity,
        String status,
        String photoStorageKey,
        Instant createdAt
) {}
