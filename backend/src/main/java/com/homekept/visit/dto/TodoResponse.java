package com.homekept.visit.dto;

import com.homekept.visit.TodoItem;

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
) {
    public static TodoResponse from(TodoItem t) {
        return new TodoResponse(
                t.getId(),
                t.getSubscriberId(),
                t.getBody(),
                t.getStatus().name(),
                t.getVisitId(),
                t.getDeclineNote(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
