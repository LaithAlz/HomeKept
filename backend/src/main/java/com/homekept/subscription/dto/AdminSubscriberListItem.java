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
 * <p>{@code mrrCents} and {@code planCode} are null when no plan has been assigned yet
 * (subscriber still PENDING_ACTIVATION pre-checkout). {@code firstName}, {@code lastName},
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
        String firstName,
        String lastName,
        String email,
        String phone
) {}
