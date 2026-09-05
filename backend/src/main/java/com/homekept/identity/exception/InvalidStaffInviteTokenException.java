package com.homekept.identity.exception;

/**
 * Thrown when a staff (technician) invite token cannot be accepted: the token itself is
 * invalid, expired, or already consumed, OR it resolves to a user who is not currently an
 * eligible PENDING_ACTIVATION TECHNICIAN (e.g. already ACTIVE or SUSPENDED). All of these
 * collapse into the same reason so the API response never distinguishes a bad token from an
 * ineligible account — the same no-enumeration rationale as
 * {@link InvalidPasswordResetTokenException} and the subscription domain's
 * {@code InvalidActivationTokenException}.
 *
 * <p>The reason ("EXPIRED", "USED", "INVALID") goes into the log/exception message only; the
 * API response is the same generic INVALID_TOKEN for all of them.
 */
public class InvalidStaffInviteTokenException extends RuntimeException {

    public InvalidStaffInviteTokenException(String reason) {
        super("Staff invite token is " + reason.toLowerCase());
    }
}
