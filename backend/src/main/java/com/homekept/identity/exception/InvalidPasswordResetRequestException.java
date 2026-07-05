package com.homekept.identity.exception;

/**
 * Thrown when a password reset request is structurally invalid (e.g. password too short).
 * Maps to HTTP 400 via {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class InvalidPasswordResetRequestException extends RuntimeException {

    public InvalidPasswordResetRequestException(String message) {
        super(message);
    }
}
