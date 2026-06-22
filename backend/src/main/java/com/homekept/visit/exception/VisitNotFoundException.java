package com.homekept.visit.exception;

/**
 * Thrown when a visit is not found, or when the requesting user does not own it.
 * Maps to HTTP 404 — per the ownership-failure rule: not-found and not-yours both return 404.
 */
public class VisitNotFoundException extends RuntimeException {

    public VisitNotFoundException(Long id) {
        super("Visit not found: " + id);
    }
}
