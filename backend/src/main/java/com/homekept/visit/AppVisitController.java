package com.homekept.visit;

import com.homekept.visit.dto.AppVisitDetail;
import com.homekept.visit.dto.AppVisitListItem;
import com.homekept.visit.dto.CreateRescheduleRequest;
import com.homekept.visit.dto.RescheduleRequestResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing visit endpoints (role: CUSTOMER).
 *
 * <p>The authenticated user's subscriber is resolved inside the service from the JWT
 * principal (a {@code Long} user id set by {@link com.homekept.identity.JwtAuthFilter}).
 * The caller never supplies a subscriber id — preventing IDOR.
 *
 * <p>These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig;
 * they are NOT in the public allowlist. The {@code @PreAuthorize} annotation is the second
 * role gate.
 *
 * <p>Ownership failures → 404 (not 403). 403 = wrong role.
 */
@RestController
@RequestMapping("/api/app/visits")
@PreAuthorize("hasRole('CUSTOMER')")
public class AppVisitController {

    private final VisitAppService visitAppService;
    private final RescheduleService rescheduleService;

    public AppVisitController(VisitAppService visitAppService,
                             RescheduleService rescheduleService) {
        this.visitAppService = visitAppService;
        this.rescheduleService = rescheduleService;
    }

    /**
     * GET /api/app/visits?status=&cursor=&limit=
     *
     * <p>Returns the authenticated subscriber's visits, cursor-paginated and ordered by
     * scheduledFor descending (newest/soonest first per the API contract).
     *
     * @param status optional status filter (name of {@link VisitStatus})
     * @param cursor optional id cursor (exclusive upper bound)
     * @param limit  optional page size (default 20, max 100)
     * @param auth   JWT principal — Long user id
     */
    @GetMapping
    public ResponseEntity<List<AppVisitListItem>> listVisits(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(visitAppService.listVisits(userId, status, cursor, limit));
    }

    /**
     * GET /api/app/visits/{id}
     *
     * <p>Returns the full visit detail including checklist. Returns 404 if the visit
     * does not belong to the authenticated subscriber (ownership → 404, not 403).
     *
     * @param id   the visit id
     * @param auth JWT principal — Long user id
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppVisitDetail> getVisit(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(visitAppService.getVisit(userId, id));
    }

    /**
     * POST /api/app/visits/{id}/reschedule-request
     *
     * <p>Records a PENDING reschedule request with the customer's proposed time slots,
     * for admin confirmation. Returns 404 if the visit is not the authenticated
     * subscriber's; 409 if the visit is not SCHEDULED or a pending request already exists.
     *
     * @param id      the visit id
     * @param request the proposed times
     * @param auth    JWT principal — Long user id
     * @return 201 with the created request
     */
    @PostMapping("/{id}/reschedule-request")
    public ResponseEntity<RescheduleRequestResponse> requestReschedule(
            @PathVariable Long id,
            @Valid @RequestBody CreateRescheduleRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        RescheduleRequestResponse response =
                rescheduleService.createRequest(userId, id, request.preferredDates());
        return ResponseEntity
                .created(java.net.URI.create("/api/app/visits/" + id + "/reschedule-request"))
                .body(response);
    }

    /**
     * DELETE /api/app/visits/{id}/reschedule-request
     *
     * <p>Withdraws the authenticated customer's own PENDING reschedule request for the visit,
     * before an admin has confirmed or declined it. Frees the partial-unique-index slot so a
     * new request can be submitted. Returns 404 if the visit is not the authenticated
     * subscriber's, or if there is no PENDING request for it (already resolved or never
     * created — either way, "nothing to cancel").
     *
     * @param id   the visit id
     * @param auth JWT principal — Long user id
     * @return 204 on success
     */
    @DeleteMapping("/{id}/reschedule-request")
    public ResponseEntity<Void> cancelReschedule(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        rescheduleService.cancelRequest(userId, id);
        return ResponseEntity.noContent().build();
    }
}
