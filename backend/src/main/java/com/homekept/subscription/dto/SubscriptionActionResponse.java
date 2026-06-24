package com.homekept.subscription.dto;

import java.time.Instant;

/**
 * Response for the self-serve lifecycle endpoints
 * ({@code POST /api/app/subscription/pause|resume|cancel}).
 *
 * <p>{@code status} is the subscriber's status <em>at the time of the request</em>. The
 * actual transition (→ PAUSED / ACTIVE / CANCELLED) is driven by the corresponding Stripe
 * webhook, so a freshly-paused subscriber still reads ACTIVE here until the webhook lands;
 * the client should treat the action as accepted, not yet applied.
 *
 * <p>{@code currentPeriodEnd} tells the customer when paid access runs through — most
 * useful for cancel (cancel-at-period-end). May be null before checkout has synced dates.
 */
public record SubscriptionActionResponse(
        String status,
        Instant currentPeriodEnd
) {}
