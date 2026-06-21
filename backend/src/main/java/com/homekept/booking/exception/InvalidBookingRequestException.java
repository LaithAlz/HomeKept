package com.homekept.booking.exception;

/**
 * Thrown when a booking request contains an invalid field value that cannot be caught
 * by Bean Validation (e.g. squareFootageRange, leadSource enum coercion).
 *
 * <p>The message is curated and safe to surface to the caller — it describes the
 * validation rule without leaking internal state. Maps to 400 Bad Request via
 * {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class InvalidBookingRequestException extends RuntimeException {

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
