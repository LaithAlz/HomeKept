package com.homekept.identity.dto;

/**
 * Response body for POST /api/auth/reset.
 *
 * <p>{@code signedIn} is always {@code true}: {@link com.homekept.identity.AuthService#resetPassword}
 * now rejects a reset outright (400 {@code INVALID_TOKEN}, no cookies touched) for any
 * account that is not {@link com.homekept.identity.UserStatus#ACTIVE}, rather than
 * succeeding without auto-sign-in — a reset that changes a non-ACTIVE account's password
 * behind the scenes was half of an account-takeover path (the other half being a staff
 * invite token wrongly redeemable here; see {@code TokenPurpose}). The field is kept
 * (rather than dropped) only to avoid a frontend contract change.
 */
public record ResetPasswordResponse(boolean signedIn) {}
