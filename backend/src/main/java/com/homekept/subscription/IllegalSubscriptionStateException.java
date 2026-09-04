package com.homekept.subscription;

/**
 * Thrown when a subscription lifecycle action (pause / resume / cancel — customer self-serve
 * or admin) is requested from a status that does not permit it — e.g. pausing an
 * already-cancelled subscription, resuming one that is not paused, or a duplicate cancel
 * request for a subscriber that already has one pending.
 *
 * <p>Legality is decided by {@link SubscriberStateMachine}; the {@code (from, to)}
 * constructor carries the rejected transition for the error message. The message-only
 * constructor is for a curated, non-transition conflict (e.g. the duplicate-cancellation
 * guard) that still belongs to the same 409 {@code ILLEGAL_STATE_TRANSITION} error code.
 * Mapped to HTTP 409 Conflict in {@link com.homekept.common.GlobalExceptionHandler}, which
 * uses {@link #getMessage()} directly rather than rebuilding it from {@code from}/{@code to}.
 */
public class IllegalSubscriptionStateException extends RuntimeException {

    private final SubscriberStatus from;
    private final SubscriberStatus to;

    public IllegalSubscriptionStateException(SubscriberStatus from, SubscriberStatus to) {
        super("Subscription status transition " + from + " → " + to + " is not permitted");
        this.from = from;
        this.to = to;
    }

    /**
     * Curated-message variant for a 409 conflict that isn't a rejected {@code from → to}
     * transition (e.g. "a cancellation has already been requested"). {@link #getFrom()} and
     * {@link #getTo()} are {@code null} for this variant.
     */
    public IllegalSubscriptionStateException(String message) {
        super(message);
        this.from = null;
        this.to = null;
    }

    public SubscriberStatus getFrom() { return from; }
    public SubscriberStatus getTo() { return to; }
}
