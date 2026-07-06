package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response item for {@code GET /api/app/properties} — one row per property the
 * authenticated user owns (multi-property portfolio, Phase 1 — see
 * docs/portfolio-multi-property-proposal.md).
 *
 * <p>Pure composition over existing domain services (subscriber + catalog, property,
 * visit, the Home Health Score rubric) — no new data, no fabrication. No PII beyond the
 * service address, which the owner already sees on the account page for the same property.
 *
 * <p>{@code planCode}/{@code planDisplayName} are {@code null} pre-checkout (no plan tier
 * assigned yet), matching {@link AppSubscriptionResponse}. {@code nextVisitDate} is
 * {@code null} when no SCHEDULED visit exists. {@code healthScore} and
 * {@code openItemsCount} are always computable (0 is a real, non-error value for both).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppPropertySummary(
        Long propertyId,
        Long subscriberId,
        String streetAddress,
        String city,
        String status,
        String planCode,
        String planDisplayName,
        int healthScore,
        Instant nextVisitDate,
        long openItemsCount
) {}
