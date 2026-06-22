package com.homekept.visit;

import com.homekept.visit.dto.AppVisitDetail;
import com.homekept.visit.dto.AppVisitListItem;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AppVisitController(VisitAppService visitAppService) {
        this.visitAppService = visitAppService;
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
}
