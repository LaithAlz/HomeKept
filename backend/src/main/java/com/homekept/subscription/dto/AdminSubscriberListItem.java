package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cursor-paginated list item for {@code GET /api/admin/subscribers}.
 *
 * <p>Carries customer PII ({@code firstName}, {@code lastName}, {@code email},
 * {@code phone}) in addition to ids, enums, and integer cents. This is safe only because
 * {@code AdminSubscriberController} is {@code @PreAuthorize("hasRole('ADMIN')")} — never
 * log these fields, and never reuse this DTO on a non-admin-gated endpoint.
 *
 * <p>{@code planCode} is null when no plan has been assigned yet (subscriber still
 * PENDING_ACTIVATION pre-checkout); so is {@code planPriceCents} in that same case.
 * {@code mrrCents} is never null: it is the plan's monthly price ONLY while the subscriber's
 * status is currently-paying revenue ({@code SubscriberStatus.isBilling()} — ACTIVE only),
 * and {@code 0} otherwise (including CANCELLED, PAUSED, PAYMENT_ISSUE, and
 * PENDING_ACTIVATION) — so a cancelled customer's row reads "$0 MRR" rather than
 * misreporting their old plan's list price as ongoing revenue. {@code planPriceCents} is
 * that list price regardless of billing status, so the console can still show e.g. "Complete,
 * $169/mo" as a plan attribute on a $0-MRR row. {@code firstName}, {@code lastName},
 * {@code email}, and {@code phone} are resolved from the identity domain via
 * {@code UserQueryService.findAdminContactsByIds} (one batched query per page, never
 * per-row); {@code phone} is frequently null since it isn't captured at account creation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSubscriberListItem(
        Long id,
        String status,
        String planCode,
        Integer mrrCents,
        Integer planPriceCents,
        String firstName,
        String lastName,
        String email,
        String phone
) {}
