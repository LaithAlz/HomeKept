package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;
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
