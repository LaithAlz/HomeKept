package com.homekept.identity.exception;

/**
 * Thrown when a password reset token is invalid, expired, or already consumed.
 * The {@code reason} field matches the API contract values: "EXPIRED", "USED", "INVALID".
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

    private final String reason;

    public InvalidPasswordResetTokenException(String reason) {
        super("Password reset token is " + reason.toLowerCase());
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
