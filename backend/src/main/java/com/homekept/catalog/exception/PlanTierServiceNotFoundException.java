package com.homekept.catalog.exception;

/**
 * Thrown when there is no {@code plan_tier_service} row for a given (planTierId, serviceId)
 * pair — the caller is trying to change the frequency of, or remove, a service that isn't
 * currently part of that plan tier's composition.
 *
 * <p>Maps to HTTP 404. The plan tier and service themselves are checked to exist first (see
 * {@code CatalogAdminService}), so this exception specifically means "that pairing doesn't
 * exist," not "one of the ids is bogus."
 */
public class PlanTierServiceNotFoundException extends RuntimeException {

    public PlanTierServiceNotFoundException(Long planTierId, Long serviceId) {
        super("Plan tier " + planTierId + " does not currently include service " + serviceId);
    }
}
