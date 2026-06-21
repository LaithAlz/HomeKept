package com.homekept.booking;

import com.homekept.booking.dto.AdminBookingDetail;
import com.homekept.booking.dto.AdminBookingListItem;
import com.homekept.booking.dto.AdminPatchBookingRequest;
import com.homekept.booking.dto.WalkthroughBookingRequest;
import com.homekept.booking.dto.WalkthroughBookingResponse;
import com.homekept.booking.exception.BookingNotFoundException;
import com.homekept.booking.exception.IllegalBookingTransitionException;
import com.homekept.booking.exception.InvalidBookingRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the booking domain.
 *
 * <p>Rules:
 * <ul>
 *   <li>Every status write goes through {@link WalkthroughBookingStateMachine}.</li>
 *   <li>No PII in logs — booking ID only.</li>
 *   <li>The {@link BookingNotifier} seam is called after persist; the real email is
 *       deferred to the notification slice.</li>
 * </ul>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** Allowed squareFootageRange values per api-contract.md. */
    private static final Set<String> ALLOWED_SQ_FT_RANGES =
            new HashSet<>(Arrays.asList("<1500", "1500-2500", "2500-4000", ">4000"));

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WalkthroughBookingRepository bookingRepository;
    private final WalkthroughBookingStateMachine stateMachine;
    private final BookingNotifier bookingNotifier;

    public BookingService(WalkthroughBookingRepository bookingRepository,
                          WalkthroughBookingStateMachine stateMachine,
                          BookingNotifier bookingNotifier) {
        this.bookingRepository = bookingRepository;
        this.stateMachine = stateMachine;
        this.bookingNotifier = bookingNotifier;
    }

    // ── Public submission ─────────────────────────────────────────────────────

    /**
     * Creates a new walk-through booking in PENDING status.
     *
     * <p>CASL: {@code contactConsentAt} is recorded as the current instant.
     * The {@code contactConsent} field has already been validated as {@code true}
     * by Bean Validation in the controller.
     *
     * @param request validated booking request
     * @return 201 response body: booking id + status "PENDING"
     */
    @Transactional
    public WalkthroughBookingResponse createBooking(WalkthroughBookingRequest request) {
        // Validate squareFootageRange if present
        if (request.squareFootageRange() != null
                && !request.squareFootageRange().isBlank()
                && !ALLOWED_SQ_FT_RANGES.contains(request.squareFootageRange())) {
            throw new InvalidBookingRequestException(
                    "squareFootageRange must be one of: <1500, 1500-2500, 2500-4000, >4000");
        }

        // Resolve leadSource — default WEBSITE_DIRECT
        LeadSource leadSource = LeadSource.WEBSITE_DIRECT;
        if (request.leadSource() != null && !request.leadSource().isBlank()) {
            try {
                leadSource = LeadSource.valueOf(request.leadSource().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidBookingRequestException("Invalid leadSource value: " + request.leadSource());
            }
        }

        Set<BookingDayOfWeek> dayPrefs = request.dayPreferences() == null
                ? new HashSet<>()
                : new HashSet<>(request.dayPreferences());

        WalkthroughBooking booking = new WalkthroughBooking(
                request.fullName(),
                request.email(),
                request.phone(),
                request.streetAddress(),
                request.city(),
                request.postalCode(),
                request.yearBuilt(),
                request.squareFootageRange(),
                request.propertyType(),
                request.preferredWeek(),
                request.timeOfDay(),
                dayPrefs,
                request.notes(),
                leadSource,
                request.posthogDistinctId(),
                Instant.now()   // contact_consent_at — recorded server-side
        );

        WalkthroughBooking saved = bookingRepository.save(booking);

        // Notify via the seam (logs only at MVP; real email added in notification slice)
        bookingNotifier.sendBookingConfirmation(saved);

        log.info("Booking created id={} leadSource={}", saved.getId(), saved.getLeadSource());
        return new WalkthroughBookingResponse(saved.getId(), saved.getStatus().name());
    }

    // ── Admin pipeline ────────────────────────────────────────────────────────

    /**
     * Returns a cursor-paginated list of walk-through bookings for the admin pipeline.
     * Ordered by id descending (newest first). If {@code status} is provided, filters
     * by that status; otherwise returns all statuses.
     *
     * @param status optional status filter (name of {@link BookingStatus})
     * @param cursor optional id cursor (exclusive upper bound — return rows with id &lt; cursor)
     * @param limit  optional page size (defaults to {@value DEFAULT_PAGE_SIZE}, capped at 100)
     */
    @Transactional(readOnly = true)
    public List<AdminBookingListItem> listBookings(String status, Long cursor, Integer limit) {
        int pageSize = resolveLimit(limit);
        PageRequest pageable = PageRequest.of(0, pageSize);

        List<WalkthroughBooking> bookings;

        if (status != null && !status.isBlank()) {
            BookingStatus bookingStatus;
            try {
                bookingStatus = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidBookingRequestException("Invalid status value: " + status);
            }

            bookings = (cursor != null)
                    ? bookingRepository.findByStatusAndIdLessThanOrderByIdDesc(bookingStatus, cursor, pageable)
                    : bookingRepository.findByStatusOrderByIdDesc(bookingStatus, pageable);
        } else {
            bookings = (cursor != null)
                    ? bookingRepository.findByIdLessThanOrderByIdDesc(cursor, pageable)
                    : bookingRepository.findAllByOrderByIdDesc(pageable);
        }

        return bookings.stream()
                .map(AdminBookingListItem::from)
                .collect(Collectors.toList());
    }

    /**
     * Applies a partial update to a booking (status transition and/or scheduledFor).
     * Status transitions are validated through the state machine.
     * Returns the updated booking as a full detail DTO.
     *
     * @param id      booking id
     * @param request patch request (both fields optional)
     * @return updated booking detail
     * @throws BookingNotFoundException          if the booking does not exist
     * @throws IllegalBookingTransitionException if the status transition is not permitted
     */
    @Transactional
    public AdminBookingDetail patchBooking(Long id, AdminPatchBookingRequest request) {
        WalkthroughBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (request.status() != null && !request.status().isBlank()) {
            BookingStatus targetStatus;
            try {
                targetStatus = BookingStatus.valueOf(request.status().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidBookingRequestException("Invalid status value: " + request.status());
            }

            BookingStatus fromStatus = booking.getStatus();
            if (!stateMachine.canTransition(fromStatus, targetStatus)) {
                throw new IllegalBookingTransitionException(fromStatus, targetStatus);
            }
            booking.setStatus(targetStatus);
            log.info("Booking id={} status transition: {} -> {}", id, fromStatus, targetStatus);
        }

        if (request.scheduledFor() != null) {
            booking.setScheduledFor(request.scheduledFor());
        }

        WalkthroughBooking saved = bookingRepository.save(booking);
        return AdminBookingDetail.from(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 100);
    }
}
