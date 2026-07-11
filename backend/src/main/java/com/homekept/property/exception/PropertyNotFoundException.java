package com.homekept.property.exception;

/**
 * Thrown when a property is not found.
 * Maps to HTTP 404 — per the ownership-failure rule: not-found and not-yours both return 404.
 */
public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(Long id) {
        super("Property not found: " + id);
    }
}
