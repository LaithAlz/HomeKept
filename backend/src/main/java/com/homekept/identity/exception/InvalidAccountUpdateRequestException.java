package com.homekept.identity.exception;

/**
 * Thrown when a {@code PATCH /api/app/account} request is structurally invalid — a
 * provided (non-null) name is blank, or a provided name/phone exceeds its column length.
 * Maps to HTTP 400 via {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class InvalidAccountUpdateRequestException extends RuntimeException {

    public InvalidAccountUpdateRequestException(String message) {
        super(message);
    }
}
