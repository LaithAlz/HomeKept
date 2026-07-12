package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link RescheduleRequestSlot}.
 */
public interface RescheduleRequestSlotRepository extends JpaRepository<RescheduleRequestSlot, Long> {

    /** The proposed slots for a request, earliest first. */
    List<RescheduleRequestSlot> findByRescheduleRequestIdOrderByPreferredSlotAsc(Long rescheduleRequestId);

    /** Deletes all slot rows for a request (used when cancelling a PENDING request). */
    void deleteByRescheduleRequestId(Long rescheduleRequestId);
}
