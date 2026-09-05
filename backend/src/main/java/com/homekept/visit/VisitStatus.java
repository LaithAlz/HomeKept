package com.homekept.visit;

/**
 * Status values for a {@link Visit}.
 *
 * <p>All status writes MUST go through {@link VisitStateMachine#canTransition}.
 * Terminal statuses: COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED.
 *
 * <p>{@code RESCHEDULED} is legacy: a visit is now rescheduled in place (its
 * {@code scheduledFor} changes; its status does not), so no current code path ever writes
 * this value. It remains here only so any historical row already persisted with this
 * status continues to deserialize — see {@link VisitStateMachine}'s javadoc for why.
 *
 * <p>See arch doc §4.2.
 */
public enum VisitStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    INCOMPLETE,
    CANCELLED,
    RESCHEDULED
}
