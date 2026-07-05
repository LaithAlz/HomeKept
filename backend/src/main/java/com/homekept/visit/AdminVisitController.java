package com.homekept.visit;

import com.homekept.visit.dto.AdminCreateVisitRequest;
import com.homekept.visit.dto.AdminPatchVisitRequest;
import com.homekept.visit.dto.AdminVisitListItem;
import com.homekept.visit.dto.AdminVisitResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Admin-only visit endpoints.
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig.
 *
 * <p>API contract (api-contract.md):
 * <ul>
 *   <li>{@code GET /api/admin/visits?status=&cursor=&limit=} — cursor-paginated visit list</li>
 *   <li>{@code POST /api/admin/visits} — create a visit for a subscriber</li>
 *   <li>{@code PATCH /api/admin/visits/{id}} — reschedule / cancel / assign technician</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/visits")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVisitController {

    private final VisitAdminService visitAdminService;

    public AdminVisitController(VisitAdminService visitAdminService) {
        this.visitAdminService = visitAdminService;
    }

    /**
     * GET /api/admin/visits?status=&cursor=&limit=
     * Cursor-paginated visit list, newest first (mirrors {@code AdminBookingController}'s
     * pagination style).
     * - {@code status}: optional filter (name of {@link VisitStatus}); invalid value → 400
     * - {@code cursor}: optional id cursor (exclusive upper bound)
     * - {@code limit}: optional page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<List<AdminVisitListItem>> listVisits(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(visitAdminService.listVisits(status, cursor, limit));
    }

    /**
     * POST /api/admin/visits
     *
     * <p>Creates a visit for the given subscriber. Optional service IDs are attached as
     * checklist items. Optional technician assignment.
     *
     * @param request validated create request
     * @return 201 with the created visit
     */
    @PostMapping
    public ResponseEntity<AdminVisitResponse> createVisit(
            @Valid @RequestBody AdminCreateVisitRequest request) {
        AdminVisitResponse response = visitAdminService.createVisit(request);
        return ResponseEntity
                .created(URI.create("/api/admin/visits/" + response.id()))
                .body(response);
    }

    /**
     * PATCH /api/admin/visits/{id}
     *
     * <p>Supports: reschedule (provide {@code scheduledFor}), cancel (provide
     * {@code status = "CANCELLED"}), assign technician (provide {@code technicianUserId}).
     * Illegal state transitions → 409. Missing visit → 404.
     *
     * @param id      the visit id
     * @param request the patch request
     * @return 200 with the updated visit
     */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminVisitResponse> patchVisit(
            @PathVariable Long id,
            @RequestBody AdminPatchVisitRequest request) {
        return ResponseEntity.ok(visitAdminService.patchVisit(id, request));
    }
}
