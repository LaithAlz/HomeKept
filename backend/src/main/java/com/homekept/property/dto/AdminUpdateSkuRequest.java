package com.homekept.property.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for {@code PATCH /api/admin/properties/{propertyId}/sku}.
 *
 * <p>All fields are nullable — partial/ongoing capture is expected. The walk-through
 * captures some fields immediately; others are filled in over subsequent visits (per
 * docs/pricing-and-visits.md §Materials). A {@code null} field leaves the corresponding
 * property column unchanged (see {@code PropertyService.updateSkuSheet}).
 */
public record AdminUpdateSkuRequest(
        String hvacFilterSizes,
        String smokeCoDetectorModels,
        String humidifierModel,
        @Min(0) @Max(100) Integer waterHeaterAgeYears,
        Boolean waterHeaterFlushEligible
) {}
