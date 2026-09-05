package com.homekept.technician;

/**
 * Thrown when a staff invite acceptance request is structurally invalid (e.g. password too
 * short). Maps to HTTP 400 via {@link com.homekept.common.GlobalExceptionHandler}, mirroring
 * {@code InvalidActivationRequestException} / {@code InvalidPasswordResetRequestException}.
 */
public class InvalidStaffInviteRequestException extends RuntimeException {

    public InvalidStaffInviteRequestException(String message) {
        super(message);
    }
}
