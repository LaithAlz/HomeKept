package com.homekept.catalog;

import com.homekept.catalog.dto.PicksMenuResponse;
import com.homekept.catalog.dto.PlanTierResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public catalog endpoints — no auth required (see SecurityConfig allowlist).
 *
 * <p>DTOs only cross this boundary; entities stay in the service layer.
 * No mutations — catalog is read-only at the API level.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * GET /api/catalog/plans
     * Returns all plan tiers with pricing and included services. Public — no auth.
     * Prices per docs/pricing-and-visits.md; money is integer cents.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanTierResponse>> getPlans() {
        return ResponseEntity.ok(catalogService.getPlans());
    }

    /**
     * GET /api/catalog/picks
     * Returns the pickable services menu grouped by tier class with à la carte prices.
     * Public — no auth. Prices: BASIC $49 / MEDIUM $89 / PREMIUM $149 (integer cents).
     */
    @GetMapping("/picks")
    public ResponseEntity<PicksMenuResponse> getPicks() {
        return ResponseEntity.ok(catalogService.getPicksMenu());
    }
}
