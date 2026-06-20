package com.homekept.identity.exception;

/**
 * Thrown when the per-email login rate limit is exceeded (5 attempts / 15 min).
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Too many login attempts. Please try again later.");
    }
}
