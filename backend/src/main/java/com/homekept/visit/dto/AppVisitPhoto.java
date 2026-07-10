package com.homekept.visit.dto;

import java.time.Instant;

/**
 * A single photo attached to a visit, as shown to the customer.
 *
 * <p>{@code url} is a signed R2 download URL with a ~15-minute TTL — generated fresh on
 * every {@code GET /api/app/visits/{id}} call via
 * {@link com.homekept.storage.StorageService#presignDownload}. It is never persisted;
 * the durable identifier is the {@code storageKey} on {@link com.homekept.visit.VisitPhoto},
 * which never crosses the controller boundary.
 */
public record AppVisitPhoto(
        String url,
        String caption,   // nullable
        Instant takenAt   // nullable
) {}
