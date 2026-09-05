package com.homekept.subscription.dto;

import java.time.Instant;

/**
 * Response for the subscription lifecycle endpoints
 * ({@code POST /api/app/subscription/cancel}, {@code POST /api/admin/subscribers/{id}/cancel}).
 *
 * <p>{@code status} is the subscriber's status <em>at the time of the request</em>. The
 * actual transition (→ CANCELLED) is driven by the corresponding Stripe webhook, so a
 * freshly-cancelled subscriber still reads its pre-cancellation status here until the
 * webhook lands; the client should treat the action as accepted, not yet applied.
 *
 * <p>{@code currentPeriodEnd} tells the customer when paid access runs through — most
 * useful for cancel (cancel-at-period-end). May be null before checkout has synced dates.
 */
public record SubscriptionActionResponse(
        String status,
        Instant currentPeriodEnd
) {}
