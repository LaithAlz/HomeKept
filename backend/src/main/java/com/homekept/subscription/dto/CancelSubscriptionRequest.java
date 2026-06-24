package com.homekept.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/app/subscription/cancel}.
 *
 * <p>A reason is required — it is churn data, captured as a {@code MANUAL}
 * {@code subscription_event} at request time. The reason is free text from the customer;
 * it is stored in the JSONB payload and never interpolated into a query.
 */
public record CancelSubscriptionRequest(
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        String reason
) {}
