package com.homekept.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/subscribers/{id}/cancel}.
 *
 * <p>{@code reason} is required (churn data) and stored as a {@code MANUAL}
 * {@code subscription_event} (JSONB payload
 * {@code {"reason": ..., "by": "ADMIN", "byUserId": ..., "immediate": true|false}}) before
 * Stripe is called. {@code immediately} defaults to {@code false} (cancel at period end,
 * matching the customer self-serve flow); {@code true} cancels the Stripe subscription right
 * away.
 *
 * <p>{@code immediately} is a boxed {@link Boolean}, not a primitive: Jackson 3 rejects a
 * missing/{@code null} JSON value for a primitive record component
 * ({@code MismatchedInputException: Cannot map null into type boolean}) instead of silently
 * defaulting it, so a request that omits the field entirely (the common case — cancel at
 * period end) would otherwise fail body binding with a 400 before validation even runs. The
 * controller normalizes a {@code null} to {@code false}.
 */
public record AdminCancelSubscriptionRequest(
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason,
        Boolean immediately
) {}
