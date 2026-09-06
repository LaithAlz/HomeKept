package com.homekept.catalog.dto;

import com.homekept.catalog.PlanTierService;
import com.homekept.catalog.TierClass;

/**
 * Response body for the plan-composition admin endpoints: adding a service to a plan tier,
 * changing its frequency. Carries {@code serviceId} (unlike the public
 * {@link ServiceSummary}, which is display-only) so the console can address the row for a
 * follow-up PATCH/DELETE.
 */
public record AdminPlanTierServiceResponse(
        Long planTierId,
        Long serviceId,
        String serviceName,
        TierClass serviceTierClass,
        int frequencyPerYear
) {
    public static AdminPlanTierServiceResponse from(PlanTierService pts) {
        return new AdminPlanTierServiceResponse(
                pts.getPlanTier().getId(),
                pts.getService().getId(),
                pts.getService().getName(),
                pts.getService().getTierClass(),
                pts.getFrequencyPerYear()
        );
    }
}
