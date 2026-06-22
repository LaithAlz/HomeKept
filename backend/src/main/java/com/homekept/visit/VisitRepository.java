package com.homekept.visit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Visit}.
 */
public interface VisitRepository extends JpaRepository<Visit, Long> {

    // ── App (customer-facing) cursor-paginated queries ────────────────────────

    /**
     * Cursor page: visits for a subscriber ordered by scheduledFor descending (soonest first
     * in the upcoming direction = largest instant first, matches "newest/soonest" per contract).
     * Visits with id less than cursor (exclusive upper bound for cursor pagination on id).
     */
    @Query("SELECT v FROM Visit v WHERE v.subscriberId = :subscriberId AND v.id < :cursor " +
           "ORDER BY v.scheduledFor DESC, v.id DESC")
    List<Visit> findBySubscriberIdAndIdLessThanOrderByScheduledForDescIdDesc(
            @Param("subscriberId") Long subscriberId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * First page (no cursor) for a subscriber ordered by scheduledFor descending.
     */
    @Query("SELECT v FROM Visit v WHERE v.subscriberId = :subscriberId " +
           "ORDER BY v.scheduledFor DESC, v.id DESC")
    List<Visit> findBySubscriberIdOrderByScheduledForDescIdDesc(
            @Param("subscriberId") Long subscriberId,
            Pageable pageable);

    /**
     * Status-filtered cursor page for a subscriber.
     */
    @Query("SELECT v FROM Visit v WHERE v.subscriberId = :subscriberId AND v.status = :status " +
           "AND v.id < :cursor ORDER BY v.scheduledFor DESC, v.id DESC")
    List<Visit> findBySubscriberIdAndStatusAndIdLessThanOrderByScheduledForDescIdDesc(
            @Param("subscriberId") Long subscriberId,
            @Param("status") VisitStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * Status-filtered first page (no cursor) for a subscriber.
     */
    @Query("SELECT v FROM Visit v WHERE v.subscriberId = :subscriberId AND v.status = :status " +
           "ORDER BY v.scheduledFor DESC, v.id DESC")
    List<Visit> findBySubscriberIdAndStatusOrderByScheduledForDescIdDesc(
            @Param("subscriberId") Long subscriberId,
            @Param("status") VisitStatus status,
            Pageable pageable);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /** Admin: cursor-paginated visits newest-first. */
    List<Visit> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /** Admin: first page newest-first. */
    List<Visit> findAllByOrderByIdDesc(Pageable pageable);

    // ── Scheduling idempotency guard ──────────────────────────────────────────

    /**
     * Returns true if the subscriber already has at least one SCHEDULED or IN_PROGRESS
     * visit. Used by {@link VisitSchedulingService} to skip duplicate scheduling.
     */
    boolean existsBySubscriberIdAndStatusIn(Long subscriberId, List<VisitStatus> statuses);

    /**
     * Ownership check: returns the visit by id and subscriber id.
     * Used to enforce ownership → 404 (not 403) for customer-facing endpoints.
     */
    Optional<Visit> findByIdAndSubscriberId(Long id, Long subscriberId);
}
