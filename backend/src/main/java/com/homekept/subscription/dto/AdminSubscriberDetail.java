package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Full detail response for {@code GET /api/admin/subscribers/{id}}.
 *
 * <p>No PII — IDs, enums, integer cents, booleans, and timestamps only.
 * Property access notes are NEVER decrypted here — only {@code hasAccessNotes} is exposed.
 * Stripe IDs are internal references (not PII per arch doc §5.2).
 *
 * <p>{@code visits} is an empty list at MVP (visit domain not yet built);
 * the field is present so the frontend shape is stable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSubscriberDetail(
        Long id,
        Long userId,
        String status,
        String planCode,
        Integer mrrCents,
        boolean foundingRate,
        String billingCycle,
        String stripeCustomerId,
        String stripeSubscriptionId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant startedAt,
        Instant pausedAt,
        Instant cancelledAt,
        AdminSubscriberPropertySummary property,
        List<Object> visits
) {}
