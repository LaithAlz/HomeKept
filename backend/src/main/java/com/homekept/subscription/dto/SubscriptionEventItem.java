package com.homekept.subscription.dto;

import java.time.Instant;

/**
 * Response item for {@code GET /api/admin/subscribers/{id}/events} — the subscriber's
 * activity history, newest first.
 *
 * @param id         the subscription_event row id
 * @param type       the event type string (e.g. {@code CANCELLATION_REQUESTED},
 *                   a Stripe event type)
 * @param source     {@link com.homekept.subscription.SubscriptionEventSource} name
 *                   ({@code STRIPE_WEBHOOK} / {@code MANUAL} / {@code SYSTEM})
 * @param occurredAt when the event was recorded ({@code created_at})
 * @param note       the churn reason for {@code CANCELLATION_REQUESTED} events, extracted
 *                   from the JSONB payload; {@code null} for every other event type
 * @param by         who requested a {@code CANCELLATION_REQUESTED} event —
 *                   {@code "CUSTOMER"} or {@code "ADMIN"}, extracted from the JSONB payload;
 *                   {@code null} for every other event type
 * @param immediate  for an admin {@code CANCELLATION_REQUESTED} event, whether it was an
 *                   immediate cancel ({@code true}) or at period end ({@code false});
 *                   {@code null} for customer self-serve cancellations (always at period end)
 *                   and every other event type
 */
public record SubscriptionEventItem(
        Long id,
        String type,
        String source,
        Instant occurredAt,
        String note,
        String by,
        Boolean immediate
) {}
