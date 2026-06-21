package com.homekept.booking;

import com.homekept.booking.dto.AdminBookingDetail;
import com.homekept.booking.dto.AdminBookingListItem;
import com.homekept.booking.dto.AdminPatchBookingRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only booking endpoints.
 * Both require role ADMIN — enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig;
 * they are NOT in the public allowlist.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * GET /api/admin/bookings?status=&cursor=&limit=
     * Cursor-paginated walk-through pipeline.
     * - {@code status}: optional filter (name of {@link BookingStatus})
     * - {@code cursor}: optional id cursor (exclusive upper bound)
     * - {@code limit}: optional page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<List<AdminBookingListItem>> listBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(bookingService.listBookings(status, cursor, limit));
    }

    /**
     * PATCH /api/admin/bookings/{id}
     * Status transition and/or scheduledFor update.
     * Status must be a legal transition per {@link WalkthroughBookingStateMachine}.
     * Illegal transitions → 409.
     * Missing booking → 404.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminBookingDetail> patchBooking(
            @PathVariable Long id,
            @RequestBody AdminPatchBookingRequest request) {
        return ResponseEntity.ok(bookingService.patchBooking(id, request));
    }
}
