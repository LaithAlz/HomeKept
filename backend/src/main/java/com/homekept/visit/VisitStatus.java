package com.homekept.visit;

/**
 * Status values for a {@link Visit}.
 *
 * <p>All status writes MUST go through {@link VisitStateMachine#canTransition}.
 * Terminal statuses: COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED.
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
