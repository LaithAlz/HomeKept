package com.homekept.visit;

/**
 * Who (or what) caused a {@link VisitEvent}. Matches the V14 migration's CHECK constraint
 * on {@code visit_event.source} exactly — unlike {@code event_type}, the set of actors is
 * closed, so this IS an enum (mirrors {@link com.homekept.subscription.SubscriptionEventSource}).
 */
public enum VisitEventSource {
    ADMIN,
    CUSTOMER,
    TECHNICIAN,
    SYSTEM
}
