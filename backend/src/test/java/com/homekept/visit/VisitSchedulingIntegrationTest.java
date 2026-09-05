package com.homekept.visit;

import com.homekept.AbstractIntegrationTest;
import com.homekept.catalog.PlanCode;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VisitSchedulingService#scheduleInitialVisits}.
 * Runs against real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>ESSENTIAL subscriber (base tier): only ESSENTIAL-tier templates scheduled.</li>
 *   <li>COMPLETE subscriber: ESSENTIAL + COMPLETE templates scheduled in the window.</li>
 *   <li>PREMIER subscriber: all three tiers' templates scheduled in the window.</li>
 *   <li>All created visits are SCHEDULED + ROUTINE + in the future.</li>
 *   <li>Each visit has exactly 4 VisitService rows (source=TEMPLATE, the 4 standing items).</li>
 *   <li>Idempotency: second call creates no new visits.</li>
 *   <li>The {@code templateOccurrenceYear}-keyed guard survives an in-place reschedule that
 *       pushes a visit outside the lookahead window (forward or into the past) — the
 *       regression this class exists to pin down after V16/V17.</li>
 *   <li>The legacy (NULL {@code templateOccurrenceYear}) path: V17 does not backfill, so an
 *       untagged, in-window row must still read as "already scheduled" via the guard's
 *       window fallback, must get its occurrence assigned from where it WAS on its first
 *       in-place reschedule, and from that point on must survive being pushed outside the
 *       window exactly like a row that was always tagged.</li>
 *   <li>The month gate on that inference: a legacy row whose current month disagrees with
 *       its template's (evidence it was already moved off its occurrence, e.g. by a pre-V16
 *       reschedule) must NOT get a year inferred — it stays {@code null}.</li>
 *   <li>Write-once: a second in-place reschedule of a row that already had its occurrence
 *       year inferred must never recompute it from wherever the row sits in between.</li>
 * </ul>
 *
 * <p>Expected counts are derived at runtime by calling the same package-local helper
 * {@link VisitSchedulingService#nextOccurrenceInWindow} that the service itself uses,
 * so the test never hardcodes absolute dates.
 */
class VisitSchedulingIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final int STANDING_ITEMS_PER_VISIT = 4;

    @Autowired VisitSchedulingService visitSchedulingService;
    @Autowired VisitAdminService visitAdminService;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired VisitTemplateRepository visitTemplateRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired JdbcTemplate jdbc;

    // ── ESSENTIAL tier (base tier, reinstated by V15) ─────────────────────────

    @Test
    void scheduleInitialVisits_essentialTier_schedulesOnlyEssentialTemplatesInWindow() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-essential@test.local", PlanCode.ESSENTIAL);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        assertThat(visits).hasSize(expectedVisitCount(PlanCode.ESSENTIAL));
        assertThat(visits).allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED);
        assertThat(visits).allMatch(v -> v.getType() == VisitType.ROUTINE);

        java.time.Instant now = java.time.Instant.now();
        assertThat(visits).allMatch(v -> v.getScheduledFor().isAfter(now));

        // Essential is the floor, so it qualifies for its own templates and nothing above.
        List<Long> essentialTemplateIds = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.ESSENTIAL))
                .stream().map(VisitTemplate::getId).toList();
        assertThat(visits).allMatch(v ->
                v.getVisitTemplateId() == null || essentialTemplateIds.contains(v.getVisitTemplateId()));
    }

    @Test
    void scheduleInitialVisits_completeGetsAtLeastAsManyVisitsAsEssential() {
        assertThat(expectedVisitCount(PlanCode.COMPLETE))
                .isGreaterThanOrEqualTo(expectedVisitCount(PlanCode.ESSENTIAL));
    }

    // ── COMPLETE tier ─────────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_completeTier_schedulesEssentialAndCompleteTemplatesInWindow() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-complete@test.local", PlanCode.COMPLETE);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        int expectedCount = expectedVisitCount(PlanCode.COMPLETE);
        assertThat(visits).hasSize(expectedCount);

        // Every created visit must be SCHEDULED and ROUTINE.
        assertThat(visits).allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED);
        assertThat(visits).allMatch(v -> v.getType() == VisitType.ROUTINE);

        // Every scheduled_for must be in the future.
        java.time.Instant now = java.time.Instant.now();
        assertThat(visits).allMatch(v -> v.getScheduledFor().isAfter(now));

        // Every visit's template must be one Complete actually qualifies for. min_tier is a
        // floor, so that is ESSENTIAL's 4 seasonal anchors plus COMPLETE's own 4, not
        // COMPLETE-min templates alone. Asking eligibleTiersFor rather than hardcoding the
        // list keeps this test honest if the tier ladder changes again.
        List<Long> eligibleTemplateIds = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.COMPLETE))
                .stream().map(VisitTemplate::getId).toList();
        assertThat(visits).allMatch(v ->
                v.getVisitTemplateId() == null || eligibleTemplateIds.contains(v.getVisitTemplateId()));
    }

    @Test
    void scheduleInitialVisits_completeTier_eachVisitHasFourStandingItems() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-complete-services@test.local", PlanCode.COMPLETE);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        assertThat(visits).isNotEmpty();
        for (Visit visit : visits) {
            List<VisitService> services = visitServiceRepository.findByVisitIdOrderByIdAsc(visit.getId());
            assertThat(services)
                    .as("visit %d should have %d standing items", visit.getId(), STANDING_ITEMS_PER_VISIT)
                    .hasSize(STANDING_ITEMS_PER_VISIT);
            assertThat(services).allMatch(vs -> vs.getSource() == VisitServiceSource.TEMPLATE);
        }
    }

    // ── PREMIER tier ──────────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_premierTier_schedulesBothTierTemplatesInWindow() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-premier@test.local", PlanCode.PREMIER);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        int expectedCount = expectedVisitCount(PlanCode.PREMIER);
        assertThat(visits).hasSize(expectedCount);

        assertThat(visits).allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED);
        assertThat(visits).allMatch(v -> v.getType() == VisitType.ROUTINE);

        java.time.Instant now = java.time.Instant.now();
        assertThat(visits).allMatch(v -> v.getScheduledFor().isAfter(now));

        // Premier gets every tier's templates: ESSENTIAL + COMPLETE + PREMIER.
        List<Long> allTemplateIds = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER))
                .stream().map(VisitTemplate::getId).toList();
        assertThat(visits).allMatch(v ->
                v.getVisitTemplateId() == null || allTemplateIds.contains(v.getVisitTemplateId()));
    }

    @Test
    void scheduleInitialVisits_premierTier_eachVisitHasFourStandingItems() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-premier-services@test.local", PlanCode.PREMIER);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        assertThat(visits).isNotEmpty();
        for (Visit visit : visits) {
            List<VisitService> services = visitServiceRepository.findByVisitIdOrderByIdAsc(visit.getId());
            assertThat(services)
                    .as("visit %d should have %d standing items", visit.getId(), STANDING_ITEMS_PER_VISIT)
                    .hasSize(STANDING_ITEMS_PER_VISIT);
            assertThat(services).allMatch(vs -> vs.getSource() == VisitServiceSource.TEMPLATE);
        }
    }

    // ── PREMIER gets at least as many visits as COMPLETE ──────────────────────

    @Test
    void scheduleInitialVisits_premierGetsAtLeastAsManyVisitsAsComplete() {
        int completeCount = expectedVisitCount(PlanCode.COMPLETE);
        int premierCount  = expectedVisitCount(PlanCode.PREMIER);

        // Premier should have at least as many visits as Complete (cumulative calendar).
        assertThat(premierCount).isGreaterThanOrEqualTo(completeCount);
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_idempotency_secondCallCreatesNoNewVisits() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-idempotent@test.local", PlanCode.COMPLETE);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        long countAfterFirst = countVisitsForSubscriber(subscriber.getId());
        assertThat(countAfterFirst).isGreaterThan(0);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        long countAfterSecond = countVisitsForSubscriber(subscriber.getId());

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void scheduleInitialVisits_idempotency_allVisitsRemainScheduled() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-idempotent-status@test.local", PlanCode.COMPLETE);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        visitSchedulingService.scheduleInitialVisits(subscriber); // second call — must no-op

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        assertThat(visits).isNotEmpty();
        assertThat(visits).allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED);
    }

    // ── Per-template top-up (not a blanket "any visit exists" guard) ───────────

    /**
     * Proves the idempotency guard is per-template, not per-subscriber: pre-seeding a visit
     * for exactly one in-window template must not block the other in-window templates from
     * being scheduled. This is what makes {@link VisitTopUpScheduler}'s daily re-run useful —
     * a blanket "any visit exists" guard would make every subsequent call a total no-op (see
     * {@link VisitSchedulingService} class Javadoc "Idempotency").
     *
     * <p>Uses PREMIER (the cumulative union of all 12 monthly templates — every calendar
     * month has exactly one) so the 4-month window always contains multiple templates
     * regardless of which day the test happens to run.
     */
    @Test
    void scheduleInitialVisits_existingVisitForOneTemplate_stillSchedulesOtherTemplatesInWindow() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-topup@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        // Every calendar month has exactly one template, so a 4-month window always spans
        // at least a few of them.
        assertThat(inWindow.size()).isGreaterThanOrEqualTo(2);

        // Pre-seed a visit for the FIRST in-window template only — simulates "this template's
        // visit was already scheduled by a previous run," without going through the service.
        // templateOccurrenceYear is set explicitly to what a real scheduling run would have
        // recorded (the candidate date's year) — the guard is now keyed on that column, not
        // on scheduledFor falling inside the window, so a fixture that omits it would no
        // longer be recognized as "already scheduled" and this test would (correctly) start
        // failing under the new guard.
        VisitTemplate preScheduled = inWindow.get(0);
        LocalDate preScheduledCandidate =
                VisitSchedulingService.nextOccurrenceInWindow(preScheduled.getMonth(), today, windowEnd, TORONTO);
        Visit preScheduledVisit = new Visit(
                subscriber.getId(), subscriber.getPropertyId(), preScheduled.getId(),
                Instant.now().plus(Duration.ofDays(30)),
                120, VisitType.ROUTINE);
        preScheduledVisit.setTemplateOccurrenceYear(preScheduledCandidate.getYear());
        visitRepository.save(preScheduledVisit);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        // Exactly one visit for the pre-seeded template — no duplicate created.
        assertThat(visits.stream().filter(v -> preScheduled.getId().equals(v.getVisitTemplateId())).count())
                .isEqualTo(1);
        // Every other in-window template now has a visit too — the "top up" actually happened.
        assertThat(visits).hasSize(inWindow.size());
    }

    // ── Annual recurrence (year-boundary) ───────────────────────────────────────

    /**
     * Visit templates recur annually (one row per month, reused every year). A visit from a
     * <em>prior year's</em> occurrence of a template must not block this year's occurrence
     * once it enters the current window — the idempotency guard is per-occurrence, not
     * "has this subscriber ever had a visit for this template" (see
     * {@link VisitSchedulingService} class Javadoc "Idempotency"). An unbounded guard would
     * permanently cap a subscriber at one lifetime visit per template instead of a fresh one
     * every year.
     */
    @Test
    void scheduleInitialVisits_priorYearVisitForTemplate_stillSchedulesCurrentYearOccurrence() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-annual@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();

        VisitTemplate template = inWindow.get(0);
        int currentOccurrenceYear =
                VisitSchedulingService.nextOccurrenceInWindow(template.getMonth(), today, windowEnd, TORONTO).getYear();

        // Pre-seed a visit for this same template dated a full year in the past, tagged with
        // LAST year's occurrence — simulates last year's occurrence, explicitly distinct from
        // the occurrence the guard will look up this run (currentOccurrenceYear). Setting this
        // explicitly (rather than leaving it null) is what makes this a real test of
        // year-scoping rather than an accident of NULL never matching an equality lookup.
        Visit priorYearVisit = new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                Instant.now().minus(Duration.ofDays(365)),
                120, VisitType.ROUTINE);
        priorYearVisit.setTemplateOccurrenceYear(currentOccurrenceYear - 1);
        visitRepository.save(priorYearVisit);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> templateVisits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .filter(v -> template.getId().equals(v.getVisitTemplateId()))
                .toList();

        // The prior-year visit is untouched, and a fresh, future-dated visit for the same
        // template was scheduled inside the current window.
        assertThat(templateVisits).hasSize(2);
        java.time.Instant now = java.time.Instant.now();
        assertThat(templateVisits.stream().filter(v -> v.getScheduledFor().isAfter(now)).count())
                .as("a fresh visit for this template must exist in the current window")
                .isEqualTo(1);
    }

    // ── In-place reschedule must not defeat the guard (V16/V17 regression) ───────

    /**
     * The bug this class exists to pin down: reschedule used to leave the original visit row
     * in place and add a replacement, so the guard (then keyed on {@code scheduledFor} falling
     * inside the window) always found a row in-window. Once reschedule started moving the
     * single row in place (V16), pushing a visit outside the lookahead window made the old
     * guard go false and the next top-up run created a duplicate for the same occurrence —
     * the customer double-booked. Exercises the REAL reschedule path
     * ({@link VisitAdminService#rescheduleVisit}), not a raw repository write, so this would
     * have failed against the pre-V17 guard.
     */
    @Test
    void topUp_rescheduledVisitPushedOutsideWindow_doesNotDuplicateTemplate() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-reschedule-forward@test.local", PlanCode.PREMIER);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        List<Visit> initial = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();
        assertThat(initial).isNotEmpty();
        Visit target = initial.get(0);
        Long templateId = target.getVisitTemplateId();
        assertThat(templateId).isNotNull();

        // Customer says "push it out" — reschedule it well past the 4-month lookahead window.
        visitAdminService.rescheduleVisit(
                target.getId(), Instant.now().plus(Duration.ofDays(400)), null, VisitEventSource.ADMIN);

        // The nightly top-up run: under the old scheduledFor-window guard, this visit is now
        // outside the window and the guard would go false, creating a duplicate.
        visitSchedulingService.scheduleInitialVisits(subscriber);

        long countForTemplate = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .filter(v -> templateId.equals(v.getVisitTemplateId()))
                .count();
        assertThat(countForTemplate)
                .as("rescheduling a visit outside the lookahead window must not duplicate its template's occurrence")
                .isEqualTo(1);
    }

    /**
     * Same regression, past-date direction: the founder's report noted "a reschedule to a
     * past date does the same thing" — a visit moved behind {@code today} also falls outside
     * {@code [today, windowEnd)} under the old guard.
     */
    @Test
    void topUp_rescheduledVisitMovedToPastDate_doesNotDuplicateTemplate() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-reschedule-past@test.local", PlanCode.PREMIER);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        List<Visit> initial = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();
        assertThat(initial).isNotEmpty();
        Visit target = initial.get(0);
        Long templateId = target.getVisitTemplateId();
        assertThat(templateId).isNotNull();

        // Called on the service directly (bypassing the controller's @Future validation,
        // which only guards the PATCH endpoint) to isolate exactly the guard behaviour this
        // regression is about — a past scheduledFor is also outside [today, windowEnd).
        visitAdminService.rescheduleVisit(
                target.getId(), Instant.now().minus(Duration.ofDays(30)), null, VisitEventSource.ADMIN);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        long countForTemplate = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .filter(v -> templateId.equals(v.getVisitTemplateId()))
                .count();
        assertThat(countForTemplate)
                .as("rescheduling a visit to a past date must not duplicate its template's occurrence")
                .isEqualTo(1);
    }

    /**
     * Proves the fix does not silently reintroduce the bug the founder explicitly rejected as
     * an alternative: widening the guard's window would make a visit moved far forward
     * suppress NEXT year's occurrence of the same template, costing the customer a visit they
     * paid for. Since the guard is keyed on {@code templateOccurrenceYear} (never written by
     * reschedule), a visit moved arbitrarily far forward keeps its OWN occurrence year and can
     * never satisfy a lookup for a different one.
     */
    @Test
    void topUp_visitRescheduledFarForward_nextYearsOccurrenceGuardStaysOpen() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-reschedule-nextyear@test.local", PlanCode.PREMIER);

        visitSchedulingService.scheduleInitialVisits(subscriber);
        List<Visit> initial = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();
        assertThat(initial).isNotEmpty();
        Visit target = initial.get(0);
        Long templateId = target.getVisitTemplateId();
        Integer occurrenceYear = target.getTemplateOccurrenceYear();
        assertThat(templateId).isNotNull();
        assertThat(occurrenceYear).isNotNull();

        visitAdminService.rescheduleVisit(
                target.getId(), Instant.now().plus(Duration.ofDays(400)), null, VisitEventSource.ADMIN);

        Visit reloaded = visitRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getTemplateOccurrenceYear())
                .as("reschedule must never change which occurrence a visit is recorded as")
                .isEqualTo(occurrenceYear);

        // Window bounds are irrelevant here — this visit already has a non-null occurrence
        // year, so the guard's year-match branch decides the outcome regardless of what these
        // are (they only matter for the NULL-year legacy fallback, exercised separately
        // below). Computed for realism anyway, matching how the service derives them.
        LocalDate today = LocalDate.now(TORONTO);
        Instant windowStart = today.atStartOfDay(TORONTO).toInstant();
        Instant windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS).atStartOfDay(TORONTO).toInstant();

        // Next year's occurrence guard is untouched by the move: the far-forward visit is
        // still tagged with THIS year's occurrence, so it cannot shadow next year's.
        assertThat(visitRepository.existsAlreadyScheduledForOccurrence(
                subscriber.getId(), templateId, occurrenceYear + 1, windowStart, windowEnd))
                .as("a visit moved far forward must not appear to satisfy next year's occurrence")
                .isFalse();
        // And this year's occurrence is still correctly recognized as already scheduled.
        assertThat(visitRepository.existsAlreadyScheduledForOccurrence(
                subscriber.getId(), templateId, occurrenceYear, windowStart, windowEnd))
                .isTrue();
    }

    // ── Legacy (NULL templateOccurrenceYear) rows — the V17 no-backfill fallback ─

    /**
     * V17 does not backfill: every visit that existed before the column shipped has
     * {@code templateOccurrenceYear == null}, and stays that way until its first in-place
     * reschedule. The guard's fallback branch must treat such a row, sitting inside the
     * current lookahead window, as "already scheduled" — the pre-V16 rule, which is correct
     * for a row that (by definition of being untagged) has never been moved in place. Without
     * this fallback every legacy row would be invisible to the guard and get duplicated on
     * the very next top-up run: the original bug, now aimed at existing customers instead of
     * new ones.
     */
    @Test
    void scheduleInitialVisits_legacyNullOccurrenceVisitInWindow_isNotDuplicated() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-legacy-null@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();

        VisitTemplate template = inWindow.get(0);

        // A legacy row: templated, in-window, but no occurrence year recorded — exactly what
        // every visit looked like before V17, and what an untouched one looks like today.
        Visit legacyVisit = visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                Instant.now().plus(Duration.ofDays(30)),
                120, VisitType.ROUTINE));
        assertThat(legacyVisit.getTemplateOccurrenceYear()).isNull();

        visitSchedulingService.scheduleInitialVisits(subscriber);

        long countForTemplate = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .filter(v -> template.getId().equals(v.getVisitTemplateId()))
                .count();
        assertThat(countForTemplate)
                .as("a legacy in-window visit with no recorded occurrence year must not be duplicated")
                .isEqualTo(1);
    }

    /**
     * The full legacy lifecycle, end to end — the path the reviewer flagged as the one that
     * matters most. A NULL-year row gets its occurrence assigned lazily, exactly once, on its
     * first in-place reschedule, computed from where {@code scheduledFor} sits at that moment
     * (the last instant that's still guaranteed to be the true occurrence) — NOT from where
     * the reschedule moves it to. From that point on it behaves exactly like a row that was
     * always tagged: the top-up guard survives it being pushed outside the lookahead window.
     */
    @Test
    void topUp_legacyVisitRescheduledOutsideWindow_assignsOccurrenceFromOldDate_doesNotDuplicate() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-legacy-reschedule@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();
        VisitTemplate template = inWindow.get(0);

        // Derived from the CHOSEN template's own candidate date, not an arbitrary "now + 30
        // days" — inWindow is ordered by month ascending (findByMinTierIn), so once the
        // window wraps into January, get(0) can be a January-2027 candidate while an
        // unrelated "+30 days" anchor is still 2026. A mismatch here would fail this test on
        // exactly the calendar days it wraps a year boundary — this must always agree with
        // the template actually under test.
        LocalDate originalCandidateDate =
                VisitSchedulingService.nextOccurrenceInWindow(template.getMonth(), today, windowEnd, TORONTO);
        Instant originalScheduledFor = originalCandidateDate.atTime(12, 0).atZone(TORONTO).toInstant();
        int expectedOccurrenceYear = originalCandidateDate.getYear();

        Visit legacyVisit = visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                originalScheduledFor, 120, VisitType.ROUTINE));
        assertThat(legacyVisit.getTemplateOccurrenceYear()).isNull();

        // Push it far outside the window — same "customer asks to move it" scenario as the
        // non-legacy regression tests above, but starting from a NULL occurrence year.
        Instant farFuture = Instant.now().plus(Duration.ofDays(400));
        visitAdminService.rescheduleVisit(legacyVisit.getId(), farFuture, null, VisitEventSource.ADMIN);

        Visit reloaded = visitRepository.findById(legacyVisit.getId()).orElseThrow();
        assertThat(reloaded.getScheduledFor()).isEqualTo(farFuture);
        // Assigned from where it WAS (originalScheduledFor's year) — never from where the
        // reschedule moved it to.
        assertThat(reloaded.getTemplateOccurrenceYear())
                .as("a legacy row's occurrence year must be inferred from where it was, not where it moved to")
                .isEqualTo(expectedOccurrenceYear);

        // Nightly top-up: the now-year-tagged visit must not be duplicated even though it
        // currently sits outside the window.
        visitSchedulingService.scheduleInitialVisits(subscriber);

        long countForTemplate = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .filter(v -> template.getId().equals(v.getVisitTemplateId()))
                .count();
        assertThat(countForTemplate)
                .as("a legacy visit, once rescheduled and assigned an occurrence year, must not be "
                        + "duplicated after moving outside the window")
                .isEqualTo(1);
    }

    /**
     * Pins the write-once invariant the whole legacy design rests on: a SECOND in-place
     * reschedule of a row that already had its occurrence year inferred on the FIRST must not
     * recompute it from wherever the row happens to sit in between. Without this, a legacy
     * row could get a correct year on reschedule #1 and a wrong one silently overwriting it on
     * reschedule #2 — the exact class of bug the month gate exists to prevent, but from a
     * different angle (an already-assigned value, not an unverifiable NULL one).
     */
    @Test
    void rescheduleVisit_legacyRowRescheduledTwice_occurrenceYearNotRecomputedOnSecondReschedule() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-legacy-twice@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();
        VisitTemplate template = inWindow.get(0);

        LocalDate originalCandidateDate =
                VisitSchedulingService.nextOccurrenceInWindow(template.getMonth(), today, windowEnd, TORONTO);
        Instant originalScheduledFor = originalCandidateDate.atTime(12, 0).atZone(TORONTO).toInstant();
        int expectedOccurrenceYear = originalCandidateDate.getYear();

        Visit legacyVisit = visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                originalScheduledFor, 120, VisitType.ROUTINE));
        assertThat(legacyVisit.getTemplateOccurrenceYear()).isNull();

        // First reschedule: the current month still matches the template's, so the year is
        // inferred from the ORIGINAL date and recorded.
        Instant firstMove = Instant.now().plus(Duration.ofDays(400));
        visitAdminService.rescheduleVisit(legacyVisit.getId(), firstMove, null, VisitEventSource.ADMIN);
        Visit afterFirst = visitRepository.findById(legacyVisit.getId()).orElseThrow();
        assertThat(afterFirst.getTemplateOccurrenceYear()).isEqualTo(expectedOccurrenceYear);

        // Second reschedule: the visit now sits at firstMove — a different month (and likely a
        // different year) than its true occurrence. If the write-once rule were violated, this
        // would recompute the year from firstMove's date instead of leaving the
        // already-recorded value alone.
        Instant secondMove = Instant.now().plus(Duration.ofDays(20));
        visitAdminService.rescheduleVisit(legacyVisit.getId(), secondMove, null, VisitEventSource.ADMIN);
        Visit afterSecond = visitRepository.findById(legacyVisit.getId()).orElseThrow();

        assertThat(afterSecond.getScheduledFor()).isEqualTo(secondMove);
        assertThat(afterSecond.getTemplateOccurrenceYear())
                .as("a second reschedule must never recompute an already-inferred occurrence year")
                .isEqualTo(expectedOccurrenceYear);
    }

    /**
     * The month gate itself: a legacy row whose current {@code scheduledFor} month does NOT
     * match its template's month is exactly what a pre-V16-moved visit looks like — evidence
     * the row is already off its true occurrence, so its date is not trustworthy evidence of
     * which year it belongs to. Rescheduling such a row must NOT infer a year from it; the
     * occurrence stays {@code null} and the window fallback keeps deciding for it instead.
     */
    @Test
    void rescheduleVisit_legacyRowMonthMismatch_occurrenceYearStaysNull() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-legacy-mismatch@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(VisitSchedulingService.eligibleTiersFor(PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();
        VisitTemplate template = inWindow.get(0);

        // A date two years out, in a month guaranteed to differ from the template's own —
        // simulates a pre-V16 reschedule that moved this visit off its occurrence long before
        // this column (or the month gate) existed. Constructed from a fixed month offset
        // rather than any "now + N days" arithmetic, so this is not calendar-dependent.
        int differentMonth = (template.getMonth() % 12) + 1;
        Instant offMonthDate = LocalDate.of(today.getYear() + 2, differentMonth, 10)
                .atTime(12, 0).atZone(TORONTO).toInstant();

        Visit legacyVisit = visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                offMonthDate, 120, VisitType.ROUTINE));
        assertThat(legacyVisit.getTemplateOccurrenceYear()).isNull();

        visitAdminService.rescheduleVisit(
                legacyVisit.getId(), Instant.now().plus(Duration.ofDays(500)), null, VisitEventSource.ADMIN);

        Visit reloaded = visitRepository.findById(legacyVisit.getId()).orElseThrow();
        assertThat(reloaded.getTemplateOccurrenceYear())
                .as("a month mismatch means the row is already off its occurrence; the year must not be inferred")
                .isNull();
    }

    // ── No-plan-tier guard ────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_noPlanTier_createsNoVisits() {
        Subscriber subscriber = seedActiveSubscriberNoPlan("scheduling-no-plan@test.local");

        visitSchedulingService.scheduleInitialVisits(subscriber);

        long count = countVisitsForSubscriber(subscriber.getId());
        assertThat(count).isZero();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Computes the expected number of visits for the given tier by reusing the
     * service's own static window logic — no hardcoded dates.
     */
    private int expectedVisitCount(PlanCode tier) {
        List<PlanCode> eligibleTiers = VisitSchedulingService.eligibleTiersFor(tier);
        List<VisitTemplate> templates = visitTemplateRepository.findByMinTierIn(eligibleTiers);

        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);

        int count = 0;
        for (VisitTemplate t : templates) {
            if (VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null) {
                count++;
            }
        }
        return count;
    }

    private long countVisitsForSubscriber(Long subscriberId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit WHERE subscriber_id = ?", Long.class, subscriberId);
        return count != null ? count : 0L;
    }

    /**
     * Seeds an ACTIVE subscriber with the given plan tier code. The plan tier id is
     * resolved from the seeded catalog (V2__catalog.sql) by code.
     */
    private Subscriber seedActiveSubscriber(String email, PlanCode planCode) {
        long nano = System.nanoTime();

        User user = userRepository.save(new User(
                email + "." + nano,
                passwordEncoder.encode("placeholder"),
                "Test", "Scheduling",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Scheduling St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        Long planTierId = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, planCode.name());
        if (planTierId == null) {
            throw new IllegalStateException("Plan tier not seeded for code: " + planCode);
        }

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        sub.setPlanTierId(planTierId);
        sub = subscriberRepository.save(sub);
        return sub;
    }

    /**
     * Seeds an ACTIVE subscriber with no plan tier id set (null) to test the guard.
     */
    private Subscriber seedActiveSubscriberNoPlan(String email) {
        long nano = System.nanoTime();

        User user = userRepository.save(new User(
                email + "." + nano,
                passwordEncoder.encode("placeholder"),
                "Test", "NoPlan",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " NoPlan St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        // planTierId intentionally left null
        sub = subscriberRepository.save(sub);
        return sub;
    }
}
