package com.homekept.subscription;

/**
 * Lifecycle status for a HomeKept subscriber.
 *
 * <p>All transitions are enforced by {@link SubscriberStateMachine}. No code may write
 * a status field directly — it must call {@link SubscriberStateMachine#canTransition}
 * first and throw if the transition is illegal.
 *
 * <p>Terminal state: {@link #CANCELLED}. A returning customer is a new subscriber row.
 */
public enum SubscriberStatus {
    /**
     * Walk-through has been converted; account created; awaiting Stripe checkout completion.
     * The Stripe {@code checkout.session.completed} webhook moves the subscriber to ACTIVE.
     */
    PENDING_ACTIVATION,

    /** Subscription is paid and active. */
    ACTIVE,

    /** Customer-initiated pause (Stripe subscription paused). */
    PAUSED,

    /**
     * Invoice payment failed; Stripe is retrying. Cleared to ACTIVE on
     * {@code invoice.payment_succeeded}; moves to CANCELLED on retry exhaustion.
     */
    PAYMENT_ISSUE,

    /**
     * Terminal. Subscription has ended. A returning customer requires a new subscriber row.
     * From: PENDING_ACTIVATION (token expired / never paid), ACTIVE, PAUSED, PAYMENT_ISSUE.
     */
    CANCELLED
}
