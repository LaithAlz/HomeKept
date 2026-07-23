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
 * and are not defined here. The picks events ({@code pick_selected}, {@code extra_purchased})
 * land with the picks/à-la-carte checkout slice.
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

    /**
     * A walk-through was booked (the top of the acquisition funnel). Captured against the
     * wizard's anonymous distinct id (no user exists yet), then folded into the user at
     * activation via {@link AnalyticsService#alias}. Props: {@code lead_source},
     * {@code city} (a bounded form value, not free text), {@code property_type}.
     */
    public static final String WALKTHROUGH_BOOKED = "walkthrough_booked";

    /** A prospect completed activation and became a subscriber. Props: {@code days_since_walkthrough}. */
    public static final String ACTIVATION_COMPLETED = "activation_completed";

    /** A customer started a Stripe checkout. Props: {@code plan_code}, {@code billing_cycle}, {@code founding_rate}. */
    public static final String CHECKOUT_STARTED = "checkout_started";

    /** A subscription went live (checkout.session.completed webhook). Props: {@code plan_code}, {@code billing_cycle}, {@code founding_rate}. */
    public static final String SUBSCRIPTION_ACTIVATED = "subscription_activated";

    /**
     * A subscription was cancelled (customer.subscription.deleted webhook). Props:
     * {@code plan_code}, {@code months_subscribed}. The {@code reason} enum from the §5.7
     * table is deferred until a cancel-reason is persisted (the cancel form's free-text
     * detail stays in the DB and never reaches analytics regardless).
     */
    public static final String SUBSCRIPTION_CANCELLED = "subscription_cancelled";

    /** A subscription was paused (customer.subscription.paused webhook). Props: none. */
    public static final String SUBSCRIPTION_PAUSED = "subscription_paused";

    /** A subscription was resumed (customer.subscription.resumed webhook). Props: none. */
    public static final String SUBSCRIPTION_RESUMED = "subscription_resumed";
}
