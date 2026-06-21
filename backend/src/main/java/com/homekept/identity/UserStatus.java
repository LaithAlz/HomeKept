package com.homekept.identity;

/**
 * User account status. Stored as VARCHAR strings in the database.
 * PENDING_ACTIVATION: walk-through booked, password/payment not yet set.
 * ACTIVE: paying, full access.
 * SUSPENDED: manually suspended by admin.
 */
public enum UserStatus {
    ACTIVE,
    PENDING_ACTIVATION,
    SUSPENDED
}
