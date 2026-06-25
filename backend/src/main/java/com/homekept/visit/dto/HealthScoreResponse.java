package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response for {@code GET /api/app/health-score}.
 *
 * <p>{@code score}: the live 0..100 Home Health Score, computed on read.
 * {@code delta}: change since the most recent snapshot (written at the last completed visit);
 * 0 if there is no prior snapshot. {@code computedAt}: when this score was computed (now).
 * {@code flagged}: the subscriber's OPEN flags.
 */
public record HealthScoreResponse(
        int score,
        int delta,
        Instant computedAt,
        List<HealthScoreFlaggedItem> flagged
) {}
