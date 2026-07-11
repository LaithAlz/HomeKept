package com.homekept.property.dto;

/**
 * Response body for {@code PATCH /api/admin/properties/{propertyId}/sku}.
 *
 * <p>DTO at the controller boundary — the {@code Property} entity never crosses out
 * through the controller. Mirrors the SKU fields exposed on
 * {@code AdminSubscriberPropertySummary}.
 */
public record AdminPropertySkuResponse(
        Long propertyId,
        String hvacFilterSizes,
        String smokeCoDetectorModels,
        String humidifierModel,
        Integer waterHeaterAgeYears,
        Boolean waterHeaterFlushEligible
) {}
