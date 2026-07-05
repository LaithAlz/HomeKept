package com.homekept.dashboard;

import com.homekept.dashboard.dto.AdminDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console home / operational dashboard aggregate (issue #43).
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * This endpoint falls under {@code .anyRequest().authenticated()} in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * GET /api/admin/dashboard
     *
     * @return {@code 200} with the aggregate metrics — see {@link AdminDashboardResponse}
     */
    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminDashboardService.getDashboard());
    }
}
