package com.homekept.visit.exception;

/**
 * Thrown when a technician tries to START a visit for a subscriber whose subscription is
 * not in a serviceable state — which would mean performing (free) service for someone who
 * is no longer paying. Guards START only; a visit already IN_PROGRESS is allowed to finish
 * even if billing pauses mid-visit. Maps to HTTP 409 Conflict.
 *
 * <p>The client-facing message is intentionally generic (it does not disclose the exact
 * billing status, e.g. that a card failed) — the specific status is written to the server
 * log at the throw site instead. Carries the status as a String so the visit domain does
 * not import a subscription entity/enum type (read via SubscriberQueryService, a service).
 */
public class SubscriberNotActiveException extends RuntimeException {

    private final String subscriberStatus;

    public SubscriberNotActiveException(String subscriberStatus) {
        super("This visit can't be started because the subscription isn't currently active.");
        this.subscriberStatus = subscriberStatus;
    }

    public String getSubscriberStatus() { return subscriberStatus; }
}
