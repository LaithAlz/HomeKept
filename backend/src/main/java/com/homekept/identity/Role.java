package com.homekept.identity;

/**
 * User roles. Stored as VARCHAR strings in the database.
 * A user has exactly one role at a given time (see arch doc §2.1).
 */
public enum Role {
    CUSTOMER,
    TECHNICIAN,
    ADMIN
}
