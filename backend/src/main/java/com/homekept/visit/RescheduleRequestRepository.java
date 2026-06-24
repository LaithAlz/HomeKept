package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link RescheduleRequest}.
 */
public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {

    /** Ownership-scoped lookup for the customer (returns empty if not owned → 404). */
    Optional<RescheduleRequest> findByIdAndSubscriberId(Long id, Long subscriberId);

    /** Admin queue: requests in the given status, oldest first. */
    List<RescheduleRequest> findByStatusOrderByIdAsc(RescheduleRequestStatus status);
}
