package com.homekept.technician;

/**
 * Thrown when {@code POST /api/admin/technicians/{id}/invite} targets a technician profile
 * whose linked user is not currently an eligible PENDING_ACTIVATION TECHNICIAN (already
 * ACTIVE, SUSPENDED, or — should not happen in normal operation — missing or wrong role).
 * Maps to HTTP 409 Conflict via {@link com.homekept.common.GlobalExceptionHandler}.
 *
 * <p>Thrown BEFORE any token is invalidated or minted (see
 * {@link TechnicianAdminService#resendInvite}) — this is the fix for a real account-takeover
 * path: resending an invite to an already-ACTIVE technician (e.g. an admin clicking "Resend"
 * on a roster row rendered from a stale cached status) would otherwise mail that account a
 * fresh, long-lived, password-setting link.
 */
public class TechnicianNotEligibleForInviteException extends RuntimeException {

    public TechnicianNotEligibleForInviteException(String message) {
        super(message);
    }
}
