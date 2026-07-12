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

    /**
     * The single most recent visit in a given status for a subscriber. Used by the Health
     * Score rubric to read the latest completed visit's checklist outcomes.
     */
    Optional<Visit> findFirstBySubscriberIdAndStatusOrderByScheduledForDescIdDesc(
            Long subscriberId, VisitStatus status);

    /**
     * The single soonest visit in a given status for a subscriber (ascending order — the
     * opposite direction from the "most recent" query above). Used by
     * {@link VisitQueryService} to resolve "next scheduled visit" for cross-domain display
     * (e.g. the subscription domain's billing page).
     */
    Optional<Visit> findFirstBySubscriberIdAndStatusOrderByScheduledForAscIdAsc(
            Long subscriberId, VisitStatus status);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /** Admin: cursor-paginated visits newest-first. */
    List<Visit> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /** Admin: first page newest-first. */
    List<Visit> findAllByOrderByIdDesc(Pageable pageable);

    /** Admin: status-filtered cursor-paginated visits newest-first. */
    List<Visit> findByStatusAndIdLessThanOrderByIdDesc(VisitStatus status, Long cursor, Pageable pageable);

    /** Admin: status-filtered first page newest-first. */
    List<Visit> findByStatusOrderByIdDesc(VisitStatus status, Pageable pageable);

    /**
     * Count of visits in the given status with {@code scheduledFor} at or after the given
     * instant. Used by the admin dashboard aggregate ("upcoming visits" = SCHEDULED and
     * not yet in the past).
     */
    long countByStatusAndScheduledForGreaterThanEqual(VisitStatus status, java.time.Instant scheduledFor);

    /**
     * Visits in {@code status} whose {@code scheduledFor} falls within {@code [from, to]}.
     * Used by {@link VisitQueryService#findScheduledInWindow} (#89) to find SCHEDULED visits
     * due for the 24h-before reminder.
     */
    List<Visit> findByStatusAndScheduledForBetween(
            VisitStatus status, java.time.Instant from, java.time.Instant to);

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

    // ── Technician day-sheet queries ──────────────────────────────────────────

    /**
     * Returns visits assigned to a technician (by user id) with scheduledFor between
     * {@code dayStart} and {@code dayEnd} (exclusive), ordered by scheduledFor ascending.
     * Used for the technician day sheet ({@code GET /api/tech/visits/today}).
     */
    @Query("SELECT v FROM Visit v WHERE v.technicianId = :technicianUserId " +
           "AND v.scheduledFor >= :dayStart AND v.scheduledFor < :dayEnd " +
           "ORDER BY v.scheduledFor ASC, v.id ASC")
    List<Visit> findByTechnicianIdAndScheduledForBetween(
            @Param("technicianUserId") Long technicianUserId,
            @Param("dayStart") java.time.Instant dayStart,
            @Param("dayEnd") java.time.Instant dayEnd);

    /**
     * Technician ownership check: returns the visit by id and technician user id.
     * Used to enforce assigned-to-this-tech authz → 404 (not 403) per the
     * ownership-failure rule (don't leak existence of another tech's visit).
     */
    Optional<Visit> findByIdAndTechnicianId(Long id, Long technicianUserId);

    /**
     * Returns any SCHEDULED or IN_PROGRESS visit for a subscriber assigned to the
     * given technician. Used for the todo PATCH authz at MVP:
     * "the todo's subscriber has a visit assigned to this tech today (or ongoing)."
     */
    @Query("SELECT v FROM Visit v WHERE v.subscriberId = :subscriberId " +
           "AND v.technicianId = :technicianUserId " +
           "AND v.status IN :statuses")
    List<Visit> findActiveVisitsBySubscriberAndTechnician(
            @Param("subscriberId") Long subscriberId,
            @Param("technicianUserId") Long technicianUserId,
            @Param("statuses") List<VisitStatus> statuses);
}
