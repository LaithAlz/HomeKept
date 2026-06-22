package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cursor-paginated list item for {@code GET /api/admin/subscribers}.
 *
 * <p>No PII — IDs, enums, and integer cents only.
 * {@code mrrCents} and {@code planCode} are null when no plan has been assigned yet
 * (subscriber still PENDING_ACTIVATION pre-checkout).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSubscriberListItem(
        Long id,
        String status,
        String planCode,
        Integer mrrCents,
        boolean foundingRate
) {}
