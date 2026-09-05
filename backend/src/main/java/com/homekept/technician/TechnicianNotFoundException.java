package com.homekept.technician;

/**
 * Thrown when a {@code technician_profile} id does not resolve to an existing row (e.g. the
 * resend-invite target). Maps to HTTP 404 via {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class TechnicianNotFoundException extends RuntimeException {

    public TechnicianNotFoundException(String message) {
        super(message);
    }
}
