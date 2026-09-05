package com.homekept.technician;

/**
 * Thrown when a staff invite is submitted for an email address that already has a
 * {@code users} row (any role/status). Maps to HTTP 409 Conflict via
 * {@link com.homekept.common.GlobalExceptionHandler}. Pre-checked in
 * {@link TechnicianAdminService#createProfile} via
 * {@code UserQueryService#existsByEmail}; the database's unique functional index on
 * {@code lower(email)} remains the last line of defence against a concurrent-invite race
 * (which would then surface as the generic {@code DataIntegrityViolationException} 409
 * instead of this curated message).
 */
public class StaffEmailAlreadyExistsException extends RuntimeException {

    public StaffEmailAlreadyExistsException() {
        super("An account already exists for that email address.");
    }
}
