package com.homekept.technician;

/**
 * Thrown when an admin attempts to create a second {@code technician_profile} for a user
 * who already has one. Maps to HTTP 409 Conflict.
 */
public class TechnicianAlreadyExistsException extends RuntimeException {

    public TechnicianAlreadyExistsException(String message) {
        super(message);
    }
}
