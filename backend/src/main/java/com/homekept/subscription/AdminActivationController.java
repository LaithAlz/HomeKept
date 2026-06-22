package com.homekept.subscription;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoint to send an activation invite to a walk-through booking.
 *
 * <p>Kept in the subscription package (not booking) so {@link AdminBookingController}
 * in the booking domain remains subscription-free — no backwards booking→subscription
 * coupling.
 *
 * <p>ADMIN role is enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminActivationController {

    private final ActivationService activationService;

    public AdminActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    /**
     * POST /api/admin/bookings/{id}/activation-invite
     * Mints an activation token and sends the magic-link email to the prospective subscriber.
     * The booking must exist (BookingNotFoundException → 404 if missing).
     * No PII in the response — only a status label.
     */
    @PostMapping("/{id}/activation-invite")
    public ResponseEntity<Map<String, String>> sendActivationInvite(@PathVariable Long id) {
        activationService.sendInvite(id);
        return ResponseEntity.ok(Map.of("status", "INVITE_SENT"));
    }
}
