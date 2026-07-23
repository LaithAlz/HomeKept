/**
 * Product analytics (PostHog) for HomeKept — arch doc §5.7.
 *
 * <p>Business-truth events are captured server-side where the state actually changes
 * (state-machine transitions, service methods), never trusting the client for anything
 * that matters. The domain is encapsulated the same way {@code notification} is: callers
 * depend only on {@link com.homekept.analytics.AnalyticsService} and
 * {@link com.homekept.analytics.AnalyticsEvent} and never learn that PostHog exists.
 *
 * <h2>Guarantees callers rely on</h2>
 * <ul>
 *   <li><b>Best-effort:</b> a capture never throws into, blocks, or rolls back the
 *       business transaction that triggered it. Failures are swallowed and logged.</li>
 *   <li><b>Commit-gated:</b> when a transaction is active, the event is emitted only
 *       after it commits — a rolled-back change produces no phantom event.</li>
 *   <li><b>Degrades to a no-op:</b> with no {@code POSTHOG_API_KEY} configured (dev, test,
 *       CI), capture does nothing and makes no network call.</li>
 *   <li><b>No PII:</b> event properties carry internal IDs, enums, and counts only — never
 *       names, emails, addresses, or free text. This is a call-site discipline; see the
 *       {@link com.homekept.analytics.AnalyticsEvent} table for each event's allowed props.</li>
 * </ul>
 */
package com.homekept.analytics;
