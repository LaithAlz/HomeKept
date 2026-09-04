package com.homekept.identity.dto;

/**
 * Response body for POST /api/auth/reset.
 *
 * <p>{@code signedIn} tells the caller whether fresh auth cookies were set on this
 * response (only when the user is {@link com.homekept.identity.UserStatus#ACTIVE} — see
 * {@link com.homekept.identity.AuthService#resetPassword}). When {@code false}, the
 * caller must not assume any existing session cookie is still valid: the controller has
 * cleared the auth cookies on this response, since a browser holding a stale session for
 * a different account must not be left signed in after a reset.
 */
public record ResetPasswordResponse(boolean signedIn) {}
