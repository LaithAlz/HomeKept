package com.homekept.subscription.dto;

/**
 * Response body for {@code POST /api/billing/portal-session}.
 *
 * @param portalUrl the Stripe-hosted billing portal URL to redirect the customer to
 */
public record PortalSessionResponse(String portalUrl) {}
