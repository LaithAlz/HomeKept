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

    /**
     * Records an event attributed to an <em>anonymous</em> distinct id — used for pre-signup
     * lead events (e.g. {@code walkthrough_booked}) where no internal user exists yet. The
     * distinct id is typically the booking wizard's PostHog id; {@link #alias} later folds it
     * into the user's identity so the acquisition funnel stitches across the signup boundary.
     *
     * @param distinctId the anonymous distinct id; a {@code null}/blank id is skipped
     * @param event      an event name from {@link AnalyticsEvent}
     * @param properties event properties (same no-PII rule as {@link #capture})
     */
    void captureAnonymous(String distinctId, String event, Map<String, Object> properties);

    /**
     * Merges an anonymous distinct id into an internal user's identity so events captured
     * before signup (against the anonymous id) and after (against the user id) resolve to one
     * person in PostHog. A no-op if the user id is {@code null} or the anonymous id is blank.
     *
     * @param anonymousDistinctId the pre-signup anonymous distinct id
     * @param userId              the internal user id to merge it into
     */
    void alias(String anonymousDistinctId, Long userId);
}
