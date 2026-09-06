package com.homekept.catalog;

import com.homekept.catalog.dto.AdminAddPlanTierServiceRequest;
import com.homekept.catalog.dto.AdminCreateServiceRequest;
import com.homekept.catalog.dto.AdminPlanTierServiceResponse;
import com.homekept.catalog.dto.AdminServiceResponse;
import com.homekept.catalog.dto.AdminUpdatePlanTierServiceRequest;
import com.homekept.catalog.dto.AdminUpdateServiceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Admin-only catalog editing endpoints: service CRUD (archive/restore only — never a hard
 * delete) and plan composition (which services a plan tier includes, and at what
 * frequency).
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (second gate after the JWT filter). These
 * endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig.
 *
 * <p><b>Deliberately absent:</b> no endpoint here writes plan tier pricing
 * ({@code monthlyPriceCents}, {@code annualPriceCents}, {@code visitsPerYear},
 * {@code includedPicksPerYear}, {@code maxPremiumPicksPerYear}, any Stripe price id). Plan
 * pricing is migration + Stripe only — see {@link CatalogAdminService}'s javadoc.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;

    public AdminCatalogController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    // ── Service CRUD ──────────────────────────────────────────────────────────

    /**
     * GET /api/admin/services
     *
     * <p>Returns every service, active and archived, sorted by tier class then name (same
     * order as the public picks menu). The public {@code GET /api/catalog/picks} only
     * returns {@code active = true} services — this is the admin view that also shows what
     * has been archived.
     */
    @GetMapping("/services")
    public ResponseEntity<List<AdminServiceResponse>> listServices() {
        List<AdminServiceResponse> services = catalogAdminService.listAllServices().stream()
                .map(AdminServiceResponse::from)
                .toList();
        return ResponseEntity.ok(services);
    }

    /**
     * POST /api/admin/services
     *
     * <p>Creates a new service. {@code category}/{@code tierClass} must be one of the values
     * the {@code service} table's CHECK constraints allow — a bad value is a 400
     * {@code VALIDATION_FAILED}. New services always start {@code active = true}.
     *
     * @return {@code 201} with the created service
     */
    @PostMapping("/services")
    public ResponseEntity<AdminServiceResponse> createService(
            @Valid @RequestBody AdminCreateServiceRequest request) {
        com.homekept.catalog.Service service = catalogAdminService.createService(
                request.name(),
                request.category(),
                request.tierClass(),
                request.defaultDurationMinutes(),
                request.aLaCartePriceCents(),
                request.description(),
                request.isFreeWithEveryVisit());

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/admin/services/" + service.getId()))
                .body(AdminServiceResponse.from(service));
    }

    /**
     * PATCH /api/admin/services/{id}
     *
     * <p>Updates name, category, tier class, default duration, à la carte price, description,
     * and/or the standing-item flag. Every field is optional — an omitted (or {@code null})
     * field leaves that column unchanged. Never touches {@code active}; see
     * {@link #archiveService} / {@link #restoreService}. Unknown {@code id} → 404; a bad
     * {@code category}/{@code tierClass} → 400 {@code VALIDATION_FAILED}.
     *
     * @return {@code 200} with the updated service
     */
    @PatchMapping("/services/{id}")
    public ResponseEntity<AdminServiceResponse> updateService(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateServiceRequest request) {
        com.homekept.catalog.Service service = catalogAdminService.updateService(
                id,
                request.name(),
                request.category(),
                request.tierClass(),
                request.defaultDurationMinutes(),
                request.aLaCartePriceCents(),
                request.description(),
                request.isFreeWithEveryVisit());

        return ResponseEntity.ok(AdminServiceResponse.from(service));
    }

    /**
     * POST /api/admin/services/{id}/archive
     *
     * <p>Sets {@code active = false}. Never a hard delete — {@code visit_service} and
     * {@code plan_tier_service} rows referencing this service are untouched, so existing
     * visit history is unaffected; the service simply drops out of the public picks menu
     * for future bookings. Idempotent. Unknown {@code id} → 404.
     *
     * @return {@code 200} with the updated service
     */
    @PostMapping("/services/{id}/archive")
    public ResponseEntity<AdminServiceResponse> archiveService(@PathVariable Long id) {
        return ResponseEntity.ok(AdminServiceResponse.from(catalogAdminService.archiveService(id)));
    }

    /**
     * POST /api/admin/services/{id}/restore
     *
     * <p>Sets {@code active = true}. Idempotent. Unknown {@code id} → 404.
     *
     * @return {@code 200} with the updated service
     */
    @PostMapping("/services/{id}/restore")
    public ResponseEntity<AdminServiceResponse> restoreService(@PathVariable Long id) {
        return ResponseEntity.ok(AdminServiceResponse.from(catalogAdminService.restoreService(id)));
    }

    // ── Plan composition ──────────────────────────────────────────────────────

    /**
     * POST /api/admin/plan-tiers/{planTierId}/services
     *
     * <p>Adds a service to a plan tier's composition at the given frequency. The pair
     * (planTierId, serviceId) is a composite primary key on {@code plan_tier_service} — a
     * second "add" for the same pair is a 409 Conflict naming the PATCH endpoint below,
     * rather than a 500 from a constraint violation. Unknown {@code planTierId} or
     * {@code serviceId} → 404.
     *
     * @return {@code 201} with the created composition row
     */
    @PostMapping("/plan-tiers/{planTierId}/services")
    public ResponseEntity<AdminPlanTierServiceResponse> addServiceToPlan(
            @PathVariable Long planTierId,
            @Valid @RequestBody AdminAddPlanTierServiceRequest request) {
        PlanTierService pts = catalogAdminService.addServiceToPlan(
                planTierId, request.serviceId(), request.frequencyPerYear());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminPlanTierServiceResponse.from(pts));
    }

    /**
     * PATCH /api/admin/plan-tiers/{planTierId}/services/{serviceId}
     *
     * <p>Changes the frequency of a service already in the plan tier's composition. 404 if
     * the plan tier, the service, or the composition row itself doesn't exist.
     *
     * @return {@code 200} with the updated composition row
     */
    @PatchMapping("/plan-tiers/{planTierId}/services/{serviceId}")
    public ResponseEntity<AdminPlanTierServiceResponse> updatePlanTierServiceFrequency(
            @PathVariable Long planTierId,
            @PathVariable Long serviceId,
            @Valid @RequestBody AdminUpdatePlanTierServiceRequest request) {
        PlanTierService pts = catalogAdminService.updateFrequency(
                planTierId, serviceId, request.frequencyPerYear());
        return ResponseEntity.ok(AdminPlanTierServiceResponse.from(pts));
    }

    /**
     * DELETE /api/admin/plan-tiers/{planTierId}/services/{serviceId}
     *
     * <p>Removes a service from the plan tier's composition. 404 if the plan tier, the
     * service, or the composition row itself doesn't exist (not a silent no-op — matches
     * the ownership/existence-failure convention used elsewhere, e.g.
     * {@code DELETE /api/app/todos/{id}}).
     *
     * @return {@code 204} on success
     */
    @DeleteMapping("/plan-tiers/{planTierId}/services/{serviceId}")
    public ResponseEntity<Void> removeServiceFromPlan(
            @PathVariable Long planTierId,
            @PathVariable Long serviceId) {
        catalogAdminService.removeServiceFromPlan(planTierId, serviceId);
        return ResponseEntity.noContent().build();
    }
}
