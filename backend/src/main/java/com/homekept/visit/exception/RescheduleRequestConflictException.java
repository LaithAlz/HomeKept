package com.homekept.visit.exception;

/**
 * Thrown when a reschedule request action conflicts with current state — e.g. requesting a
 * reschedule for a visit that is not SCHEDULED, a duplicate PENDING request for the same
 * visit, or an admin confirm/decline on a request that is already resolved.
 *
 * <p>Maps to HTTP 409 Conflict. The message is a pre-canned, safe string set by the service.
 */
public class RescheduleRequestConflictException extends RuntimeException {

    public RescheduleRequestConflictException(String message) {
        super(message);
    }
}
