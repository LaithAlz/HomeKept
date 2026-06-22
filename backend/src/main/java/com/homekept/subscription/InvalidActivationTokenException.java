package com.homekept.subscription;

/**
 * Thrown when an activation token is invalid, expired, or already consumed.
 * The {@code reason} field matches the API contract values: "EXPIRED", "USED", "INVALID".
 */
public class InvalidActivationTokenException extends RuntimeException {

    private final String reason;

    public InvalidActivationTokenException(String reason) {
        super("Activation token is " + reason.toLowerCase());
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
