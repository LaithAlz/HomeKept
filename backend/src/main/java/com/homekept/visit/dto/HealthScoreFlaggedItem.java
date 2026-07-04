package com.homekept.visit.dto;

import java.time.Instant;

/**
 * A flagged item surfaced on the Home Health Score (a customer-facing slim view of an OPEN
 * {@link com.homekept.visit.Flag} — no internal ids or storage keys).
 */
public record HealthScoreFlaggedItem(
        Long id,
        String body,
        String severity,
        Instant createdAt
) {}
