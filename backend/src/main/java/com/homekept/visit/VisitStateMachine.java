package com.homekept.visit;

import org.springframework.stereotype.Component;

/**
 * State machine for the {@link Visit} lifecycle (arch doc §4.2).
 *
 * <pre>
 * SCHEDULED ──→ IN_PROGRESS ──→ COMPLETED   (terminal)
 *     │               │
 *     │               └────→ INCOMPLETE      (terminal — flag for follow-up)
 *     │
 *     └──→ CANCELLED    (terminal)
 * </pre>
 *
 * <p><strong>Every status write in the entire codebase MUST call
 * {@link #canTransition} first.</strong> No direct status writes, ever.
 *
 * <p>Terminal statuses: COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED.
 *
 * <h2>RESCHEDULED — legacy status, never written by current code</h2>
 * <p>A visit used to be "rescheduled" by marking the old row RESCHEDULED (terminal) and
 * creating a brand-new SCHEDULED row. That put every reschedule into the admin visit list
 * as an extra row and broke the "Visit #N" identity an operator uses to refer to a visit
 * (founder's explicit ask to fix this — see {@link VisitAdminService#rescheduleInternal}).
 * A visit is now rescheduled IN PLACE: its {@code scheduledFor} is updated and the status
 * never changes, so {@code RESCHEDULED} is never written by any code path today.
 *
 * <p>{@code RESCHEDULED} stays in {@link VisitStatus} and {@code SCHEDULED → RESCHEDULED}
 * stays illegal below (rather than legal-but-unused) precisely BECAUSE it can no longer
 * happen — leaving it legal would misrepresent what the state machine actually permits and
 * could mislead a future engineer into resurrecting the old replacement-visit pattern. The
 * enum value itself is kept (removing it needs a migration touching the V6 CHECK
 * constraint, which is the founder's hand-write boundary) so any historical rows already
 * persisted with this status continue to deserialize and remain correctly terminal here.
 *
 * <p>{@link #canReschedule} — not {@code canTransition(from, RESCHEDULED)} — is the single
 * source of truth for "may this visit be rescheduled at all", since a reschedule no longer
 * transitions status.
 */
@Component
public class VisitStateMachine {

    /**
     * Returns {@code true} if the transition from {@code from} to {@code to} is legal
     * per the state machine diagram above.
     *
     * @param from current status (must not be null)
     * @param to   desired next status (must not be null)
     * @return {@code true} if the transition is permitted
     */
    public boolean canTransition(VisitStatus from, VisitStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case SCHEDULED    -> to == VisitStatus.IN_PROGRESS
                              || to == VisitStatus.CANCELLED;
            case IN_PROGRESS  -> to == VisitStatus.COMPLETED
                              || to == VisitStatus.INCOMPLETE;
            // Terminals: no outbound transitions
            case COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED -> false;
        };
    }

    /**
     * Whether a visit currently in {@code status} may be rescheduled in place (see
     * {@link VisitAdminService#rescheduleInternal}). Only a {@code SCHEDULED} visit is
     * reschedulable — an already-started, already-finished, or cancelled visit is not.
     *
     * <p>This is deliberately a separate predicate from {@link #canTransition}: rescheduling
     * no longer changes the visit's status (it stays {@code SCHEDULED}), so there is no
     * {@code (from, to)} pair to check here — just whether the current status permits it.
     *
     * @param status the visit's current status (must not be null)
     * @return {@code true} if a visit in this status may be rescheduled
     */
    public boolean canReschedule(VisitStatus status) {
        return status == VisitStatus.SCHEDULED;
    }
}
