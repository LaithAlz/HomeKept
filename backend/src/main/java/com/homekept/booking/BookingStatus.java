package com.homekept.booking;

/**
 * Lifecycle status of a walk-through booking (lead).
 * State transitions are enforced by {@link WalkthroughBookingStateMachine}.
 * See arch doc §4.3.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    PERFORMED,
    CONVERTED,   // terminal
    LOST,        // terminal
    NO_SHOW      // terminal
}
