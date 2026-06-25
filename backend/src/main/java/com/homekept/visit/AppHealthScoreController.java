package com.homekept.visit;

import com.homekept.visit.dto.HealthScoreResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing Home Health Score endpoint (role: CUSTOMER).
 *
 * <p>The subscriber is resolved from the JWT principal inside the service — the caller never
 * supplies a subscriber id. Falls under {@code .anyRequest().authenticated()} in SecurityConfig.
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
public class AppHealthScoreController {

    private final HealthScoreService healthScoreService;

    public AppHealthScoreController(HealthScoreService healthScoreService) {
        this.healthScoreService = healthScoreService;
    }

    /**
     * GET /api/app/health-score → {@code { score, delta, computedAt, flagged: [...] }}.
     *
     * @param auth JWT principal — Long user id
     */
    @GetMapping("/api/app/health-score")
    public ResponseEntity<HealthScoreResponse> getHealthScore(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(healthScoreService.getHealthScore(userId));
    }
}
