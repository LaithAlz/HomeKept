package com.homekept.identity;

/**
 * What a {@code password_reset_tokens} row is for (V13 migration). The customer
 * forgot/reset-password flow and the technician staff-invite flow share one table — it is
 * already user-bound, HMAC-signed, stored hash-only, and single-use at the database level —
 * but with different lifetimes (30 minutes vs. 7 days) and very different consequences if
 * confused for one another.
 *
 * <p>Purpose is the hard boundary between the two: every lookup filters by it
 * ({@link PasswordResetTokenService}), and a token of the wrong purpose is indistinguishable
 * in the API response from one that simply does not exist — never "wrong purpose", just
 * "INVALID". Values must exactly match the V13 migration's CHECK constraint.
 */
public enum TokenPurpose {
    PASSWORD_RESET,
    STAFF_INVITE
}
