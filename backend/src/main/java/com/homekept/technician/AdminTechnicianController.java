package com.homekept.technician;

import com.homekept.technician.dto.AdminTechnicianListItem;
import com.homekept.technician.dto.CreateTechnicianRequest;
import com.homekept.technician.dto.CreateTechnicianResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * Admin-only technician endpoints.
 *
 * <p>Onboarding is invite-by-email: the admin supplies identity only (name, email, optional
 * phone) via {@code POST /technicians}; the account is created {@code PENDING_ACTIVATION}
 * and the invited person sets their own password via the public staff-invite accept link
 * ({@link StaffInviteController}). The role is always server-set to TECHNICIAN.
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
     * <p>Returns the full technician roster (name, email, role, status, and latest invite
     * timestamp resolved from the identity domain; employee status, hire date, and hourly
     * cost from the technician profile). No pagination — the roster is small at MVP.
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
     * <p>Invites a new technician by identity only: creates the {@code User} (TECHNICIAN,
     * PENDING_ACTIVATION), the {@code technician_profile}, mints a 7-day invite token, and
     * queues the invite email. Returns 409 if a user with this email already exists.
     *
     * @param request the invite identity data (firstName, lastName, email, optional phone)
     * @return {@code 201} with the created profile + identity summary
     */
    @PostMapping("/technicians")
    public ResponseEntity<CreateTechnicianResponse> createTechnician(
            @Valid @RequestBody CreateTechnicianRequest request) {
        CreateTechnicianResponse response = technicianAdminService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/admin/technicians/{id}/invite
     *
     * <p>Resends the invite for an existing technician profile: invalidates the user's prior
     * unconsumed invite tokens, then mints a fresh one and queues a new invite email, in one
     * transaction, so the old link stops working. {@code id} is the {@code technician_profile}
     * id (the roster row's {@code id}, not the {@code userId}).
     *
     * @param id the technician_profile id
     * @return {@code 202} once the resend has been queued; {@code 404} if no such profile
     */
    @PostMapping("/technicians/{id}/invite")
    public ResponseEntity<Void> resendInvite(@PathVariable Long id) {
        technicianAdminService.resendInvite(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
