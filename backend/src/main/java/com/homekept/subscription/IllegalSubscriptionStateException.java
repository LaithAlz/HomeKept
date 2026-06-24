package com.homekept.subscription;

/**
 * Thrown when a self-serve subscription lifecycle action (pause / resume / cancel) is
 * requested from a status that does not permit it — e.g. pausing an already-cancelled
 * subscription, or resuming one that is not paused.
 *
 * <p>Legality is decided by {@link SubscriberStateMachine}; this exception carries the
 * rejected transition for the error message. Mapped to HTTP 409 Conflict in
 * {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class IllegalSubscriptionStateException extends RuntimeException {

    private final SubscriberStatus from;
    private final SubscriberStatus to;

    public IllegalSubscriptionStateException(SubscriberStatus from, SubscriberStatus to) {
        super("Subscription status transition " + from + " → " + to + " is not permitted");
        this.from = from;
        this.to = to;
    }

    public SubscriberStatus getFrom() { return from; }
    public SubscriberStatus getTo() { return to; }
}
