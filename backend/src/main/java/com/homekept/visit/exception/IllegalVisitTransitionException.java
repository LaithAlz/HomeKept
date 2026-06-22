package com.homekept.visit.exception;

import com.homekept.visit.VisitStatus;

/**
 * Thrown when a visit status transition is rejected by {@link com.homekept.visit.VisitStateMachine}.
 * Maps to HTTP 409 Conflict.
 */
public class IllegalVisitTransitionException extends RuntimeException {

    private final VisitStatus from;
    private final VisitStatus to;

    public IllegalVisitTransitionException(VisitStatus from, VisitStatus to) {
        super("Visit status transition " + from + " → " + to + " is not permitted");
        this.from = from;
        this.to = to;
    }

    public VisitStatus getFrom() { return from; }
    public VisitStatus getTo() { return to; }
}
