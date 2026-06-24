package com.homekept.visit;

import com.homekept.visit.dto.AdminConfirmRescheduleRequest;
import com.homekept.visit.dto.AdminDeclineRescheduleRequest;
import com.homekept.visit.dto.AdminRescheduleRequestItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only reschedule-request queue (#54): list pending, confirm (reschedules the visit
 * via the state machine), or decline with a note.
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize}; these fall under
 * {@code .anyRequest().authenticated()} in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/reschedule-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRescheduleController {

    private final RescheduleService rescheduleService;

    public AdminRescheduleController(RescheduleService rescheduleService) {
        this.rescheduleService = rescheduleService;
    }

    /**
     * GET /api/admin/reschedule-requests — the PENDING queue, oldest first.
     */
    @GetMapping
    public ResponseEntity<List<AdminRescheduleRequestItem>> listPending() {
        return ResponseEntity.ok(rescheduleService.listPending());
    }

    /**
     * POST /api/admin/reschedule-requests/{id}/confirm — reschedule the visit to the chosen
     * time and mark the request CONFIRMED. 404 if missing; 409 if already resolved or the
     * visit is no longer reschedulable.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<AdminRescheduleRequestItem> confirm(
            @PathVariable Long id,
            @Valid @RequestBody AdminConfirmRescheduleRequest request) {
        return ResponseEntity.ok(
                rescheduleService.confirm(id, request.scheduledFor(), request.adminNote()));
    }

    /**
     * POST /api/admin/reschedule-requests/{id}/decline — mark DECLINED with a required note.
     * 404 if missing; 409 if already resolved.
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<AdminRescheduleRequestItem> decline(
            @PathVariable Long id,
            @Valid @RequestBody AdminDeclineRescheduleRequest request) {
        return ResponseEntity.ok(rescheduleService.decline(id, request.adminNote()));
    }
}
