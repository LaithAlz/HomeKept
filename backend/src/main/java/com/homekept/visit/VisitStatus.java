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
    RESCHEDULED;

    /**
     * Whether this status means the technician attended, is attending, or is going to attend
     * — the single source of truth for {@link VisitRepository#existsAttendingAssignment},
     * the technician property-notes access check.
     *
     * <p>{@code SCHEDULED}, {@code IN_PROGRESS}, {@code COMPLETED}, and {@code INCOMPLETE}
     * all represent a real or upcoming attendance. {@code CANCELLED} is deliberately
     * excluded: it means the attendance did NOT happen, so treating it as proof of a
     * standing relationship would make access permanent and irrevocable. {@code RESCHEDULED}
     * is legacy (see this enum's class javadoc) and excluded for the same reason — neither
     * represents a live, real assignment.
     */
    public boolean impliesAttendance() {
        return this == SCHEDULED || this == IN_PROGRESS || this == COMPLETED || this == INCOMPLETE;
    }
}
