package com.homekept.analytics;

/**
 * The canonical product-analytics event names (arch doc §5.7). Event names are snake_case,
 * past tense, and owned by this table: <b>additions are fine, renames are migrations</b>
 * (a rename orphans historical data in PostHog).
 *
 * <p>The comment on each constant lists the properties that event is allowed to carry. All
 * are internal IDs, enums, or counts — never PII or free text (a customer's todo body, a
 * cancel reason's free-text detail, names, emails, and addresses stay in the database and
 * never reach analytics).
 *
 * <p>This class holds the backend-sourced events wired so far. Frontend-sourced events
 * ({@code booking_step_completed}, {@code report_viewed}) are captured by {@code posthog-js}
 * and are not defined here. The revenue and acquisition-funnel events
 * ({@code checkout_started}, {@code subscription_activated}, {@code subscription_cancelled},
 * {@code subscription_paused}, {@code subscription_resumed}, {@code activation_completed},
 * {@code walkthrough_booked}, {@code pick_selected}, {@code extra_purchased}) land with the
 * checkout/webhook wiring in a follow-up and will be added here then.
 */
public final class AnalyticsEvent {

    private AnalyticsEvent() {}

    /** A technician marked a visit complete. Props: {@code visit_template}, {@code duration_actual}, {@code services_count}, {@code photos_count}. */
    public static final String VISIT_COMPLETED = "visit_completed";

    /** A customer added an item to their list. Props: none (the item text is never sent). */
    public static final String TODO_ADDED = "todo_added";

    /** A technician raised a flag on a visit. Props: {@code severity}. */
    public static final String FLAG_CREATED = "flag_created";

    /** A customer requested a reschedule of a visit. Props: none. */
    public static final String RESCHEDULE_REQUESTED = "reschedule_requested";
}
