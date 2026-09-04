package com.homekept.subscription.dto;

import com.homekept.catalog.PlanCode;
import com.homekept.subscription.BillingCycle;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/checkout/session}.
 *
 * @param planCode     the plan tier to subscribe to (COMPLETE / PREMIER)
 * @param billingCycle MONTHLY or ANNUAL
 */
public record CheckoutSessionRequest(
        @NotNull(message = "planCode is required") PlanCode planCode,
        @NotNull(message = "billingCycle is required") BillingCycle billingCycle
) {}
