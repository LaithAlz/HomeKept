package com.homekept.identity.exception;

/**
 * Thrown when a refresh token is invalid, expired, already revoked, or not found.
 */
public class TokenException extends RuntimeException {

    public enum Reason { NOT_FOUND, EXPIRED, REVOKED }

    private final Reason reason;

    public TokenException(Reason reason) {
        super("Refresh token " + reason.name().toLowerCase());
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
