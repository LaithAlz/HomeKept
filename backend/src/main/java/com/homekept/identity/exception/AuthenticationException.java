package com.homekept.identity.exception;

/**
 * Thrown when login credentials are invalid (wrong email or wrong password).
 * The caller always returns the same 401 response — never distinguish between
 * "email not found" and "wrong password" to prevent user enumeration.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException() {
        super("Invalid credentials");
    }
}
