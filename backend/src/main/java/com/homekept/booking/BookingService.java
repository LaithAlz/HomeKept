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

    /**
     * Returns the count of walk-through bookings still PENDING (booked but not yet
     * confirmed by admin). Used by the admin dashboard aggregate.
     */
    @Transactional(readOnly = true)
    public long countPendingWalkthroughs() {
        return bookingRepository.countByStatus(BookingStatus.PENDING);
    }

    // ── Cross-domain accessors (used by subscription domain) ──────────────────

    /**
     * Returns the activation data needed by the subscription domain during the
     * activation flow. The subscription domain MUST call this method — it must
     * never touch {@link WalkthroughBookingRepository} or {@link WalkthroughBooking} directly.
     *
     * @param bookingId the walk-through booking id
     * @return activation data record
     * @throws BookingNotFoundException if the booking does not exist
     */
    @Transactional(readOnly = true)
    public BookingActivationData getActivationData(Long bookingId) {
        WalkthroughBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        String fullName = booking.getFullName() != null ? booking.getFullName().trim() : "";
        String firstName;
        String lastName;
        int spaceIndex = fullName.indexOf(' ');
        if (spaceIndex < 0) {
            firstName = fullName;
            lastName = "";
        } else {
            firstName = fullName.substring(0, spaceIndex);
            lastName = fullName.substring(spaceIndex + 1);
        }

        return new BookingActivationData(
                booking.getId(),
                booking.getEmail(),
                firstName,
                lastName,
                booking.getStreetAddress(),
                booking.getCity(),
                booking.getPostalCode(),
                booking.getYearBuilt(),
                booking.getSquareFootageRange(),
                booking.getPropertyType() != null ? booking.getPropertyType().name() : null
        );
    }

    /**
     * Records that this booking has been converted to a subscriber.
     * Called by the subscription domain after the subscriber row is created.
     *
     * @param bookingId    the walk-through booking id
     * @param subscriberId the newly created subscriber id
     */
    @Transactional
    public void markConverted(Long bookingId, Long subscriberId) {
        WalkthroughBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        // Conversion is a state transition (PERFORMED → CONVERTED, §4.3) — route it through
        // the state machine. This also makes re-conversion impossible: CONVERTED is terminal,
        // so a second attempt on an already-converted booking fails canTransition.
        BookingStatus from = booking.getStatus();
        if (!stateMachine.canTransition(from, BookingStatus.CONVERTED)) {
            throw new IllegalBookingTransitionException(from, BookingStatus.CONVERTED);
        }
        booking.setConvertedToSubscriberId(subscriberId);
        booking.setStatus(BookingStatus.CONVERTED);
        bookingRepository.save(booking);
    }

    /**
     * Attaches an activation token record to this booking.
     * Called by the subscription domain after minting the token.
     *
     * @param bookingId         the walk-through booking id
     * @param activationTokenId the activation token id to attach
     */
    @Transactional
    public void attachActivationToken(Long bookingId, Long activationTokenId) {
        WalkthroughBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        booking.setActivationTokenId(activationTokenId);
        bookingRepository.save(booking);
    }

    /**
     * Returns the CONFIRMED bookings whose {@code scheduledFor} falls within
     * {@code [from, to]}. Called by {@code com.homekept.notification.ReminderScheduler} (#89)
     * to find walk-throughs due for the 24h-before reminder — the notification domain must
     * never touch {@link WalkthroughBookingRepository} or {@link WalkthroughBooking} directly,
     * only this narrow {@link BookingReminderTarget} projection.
     *
     * @param from window lower bound (inclusive)
     * @param to   window upper bound (inclusive)
     */
    @Transactional(readOnly = true)
    public List<BookingReminderTarget> findConfirmedInWindow(Instant from, Instant to) {
        return bookingRepository.findByStatusAndScheduledForBetween(BookingStatus.CONFIRMED, from, to)
                .stream()
                .map(b -> new BookingReminderTarget(b.getId(), b.getEmail(), b.getFullName(),
                        b.getStreetAddress(), b.getCity(), b.getScheduledFor()))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 100);
    }

    // ── Cross-domain data types ───────────────────────────────────────────────

    /**
     * Projection of walk-through booking data needed by the subscription activation flow.
     * This record is the only object the subscription domain receives from the booking domain —
     * no entity references, no repository access crosses the boundary.
     *
     * @param bookingId          the booking id
     * @param email              prospective subscriber email
     * @param firstName          first name (split from fullName on first space)
     * @param lastName           last name (remainder after first space, or empty string)
     * @param streetAddress      property street address
     * @param city               property city
     * @param postalCode         property postal code
     * @param yearBuilt          year the home was built (nullable)
     * @param squareFootageRange square footage range string (nullable)
     * @param propertyType       property type name string (e.g. "DETACHED")
     */
    public record BookingActivationData(
            Long bookingId,
            String email,
            String firstName,
            String lastName,
            String streetAddress,
            String city,
            String postalCode,
            Integer yearBuilt,
            String squareFootageRange,
            String propertyType
    ) {}

    /**
     * Projection of walk-through booking data needed by the notification domain's reminder
     * scheduler (#89). No entity references cross the boundary.
     *
     * @param bookingId     the booking id
     * @param email         prospective subscriber email
     * @param fullName      full name as submitted (the notifier splits it for the greeting)
     * @param streetAddress property street address
     * @param city          property city
     * @param scheduledFor  the walk-through's scheduled time (set when CONFIRMED)
     */
    public record BookingReminderTarget(
            Long bookingId,
            String email,
            String fullName,
            String streetAddress,
            String city,
            Instant scheduledFor
    ) {}
}
