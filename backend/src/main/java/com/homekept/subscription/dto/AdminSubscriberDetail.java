package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Full detail response for {@code GET /api/admin/subscribers/{id}}.
 *
 * <p>Carries customer PII ({@code firstName}, {@code lastName}, {@code email},
 * {@code phone}) in addition to ids, enums, integer cents, booleans, and timestamps. This
 * is safe only because {@code AdminSubscriberController} is
 * {@code @PreAuthorize("hasRole('ADMIN')")} — never log these fields, and never reuse this
 * DTO on a non-admin-gated endpoint.
 * Property access notes are NEVER decrypted here — only {@code hasAccessNotes} is exposed.
 * Stripe IDs are internal references (not PII per arch doc §5.2).
 * {@code firstName}, {@code lastName}, {@code email}, and {@code phone} are resolved from
 * the identity domain via {@code UserQueryService.findAdminContactById}; {@code phone} is
 * frequently null since it isn't captured at account creation.
 *
 * <p>{@code mrrCents} is never null: it is the plan's monthly price ONLY while the
 * subscriber's status is currently-paying revenue ({@code SubscriberStatus.isBilling()} —
 * ACTIVE only), and {@code 0} otherwise (including CANCELLED, PAUSED, PAYMENT_ISSUE, and
 * PENDING_ACTIVATION). {@code planPriceCents} is the plan's list price regardless of billing
 * status (null if no plan has been assigned yet) — kept separately so the page can still show
 * e.g. "Complete, $169/mo" as a plan attribute on a $0-MRR (e.g. cancelled) customer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSubscriberDetail(
        Long id,
        Long userId,
        String status,
        String planCode,
        Integer mrrCents,
        Integer planPriceCents,
        String billingCycle,
        String stripeCustomerId,
        String stripeSubscriptionId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant startedAt,
        Instant pausedAt,
        Instant cancelledAt,
        AdminSubscriberPropertySummary property,
        String firstName,
        String lastName,
        String email,
        String phone
) {}
