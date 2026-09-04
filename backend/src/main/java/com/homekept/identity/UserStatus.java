package com.homekept.identity;

/**
 * User account status. Stored as VARCHAR strings in the database.
 * ACTIVE: the account has a password set and can authenticate. Service entitlement
 *         (whether the subscriber is actually paying/serviceable) is tracked separately
 *         on {@code Subscriber} — see that state machine, not this one.
 * PENDING_ACTIVATION: the account row exists but no password has been set yet. Not
 *         reachable today — {@code ActivationService.complete} creates the {@code User}
 *         row only once a password has been chosen, so it creates it ACTIVE. Reserved
 *         for a future invite-before-password flow.
 * SUSPENDED: blocked by an admin; cannot log in regardless of Subscriber status.
 */
public enum UserStatus {
    ACTIVE,
    PENDING_ACTIVATION,
    SUSPENDED
}
