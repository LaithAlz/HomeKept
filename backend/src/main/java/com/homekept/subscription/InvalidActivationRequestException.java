package com.homekept.subscription;

/**
 * Thrown when an activation request is structurally invalid (e.g. password too short).
 * Maps to HTTP 400 via {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class InvalidActivationRequestException extends RuntimeException {

    public InvalidActivationRequestException(String message) {
        super(message);
    }
}
