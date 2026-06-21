package com.homekept.booking;

import com.homekept.booking.dto.WalkthroughBookingRequest;
import com.homekept.booking.dto.WalkthroughBookingResponse;
import com.homekept.common.ClientIpResolver;
import com.homekept.identity.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public booking endpoint — no auth required (added to SecurityConfig allowlist).
 * Rate limited: 3 submissions per IP per hour.
 *
 * <p>IP is resolved via {@link ClientIpResolver#resolve(HttpServletRequest)}, which
 * prefers the {@code CF-Connecting-IP} header (set by Cloudflare, not spoofable by
 * clients) over {@code request.getRemoteAddr()}. This prevents a client from rotating
 * the rate-limit bucket by supplying different {@code X-Forwarded-For} values.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRateLimiter rateLimiter;

    public BookingController(BookingService bookingService, BookingRateLimiter rateLimiter) {
        this.bookingService = bookingService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/bookings/walkthrough
     * Public — added to SecurityConfig allowlist.
     * Rate limit: 3/IP/hour (per api-contract.md).
     * On success: 201 { id, status: "PENDING" }.
     */
    @PostMapping("/walkthrough")
    public ResponseEntity<WalkthroughBookingResponse> submitWalkthrough(
            @Valid @RequestBody WalkthroughBookingRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new RateLimitExceededException();
        }

        WalkthroughBookingResponse response = bookingService.createBooking(request);
        return ResponseEntity
                .created(URI.create("/api/bookings/walkthrough/" + response.id()))
                .body(response);
    }
}
