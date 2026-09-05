package com.homekept.visit;

/**
 * Known {@code visit_event.event_type} string literals shared by this domain's producers
 * ({@link VisitAdminService}, {@link RescheduleService}).
 *
 * <p>Per the V14 migration comment, {@code event_type} is deliberately NOT a DB-level
 * enum/CHECK — a new kind of event should never require a migration. This class is just a
 * convenience so the two current producers don't duplicate the same string literals; it is
 * NOT an exhaustive list and callers must not treat it as one (e.g. {@link
 * com.homekept.subscription.SubscriptionAdminService}'s admin-cancel payload similarly
 * defines its own event type locally rather than via a shared enum).
 */
final class VisitEventType {

    /** Visit rescheduled in place. Payload: {@code {"from": "...", "to": "..."}} (Instants). */
    static final String RESCHEDULED = "RESCHEDULED";

    /** Technician assigned or changed. Payload: {@code {"from": <id|null>, "to": <id>}}. */
    static final String TECHNICIAN_ASSIGNED = "TECHNICIAN_ASSIGNED";

    /** Visit cancelled. No payload — nothing else is captured about a cancel today. */
    static final String CANCELLED = "CANCELLED";

    private VisitEventType() {}
}
