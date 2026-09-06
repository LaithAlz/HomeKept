package com.homekept.catalog.exception;

/**
 * Thrown when a catalog service id does not exist.
 * Maps to HTTP 404 — per the ownership-failure rule: not-found and not-yours both return 404.
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(Long id) {
        super("Service not found: " + id);
    }
}
