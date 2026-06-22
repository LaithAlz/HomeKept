package com.homekept.subscription.dto;

/**
 * Response body for {@code POST /api/activation/complete}.
 * {@code next} is always {@code "CHECKOUT"} — the frontend redirects to Stripe checkout.
 */
public record ActivationCompleteResponse(
        Long userId,
        String next
) {}
