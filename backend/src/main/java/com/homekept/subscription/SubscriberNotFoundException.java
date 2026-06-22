package com.homekept.subscription;

/**
 * Thrown when a subscriber row cannot be found for the given user or id.
 *
 * <p>Mapped to HTTP 404 in {@link com.homekept.common.GlobalExceptionHandler}.
 * Per CLAUDE.md ownership-failure rule: not-found and not-yours both return 404.
 */
public class SubscriberNotFoundException extends RuntimeException {

    public SubscriberNotFoundException(String message) {
        super(message);
    }
}
