package com.homekept.catalog.exception;

/**
 * Thrown when a plan tier id does not exist.
 * Maps to HTTP 404 — per the ownership-failure rule: not-found and not-yours both return 404.
 */
public class PlanTierNotFoundException extends RuntimeException {

    public PlanTierNotFoundException(Long id) {
        super("Plan tier not found: " + id);
    }
}
