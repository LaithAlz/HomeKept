package com.homekept.booking.exception;

/**
 * Thrown when a booking is not found (or is not accessible to the caller).
 * Maps to 404 — per CLAUDE.md, ownership failures return 404, not 403.
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Booking not found: " + id);
    }
}
