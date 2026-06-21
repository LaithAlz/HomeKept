package com.homekept.booking.exception;

import com.homekept.booking.BookingStatus;

/**
 * Thrown when a requested status transition is not permitted by the state machine.
 * Maps to 409 Conflict (per api-contract.md status codes).
 */
public class IllegalBookingTransitionException extends RuntimeException {

    private final BookingStatus from;
    private final BookingStatus to;

    public IllegalBookingTransitionException(BookingStatus from, BookingStatus to) {
        super("Illegal booking status transition: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public BookingStatus getFrom() { return from; }
    public BookingStatus getTo() { return to; }
}
