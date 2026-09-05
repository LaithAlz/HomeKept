package com.homekept.visit.exception;

import com.homekept.visit.VisitStatus;

/**
 * Thrown when a reschedule is attempted on a visit whose current status does not permit it
 * ({@code VisitStateMachine#canReschedule}).
 *
 * <p>Deliberately distinct from {@link IllegalVisitTransitionException}: a reschedule no
 * longer transitions the visit's status (see {@code VisitStateMachine}'s class javadoc —
 * a rescheduled visit stays {@code SCHEDULED}), so reporting a refused reschedule as an
 * attempted transition to {@code RESCHEDULED} — a status no code path can ever produce —
 * would tell the operator they tried something that was never actually on offer. This
 * exception's message names the visit's actual status instead of a nonexistent target.
 *
 * <p>Maps to HTTP 409 Conflict with the same error code as
 * {@link IllegalVisitTransitionException} ({@code ILLEGAL_STATE_TRANSITION}) — the outcome
 * for the API consumer is the same "this isn't allowed right now" 409, just with an honest
 * message.
 */
public class VisitNotReschedulableException extends RuntimeException {

    private final VisitStatus status;

    public VisitNotReschedulableException(VisitStatus status) {
        super("Visit is " + status + " and cannot be rescheduled");
        this.status = status;
    }

    public VisitStatus getStatus() { return status; }
}
