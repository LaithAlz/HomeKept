package com.homekept.booking;

import org.springframework.stereotype.Component;

/**
 * State machine for the walk-through booking lifecycle (arch doc §4.3).
 *
 * <pre>
 * PENDING  ──→ CONFIRMED ──→ PERFORMED ──→ CONVERTED  (terminal)
 *     │            │              │
 *     │            │              └────→ LOST  (terminal)
 *     │            │
 *     │            └────→ NO_SHOW  (terminal)
 *     │
 *     └────→ LOST  (terminal)
 * </pre>
 *
 * <p><strong>Every status write in the system MUST call {@link #canTransition} first.</strong>
 * Callers that skip this check violate the architecture contract.
 */
@Component
public class WalkthroughBookingStateMachine {

    /**
     * Returns {@code true} if the transition from {@code from} to {@code to} is legal
     * per the state machine diagram above.
     *
     * @param from current status
     * @param to   desired next status
     * @return {@code true} if the transition is permitted
     */
    public boolean canTransition(BookingStatus from, BookingStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PENDING   -> to == BookingStatus.CONFIRMED || to == BookingStatus.LOST;
            case CONFIRMED -> to == BookingStatus.PERFORMED || to == BookingStatus.NO_SHOW;
            case PERFORMED -> to == BookingStatus.CONVERTED || to == BookingStatus.LOST;
            // Terminal states: no outbound transitions
            case CONVERTED, LOST, NO_SHOW -> false;
        };
    }
}
