package com.homekept.visit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
     * Returns true if the subscriber already has a visit (any status) tied to this specific
     * template that counts as "already scheduled" for {@code occurrenceYear}. Used by
     * {@link VisitSchedulingService} as a per-template, per-occurrence scheduling guard: a
     * template whose occurrence for the target year already has a visit is skipped, while
     * other eligible templates newly in the window are still scheduled.
     *
     * <p>An explicit {@code @Query} rather than a derived one because the match is two
     * branches, not one, combined with OR:
     * <ul>
     *   <li>{@code templateOccurrenceYear = :occurrenceYear} — the V17 rule. A visit's
     *       occurrence year is set once, at creation, and never moved by a reschedule (see
     *       {@code Visit#templateOccurrenceYear} and {@code VisitAdminService#rescheduleInternal}),
     *       so this branch is correct regardless of where the visit currently sits on the
     *       calendar.</li>
     *   <li>{@code templateOccurrenceYear IS NULL AND scheduledFor BETWEEN :windowStart AND
     *       :windowEnd} — the LEGACY fallback, not an accident. V17 does not backfill: a
     *       migration cannot verify that no pre-V16 reschedule ever landed a replacement
     *       visit's {@code scheduledFor} in a different calendar year than the one it
     *       actually occupied (pre-V16, reschedule created a new row carrying the same
     *       template id at a new date — nothing constrained that date to the same year), so
     *       guessing a year from {@code scheduledFor} at backfill time could stamp the WRONG
     *       year and silently suppress a real future occurrence forever. A {@code NULL}-year
     *       row is either a row from before this column existed, or a legacy row that has not
     *       yet been rescheduled in place since V16/V17 shipped (a legacy row gets its year
     *       assigned lazily, from where it sits at that moment, on its first in-place
     *       reschedule — see {@code VisitAdminService#rescheduleInternal}). Either way it has
     *       never had {@code scheduledFor} moved in place, so the pre-V16 rule — "already
     *       scheduled" means {@code scheduledFor} falls in the current lookahead window — is
     *       still exactly its actual behaviour today. This branch is strictly MORE
     *       conservative than the year match (window membership is a narrower condition than
     *       "ever," and it only applies at all when there is no year to match against), so it
     *       can never reintroduce the bug V17 fixes for a row that already has a year
     *       recorded, while it prevents that same bug landing on legacy rows that don't yet.
     *       "More conservative" cuts only one way, though, and it's worth naming: this branch
     *       makes the guard MORE likely to say "already scheduled" than the year rule alone
     *       would, i.e. it creates FEWER visits, never more. So if this branch is ever wrong,
     *       the failure it produces is a MISSING visit the customer already paid for — never
     *       a duplicate. That is the acceptable direction to fail in for a legacy row we can't
     *       fully verify, but it is a real failure mode, not a free lunch, and it is the
     *       opposite failure mode from the one V17 exists to fix.
     * </ul>
     *
     * <p>This is what makes both webhook replay (the activation listener) and the recurring
     * {@link VisitTopUpScheduler} top-up job safe to call repeatedly without duplicating
     * visits, while still scheduling each template's fresh occurrence every year: next
     * year's occurrence has a different {@code occurrenceYear} and falls outside this year's
     * window, so it is never shadowed by this year's (possibly since-moved) visit.
     *
     * @param subscriberId    the subscriber
     * @param visitTemplateId the template
     * @param occurrenceYear  the occurrence year being scheduled this run (never null — the
     *                        caller always has a concrete candidate year by this point)
     * @param windowStart     inclusive lower bound of the current lookahead window, used only
     *                        by the legacy (NULL-year) fallback branch
     * @param windowEnd       inclusive-in-SQL upper bound of the current lookahead window
     *                        (see {@link VisitSchedulingService#scheduleInitialVisits} for how
     *                        it's derived — visits are always placed after local midnight, so
     *                        {@code BETWEEN}'s inclusive end is not reachable in practice),
     *                        used only by the legacy fallback branch
     */
    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM Visit v "
            + "WHERE v.subscriberId = :subscriberId AND v.visitTemplateId = :visitTemplateId "
            + "AND (v.templateOccurrenceYear = :occurrenceYear "
            + "     OR (v.templateOccurrenceYear IS NULL AND v.scheduledFor BETWEEN :windowStart AND :windowEnd))")
    boolean existsAlreadyScheduledForOccurrence(
            @Param("subscriberId") Long subscriberId,
            @Param("visitTemplateId") Long visitTemplateId,
            @Param("occurrenceYear") int occurrenceYear,
            @Param("windowStart") java.time.Instant windowStart,
            @Param("windowEnd") java.time.Instant windowEnd);

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

    // ── Admin Routes month-sidebar aggregate ──────────────────────────────────

    /**
     * Projection for {@link #findScheduledDayLoad}: one row per local calendar day with at
     * least one SCHEDULED visit, carrying the day's total visit count and how many of those
     * have no technician assigned.
     */
    interface VisitDayLoadRow {
        LocalDate getDay();
        Long getTotal();
        Long getUnassigned();
    }

    /**
     * Aggregates SCHEDULED visits by local calendar day within {@code [from, to)}, in one
     * grouped query — never by loading rows and grouping in application code. Backs
     * {@code GET /api/admin/visits/day-load} (the admin Routes month sidebar): honest
     * visit/unassigned counts only, ascending by day, omitting days with zero SCHEDULED
     * visits.
     *
     * <p>{@code zone} is the IANA zone id (the same {@code renderZoneId} bean
     * {@link com.homekept.visit.VisitSchedulingService} uses — never a hardcoded literal);
     * grouping on {@code (scheduled_for AT TIME ZONE :zone)::date} converts the stored UTC
     * instant to that zone's local wall-clock date before grouping, so a visit at 11pm UTC
     * still lands on the correct Toronto-local day.
     *
     * @param zone the IANA zone id, e.g. {@code "America/Toronto"}
     * @param from inclusive lower bound — the UTC instant of local midnight on the requested
     *             "from" day
     * @param to   exclusive upper bound — the UTC instant of local midnight the day after the
     *             requested "to" day
     */
    @Query(value = """
            SELECT (v.scheduled_for AT TIME ZONE :zone)::date AS day,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE v.technician_id IS NULL) AS unassigned
            FROM visit v
            WHERE v.status = 'SCHEDULED'
              AND v.scheduled_for >= :from
              AND v.scheduled_for < :to
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<VisitDayLoadRow> findScheduledDayLoad(
            @Param("zone") String zone,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);
}
