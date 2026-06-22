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
 *     ├──→ CANCELLED    (terminal)
 *     │
 *     └──→ RESCHEDULED  (terminal — old row stays; new SCHEDULED row created)
 * </pre>
 *
 * <p><strong>Every status write in the entire codebase MUST call
 * {@link #canTransition} first.</strong> No direct status writes, ever.
 *
 * <p>Terminal statuses: COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED.
 * RESCHEDULED is a historical marker — the new visit is a fresh SCHEDULED row.
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
                              || to == VisitStatus.CANCELLED
                              || to == VisitStatus.RESCHEDULED;
            case IN_PROGRESS  -> to == VisitStatus.COMPLETED
                              || to == VisitStatus.INCOMPLETE;
            // Terminals: no outbound transitions
            case COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED -> false;
        };
    }
}
