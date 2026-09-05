package com.homekept.visit.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Response item for {@code GET /api/admin/visits/{id}/events} — the visit's activity log,
 * newest first, capped at 100 rows. Mirrors
 * {@code GET /api/admin/subscribers/{id}/events}'s shape ({@code id, type, source,
 * occurredAt}), plus two fields {@code SubscriptionEventItem} has no equivalent for:
 * {@code byUserId} (a real column on {@code visit_event}, unlike {@code subscription_event})
 * and a generic {@code payload} passthrough rather than per-event-type extracted fields —
 * a visit accumulates several distinct event types from day one (RESCHEDULED, technician
 * assignment, CANCELLED, ...), so a fixed set of extracted fields would not generalize the
 * way it does for subscription's single {@code CANCELLATION_REQUESTED} case.
 *
 * @param id         the visit_event row id
 * @param type       the event type string (e.g. {@code RESCHEDULED}, {@code
 *                   TECHNICIAN_ASSIGNED}, {@code CANCELLED} — unconstrained, so this is not
 *                   an exhaustive list)
 * @param source     {@link com.homekept.visit.VisitEventSource} name (
 *                   {@code ADMIN} / {@code CUSTOMER} / {@code TECHNICIAN} / {@code SYSTEM})
 * @param occurredAt when the event was recorded ({@code created_at})
 * @param byUserId   the acting user's id; {@code null} for {@code SYSTEM} events. A bare
 *                   cross-domain id, not resolved to a name — the activity log doesn't need
 *                   the acting user's identity resolved the way the visit list/detail need
 *                   the customer's
 * @param payload    the event's raw JSONB detail (e.g. {@code {"from": ..., "to": ...}} for
 *                   {@code RESCHEDULED}), embedded as-is; {@code null} for an event with no
 *                   extra detail (e.g. {@code CANCELLED})
 */
public record VisitEventItem(
        Long id,
        String type,
        String source,
        Instant occurredAt,
        Long byUserId,
        JsonNode payload
) {}
