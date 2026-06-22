package com.homekept.visit.dto;

import java.time.Instant;

/**
 * Response DTO for a {@code TodoItem}.
 * No PII — body is subscriber-written free text (not logged, not sent to analytics).
 */
public record TodoResponse(
        Long id,
        Long subscriberId,
        String body,
        String status,
        Long visitId,
        String declineNote,
        Instant createdAt,
        Instant updatedAt
) {}
