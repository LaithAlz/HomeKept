package com.homekept.property;

import com.homekept.property.dto.AdminPropertySkuResponse;
import com.homekept.property.dto.AdminUpdateSkuRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only property endpoints.
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig.
 *
 * <p>Property mutations belong to the property domain — this controller calls
 * {@link PropertyService} directly rather than routing through the subscription domain.
 */
@RestController
@RequestMapping("/api/admin/properties")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPropertyController {

    private final PropertyService propertyService;

    public AdminPropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * PATCH /api/admin/properties/{propertyId}/sku
     *
     * <p>Updates the property's SKU sheet (technician prep data captured by the
     * walk-through and refined over subsequent visits): HVAC filter sizes, smoke/CO
     * detector models, humidifier model, water heater age, and water heater flush
     * eligibility. All request fields are optional/nullable — a {@code null} field
     * leaves the corresponding column unchanged (partial/ongoing capture).
     *
     * <p>Unknown {@code propertyId} → 404. {@code waterHeaterAgeYears} outside 0..100
     * (when present) → 400.
     *
     * @param propertyId the property to update
     * @param request    the SKU fields to apply
     * @return 200 with the updated SKU sheet
     */
    @PatchMapping("/{propertyId}/sku")
    public ResponseEntity<AdminPropertySkuResponse> updateSkuSheet(
            @PathVariable Long propertyId,
            @Valid @RequestBody AdminUpdateSkuRequest request) {
        Property property = propertyService.updateSkuSheet(
                propertyId,
                request.hvacFilterSizes(),
                request.smokeCoDetectorModels(),
                request.humidifierModel(),
                request.waterHeaterAgeYears(),
                request.waterHeaterFlushEligible()
        );

        return ResponseEntity.ok(new AdminPropertySkuResponse(
                property.getId(),
                property.getHvacFilterSizes(),
                property.getSmokeCODetectorModels(),
                property.getHumidifierModel(),
                property.getWaterHeaterAgeYears(),
                property.getWaterHeaterFlushEligible()
        ));
    }
}
