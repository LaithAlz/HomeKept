package com.homekept.subscription.dto;

import com.homekept.catalog.PlanCode;
import com.homekept.subscription.BillingCycle;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/checkout/session}.
 *
 * @param planCode     the plan tier to subscribe to (ESSENTIAL / COMPLETE / PREMIER)
 * @param billingCycle MONTHLY or ANNUAL
 * @param foundingRate whether to apply the founding-member rate (validated server-side against the cap)
 */
public record CheckoutSessionRequest(
        @NotNull(message = "planCode is required") PlanCode planCode,
        @NotNull(message = "billingCycle is required") BillingCycle billingCycle,
        boolean foundingRate
) {}
