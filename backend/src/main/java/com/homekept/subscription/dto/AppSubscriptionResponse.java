package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response for {@code GET /api/app/subscription} — the customer app's billing/plan summary
 * (billing + settings pages).
 *
 * <p>No PII — IDs, enums, integer cents, booleans, and timestamps only. Money is integer
 * cents (never floats). {@code priceCents} is the price actually charged for the
 * subscriber's billing cycle: the founding rate (when {@code foundingRate} is true and the
 * plan has one) takes precedence over the regular monthly/annual price.
 *
 * <p>{@code planCode}, {@code planDisplayName}, and {@code priceCents} are {@code null}
 * before checkout completes (a {@code PENDING_ACTIVATION} subscriber has no plan tier
 * assigned yet). {@code foundingRateExpiresAt} is {@code null} unless {@code foundingRate}
 * is true. {@code nextVisitDate} is {@code null} when no SCHEDULED visit exists yet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppSubscriptionResponse(
        String status,
        String planCode,
        String planDisplayName,
        String billingCycle,
        Integer priceCents,
        boolean foundingRate,
        Instant foundingRateExpiresAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant nextVisitDate
) {}
