package com.homekept.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/subscribers/{id}/cancel}.
 *
 * <p>{@code reason} is required (churn data) and stored as a {@code MANUAL}
 * {@code subscription_event} (JSONB payload {@code {"reason": ..., "by": "ADMIN"}}) before
 * Stripe is called. {@code immediately} defaults to {@code false} (cancel at period end,
 * matching the customer self-serve flow); {@code true} cancels the Stripe subscription right
 * away.
 */
public record AdminCancelSubscriptionRequest(
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason,
        boolean immediately
) {}
