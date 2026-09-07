package com.homekept.property;

import com.homekept.property.dto.AdminPropertySkuResponse;
import com.homekept.property.dto.AdminUpdateSkuRequest;
import com.homekept.property.dto.CreatePropertyNoteRequest;
import com.homekept.property.dto.PropertyNoteItem;
import com.homekept.property.dto.PropertyNotePage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only property endpoints.
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (defense in depth): {@code /api/admin/**}
 * already requires {@code hasRole("ADMIN")} in {@link com.homekept.config.SecurityConfig},
 * not merely {@code anyRequest().authenticated()}.
 *
 * <p>Property mutations belong to the property domain — this controller calls
 * {@link PropertyService} directly rather than routing through the subscription domain.
 *
 * <p>The notes endpoints below read/write the SAME {@code property_note} rows the assigned
 * technician can reach via {@code GET}/{@code POST /api/tech/properties/{propertyId}/notes}
 * (in the visit domain's {@code TechVisitController}) — an admin simply has no ownership
 * restriction on top of the ADMIN role gate.
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

    /**
     * GET /api/admin/properties/{propertyId}/notes?cursor=&limit=
     *
     * <p>A property's threaded operational notes, newest first, cursor-paginated (same
     * convention as {@code GET /api/admin/visits} — an exclusive-upper-bound {@code id}
     * cursor, default/max page size via {@code Pagination.resolveLimit}) — standing
     * knowledge about the home ("dog in the yard", "gate sticks"), as distinct from a single
     * visit's own notes. Never the encrypted access notes. Unknown {@code propertyId} → 404.
     *
     * @param propertyId the property id
     * @param cursor     optional id cursor (exclusive upper bound)
     * @param limit      optional page size (default 20, max 100)
     * @param auth       the authenticated admin — recorded in the read-audit log line, not
     *                   used for authorization
     * @return 200 with the requested page of notes, newest first
     */
    @GetMapping("/{propertyId}/notes")
    public ResponseEntity<PropertyNotePage> listNotes(
            @PathVariable Long propertyId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            Authentication auth) {
        Long adminUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(propertyService.listNotes(propertyId, cursor, limit, adminUserId));
    }

    /**
     * POST /api/admin/properties/{propertyId}/notes
     *
     * <p>Adds a note to the property's operational log. The author is always the
     * authenticated admin (the JWT principal) — there is no way to supply a different author
     * in the request body. Never accept a lockbox/alarm code or key location here — see
     * {@link CreatePropertyNoteRequest}'s javadoc. There is no delete or edit operation on a
     * note; see {@link PropertyService#addNote}'s javadoc for why.
     *
     * @param propertyId the property id
     * @param request    the note body
     * @param auth       the authenticated admin — recorded as the note's author
     * @return 201 with the created note
     */
    @PostMapping("/{propertyId}/notes")
    public ResponseEntity<PropertyNoteItem> addNote(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreatePropertyNoteRequest request,
            Authentication auth) {
        Long adminUserId = (Long) auth.getPrincipal();
        PropertyNoteItem note = propertyService.addNote(propertyId, request.body(), adminUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }
}
