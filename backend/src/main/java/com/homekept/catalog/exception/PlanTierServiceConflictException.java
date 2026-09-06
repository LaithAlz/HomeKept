package com.homekept.catalog.exception;

/**
 * Thrown when an admin tries to add a service to a plan tier that already includes it — the
 * composite primary key on {@code plan_tier_service} (plan_tier_id, service_id) means a
 * second "add" is a duplicate, not a new row. The caller should use the update-frequency
 * endpoint instead.
 *
 * <p>Maps to HTTP 409 Conflict. The message is a pre-canned, safe string set by the service
 * (mirrors {@code RescheduleRequestConflictException}).
 */
public class PlanTierServiceConflictException extends RuntimeException {

    public PlanTierServiceConflictException(String message) {
        super(message);
    }
}
