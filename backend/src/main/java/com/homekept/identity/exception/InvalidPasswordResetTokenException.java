package com.homekept.identity.exception;

/**
 * Thrown when a password reset token is invalid, expired, or already consumed. The reason
 * ("EXPIRED", "USED", "INVALID") goes into the log message only; the API response is the
 * same generic INVALID_TOKEN for all three so callers can't tell them apart.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException(String reason) {
        super("Password reset token is " + reason.toLowerCase());
    }
}
