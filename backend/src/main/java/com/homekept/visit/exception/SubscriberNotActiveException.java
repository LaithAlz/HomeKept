package com.homekept.visit.exception;

/**
 * Thrown when a self-serve or service action is attempted for a subscriber whose subscription
 * is not in a serviceable state ({@code SubscriberStatus.isServiceable()} = ACTIVE or
 * PAYMENT_ISSUE). Used by three guards: starting a visit, creating a reschedule request, and
 * adding a to-do item — all of which would otherwise let a paused/cancelled (non-paying)
 * customer keep pulling on the service. Maps to HTTP 409 Conflict.
 *
 * <p>The client-facing message is intentionally generic (it does not disclose the exact
 * billing status, e.g. that a card failed) — the specific status is written to the server
 * log at the throw site instead. Carries the status as a String so the visit domain does
 * not import a subscription entity/enum type (read via SubscriberQueryService, a service).
 */
public class SubscriberNotActiveException extends RuntimeException {

    private final String subscriberStatus;

    public SubscriberNotActiveException(String subscriberStatus) {
        super("This isn't available right now because the subscription isn't currently active.");
        this.subscriberStatus = subscriberStatus;
    }

    public String getSubscriberStatus() { return subscriberStatus; }
}
