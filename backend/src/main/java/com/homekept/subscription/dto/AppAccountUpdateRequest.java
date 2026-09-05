package com.homekept.subscription.dto;

/**
 * Request body for {@code PATCH /api/app/account}.
 *
 * <p>Every field is optional: {@code null} (or an omitted key) leaves the corresponding
 * value unchanged, so a caller can update just one field at a time. Validation (non-blank
 * names, length caps matching the {@code User} columns) happens in
 * {@link com.homekept.identity.UserProfileService#updateProfile}, which owns the
 * {@code User} entity's invariants.
 *
 * <p>Email is deliberately not editable here — changing it is an account-takeover path
 * that needs dual verification, a separate later piece of work — and neither is the
 * service property address, which drives routing and the property record (its own
 * admin-only update path).
 */
public record AppAccountUpdateRequest(
        String firstName,
        String lastName,
        String phone
) {}
