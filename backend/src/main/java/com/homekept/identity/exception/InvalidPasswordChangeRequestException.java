package com.homekept.identity.exception;

/**
 * Thrown when a {@code POST /api/auth/change-password} request cannot be completed: the
 * supplied current password is wrong, the new password is too short, or the new password
 * is the same as the current one. Maps to HTTP 400 via
 * {@link com.homekept.common.GlobalExceptionHandler}. Every message here is a fixed,
 * pre-canned string — safe to return verbatim (no request data is echoed back).
 */
public class InvalidPasswordChangeRequestException extends RuntimeException {

    public InvalidPasswordChangeRequestException(String message) {
        super(message);
    }
}
