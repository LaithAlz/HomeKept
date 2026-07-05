package com.homekept.technician;

import com.homekept.technician.dto.AdminTechnicianListItem;
import com.homekept.technician.dto.CreateTechnicianRequest;
import com.homekept.technician.dto.TechnicianProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only technician endpoints.
 *
 * <p><strong>Note for founders:</strong> role assignment to TECHNICIAN must be done
 * in the identity layer first (e.g. via a seed migration or direct DB update). The POST
 * endpoint only creates the {@code technician_profile} row for a user who already has
 * the TECHNICIAN role. The profile is what links them to the daily visit roster and
 * provides the {@code fully_loaded_hourly_cost_cents} for unit economics.
 *
 * <p>Technician regions/availability are Stage 3 (50+ customers) — deferred per §2.7.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTechnicianController {

    private final TechnicianAdminService technicianAdminService;

    public AdminTechnicianController(TechnicianAdminService technicianAdminService) {
        this.technicianAdminService = technicianAdminService;
    }

    /**
     * GET /api/admin/technicians
     *
     * <p>Returns the full technician roster (name, email, role, status resolved from the
     * identity domain; employee status, hire date, and hourly cost from the technician
     * profile). No pagination — the roster is small at MVP.
     *
     * @return {@code 200} with the roster, newest first
     */
    @GetMapping("/technicians")
    public ResponseEntity<List<AdminTechnicianListItem>> listTechnicians() {
        return ResponseEntity.ok(technicianAdminService.listTechnicians());
    }

    /**
     * POST /api/admin/technicians
     *
     * <p>Creates a {@code technician_profile} for an existing user (who must already
     * have the TECHNICIAN role). Idempotent guard: returns 409 if a profile already
     * exists for this user.
     *
     * @param request onboarding data — userId, optional cost, status, hire date
     * @return {@code 201} with the created profile
     */
    @PostMapping("/technicians")
    public ResponseEntity<TechnicianProfileResponse> createTechnician(
            @Valid @RequestBody CreateTechnicianRequest request) {
        TechnicianProfileResponse response = technicianAdminService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
