package com.homekept.visit.exception;

/**
 * Thrown when a reschedule request does not exist, or (for a customer) is not owned by the
 * authenticated subscriber. Maps to HTTP 404 — ownership and not-found both 404 so the
 * existence of another subscriber's request is never leaked.
 */
public class RescheduleRequestNotFoundException extends RuntimeException {

    public RescheduleRequestNotFoundException(Long id) {
        super("Reschedule request not found: " + id);
    }
}
