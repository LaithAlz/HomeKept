package com.homekept.booking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for walk-through bookings.
 * Cursor-based pagination uses id-descending ordering.
 */
public interface WalkthroughBookingRepository extends JpaRepository<WalkthroughBooking, Long> {

    /**
     * Cursor page: bookings with id &lt; cursor, newest first.
     * Used by the admin pipeline when a cursor (last seen id) is supplied.
     */
    List<WalkthroughBooking> findByStatusAndIdLessThanOrderByIdDesc(
            BookingStatus status, Long cursor, Pageable pageable);

    /**
     * First page: bookings without a cursor filter.
     */
    List<WalkthroughBooking> findByStatusOrderByIdDesc(
            BookingStatus status, Pageable pageable);

    /**
     * All statuses — cursor page.
     */
    List<WalkthroughBooking> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /**
     * All statuses — first page.
     */
    List<WalkthroughBooking> findAllByOrderByIdDesc(Pageable pageable);
}
