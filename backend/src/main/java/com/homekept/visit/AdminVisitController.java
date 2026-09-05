package com.homekept.visit;

import com.homekept.visit.dto.AdminCreateVisitRequest;
import com.homekept.visit.dto.AdminPatchVisitRequest;
import com.homekept.visit.dto.AdminVisitDayLoadItem;
import com.homekept.visit.dto.AdminVisitDetail;
import com.homekept.visit.dto.AdminVisitListItem;
import com.homekept.visit.dto.AdminVisitResponse;
import com.homekept.visit.dto.VisitEventItem;
import jakarta.validation.Valid;
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

import java.net.URI;
import java.time.LocalDate;
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
 *   <li>{@code GET /api/admin/visits/{id}} — full single-visit detail</li>
 *   <li>{@code PATCH /api/admin/visits/{id}} — reschedule (in place) / cancel / assign
 *       technician</li>
 *   <li>{@code GET /api/admin/visits/{id}/events} — the visit's activity log</li>
 *   <li>{@code GET /api/admin/visits/day-load?from=&to=} — per-local-day SCHEDULED visit
 *       load (Routes month-sidebar aggregate)</li>
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
     * GET /api/admin/visits/{id}
     *
     * <p>Full single-visit detail: the visit itself, the customer's identity, the
     * property's address, the assigned technician's name, the checklist, and any
     * notes/photos captured so far. This is the link the founder wanted a visit row to
     * open into (rather than the list growing another row on reschedule) — see
     * {@link #listEvents} for the history itself.
     *
     * @param id the visit id
     * @return 200 with the full detail; 404 if no visit exists with this id
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminVisitDetail> getVisit(@PathVariable Long id) {
        return ResponseEntity.ok(visitAdminService.getVisitDetail(id));
    }

    /**
     * PATCH /api/admin/visits/{id}
     *
     * <p>Supports: reschedule in place (provide {@code scheduledFor} — updates the visit's
     * {@code scheduledFor}/technician and records a {@code RESCHEDULED} {@code visit_event};
     * no replacement visit is created), cancel (provide {@code status = "CANCELLED"}),
     * assign technician (provide {@code technicianUserId}). Illegal state transitions →
     * 409. Missing visit → 404.
     *
     * @param id      the visit id
     * @param request the patch request — validated: a present {@code scheduledFor} must be
     *                in the future (400 {@code VALIDATION_FAILED} otherwise)
     * @param auth    the authenticated admin — its principal is recorded as the acting user
     *                on any {@code visit_event} this patch produces
     * @return 200 with the updated visit
     */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminVisitResponse> patchVisit(
            @PathVariable Long id,
            @Valid @RequestBody AdminPatchVisitRequest request,
            Authentication auth) {
        Long adminUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(visitAdminService.patchVisit(id, request, adminUserId));
    }

    /**
     * GET /api/admin/visits/{id}/events
     *
     * <p>The visit's activity log, newest first, capped at 100 rows — mirrors
     * {@code GET /api/admin/subscribers/{id}/events}'s shape and cap. Missing visit → 404.
     *
     * @param id the visit id
     * @return 200 with the events, newest first
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<VisitEventItem>> listEvents(@PathVariable Long id) {
        return ResponseEntity.ok(visitAdminService.listEvents(id));
    }

    /**
     * GET /api/admin/visits/day-load?from=YYYY-MM-DD&amp;to=YYYY-MM-DD
     *
     * <p>Per-local-day SCHEDULED visit load for the admin Routes month-sidebar calendar:
     * one entry per day in {@code [from, to]} (inclusive, local dates in the configured
     * render zone) that has at least one SCHEDULED visit, with the day's total visit count
     * and how many are unassigned. Empty days are omitted; the response is ascending by day.
     *
     * <p>Deliberately no capacity/percentage/"slots free" figure — there is no backing model
     * of technician working hours, so a fabricated availability signal would be worse than
     * none.
     *
     * <p>{@code from}/{@code to} are required; missing or unparseable values fail with the
     * standard 400 validation error. A span longer than 62 days → 400 {@code INVALID_REQUEST}.
     *
     * @param from inclusive local start date
     * @param to   inclusive local end date
     * @return 200 with the per-day load, ascending
     */
    @GetMapping("/day-load")
    public ResponseEntity<List<AdminVisitDayLoadItem>> dayLoad(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(visitAdminService.listDayLoad(from, to));
    }
}
