package com.homekept.subscription.dto;

/**
 * Response body for {@code POST /api/checkout/session}.
 *
 * @param checkoutUrl the Stripe-hosted checkout URL to redirect the customer to
 */
public record CheckoutSessionResponse(String checkoutUrl) {}
