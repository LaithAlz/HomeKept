package com.homekept.analytics;

import java.util.Map;

/**
 * The seam every domain uses to record a product-analytics event. Implementations must
 * honour the guarantees documented on the package: best-effort (never throws/blocks/rolls
 * back the caller), commit-gated (fires after the active transaction commits), degrades to
 * a no-op without a key, and carries no PII.
 *
 * <p>Callers never construct PostHog payloads or know the transport — they name an event
 * from {@link AnalyticsEvent} and pass internal IDs, enums, and counts.
 */
public interface AnalyticsService {

    /**
     * Records an event attributed to an internal user id.
     *
     * @param distinctUserId the internal user id the event belongs to (used as PostHog's
     *                       {@code distinct_id}); a {@code null} id is skipped, never sent
     *                       as the string {@code "null"}
     * @param event          an event name from {@link AnalyticsEvent} (snake_case, past tense)
     * @param properties     event properties — <strong>internal IDs, enums, and counts only,
     *                       never PII or free text</strong>; may be empty
     */
    void capture(Long distinctUserId, String event, Map<String, Object> properties);
}
