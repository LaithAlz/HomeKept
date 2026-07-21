package com.homekept.visit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link RescheduleRequest}.
 */
public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {

    /** Ownership-scoped lookup for the customer (returns empty if not owned → 404). */
    Optional<RescheduleRequest> findByIdAndSubscriberId(Long id, Long subscriberId);

    /**
     * Pessimistic-write-locked lookup by id, used by the admin confirm/decline path to
     * serialise concurrent resolutions of the SAME request (e.g. a double-clicked confirm, or
     * two admins). The second caller blocks until the first commits, then re-reads the
     * now-resolved status and is rejected — so a request can never be confirmed twice, which
     * would otherwise create two replacement visits (one orphaned).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RescheduleRequest r where r.id = :id")
    Optional<RescheduleRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * The request for a visit in the given status (e.g. the PENDING one, if any). The visit's
     * ownership must already be resolved by the caller — this is scoped by visit id only.
     */
    Optional<RescheduleRequest> findByVisitIdAndStatus(Long visitId, RescheduleRequestStatus status);

    /** Admin queue: requests in the given status, oldest first. */
    List<RescheduleRequest> findByStatusOrderByIdAsc(RescheduleRequestStatus status);

    /** Whether the given visit has a request in the given status (e.g. a PENDING one). */
    boolean existsByVisitIdAndStatus(Long visitId, RescheduleRequestStatus status);

    /**
     * Batch lookup: which of the given visit ids have a request in the given status (e.g.
     * PENDING). Selects only the {@code visit_id} column — never loads full rows — so a page
     * of visits can be checked in a single query instead of one {@code exists} query per row.
     */
    @Query("select r.visitId from RescheduleRequest r where r.status = :status and r.visitId in :visitIds")
    List<Long> findVisitIdByStatusAndVisitIdIn(@Param("status") RescheduleRequestStatus status,
                                                @Param("visitIds") Collection<Long> visitIds);
}
