package com.homekept.visit;

import com.homekept.TestcontainersConfiguration;
import com.homekept.catalog.PlanCode;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VisitSchedulingService#scheduleInitialVisits}.
 * Runs against real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>ESSENTIAL subscriber: only ESSENTIAL-tier templates scheduled in the window.</li>
 *   <li>PREMIER subscriber: all three tier templates scheduled in the window.</li>
 *   <li>All created visits are SCHEDULED + ROUTINE + in the future.</li>
 *   <li>Each visit has exactly 4 VisitService rows (source=TEMPLATE, the 4 standing items).</li>
 *   <li>Idempotency: second call creates no new visits.</li>
 * </ul>
 *
 * <p>Expected counts are derived at runtime by calling the same package-local helper
 * {@link VisitSchedulingService#nextOccurrenceInWindow} that the service itself uses,
 * so the test never hardcodes absolute dates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VisitSchedulingIntegrationTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final int STANDING_ITEMS_PER_VISIT = 4;

    @Autowired VisitSchedulingService visitSchedulingService;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired VisitTemplateRepository visitTemplateRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds       = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // visit_service → visit (ON DELETE CASCADE handles visit_service when visit deleted)
        // but visit → subscriber ON DELETE RESTRICT, so delete visits first.
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM visit WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM subscription_event WHERE subscriber_id = ?", subId);
        }
        for (Long subId : createdSubscriberIds) {
            subscriberRepository.deleteById(subId);
        }
        createdSubscriberIds.clear();

        for (Long propId : createdPropertyIds) {
            propertyRepository.deleteById(propId);
        }
        createdPropertyIds.clear();

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    // ── ESSENTIAL tier ────────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_essentialTier_schedulesOnlyEssentialTemplatesInWindow() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-essential@test.local", PlanCode.ESSENTIAL);

        visitSchedulingService.scheduleInitialVisits(subscriber);

        List<Visit> visits = visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriber.getId()))
                .toList();

        int expectedCount = expectedVisitCount(PlanCode.ESSENTIAL);
        assertThat(visits).hasSize(expectedCount);

        // Every created visit must be SCHEDULED and ROUTINE.
        assertThat(visits).allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED);
        assertThat(visits).allMatch(v -> v.getType() == VisitType.ROUTINE);

        // Every scheduled_for must be in the future.
        java.time.Instant now = java.time.Instant.now();
        assertThat(visits).allMatch(v -> v.getScheduledFor().isAfter(now));

        // The visit template ids must all belong to ESSENTIAL-min templates only.
        List<Long> essentialTemplateIds = visitTemplateRepository
                .findByMinTierIn(List.of(PlanCode.ESSENTIAL))
                .stream().map(VisitTemplate::getId).toList();
        assertThat(visits).allMatch(v ->
                v.getVisitTemplateId() == null || essentialTemplateIds.contains(v.getVisitTemplateId()));
    }

    @Test
    void scheduleInitialVisits_essentialTier_eachVisitHasFourStandingItems() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-essential-services@test.local", PlanCode.ESSENTIAL);

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
    void scheduleInitialVisits_premierTier_schedulesAllThreeTierTemplatesInWindow() {
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

        // Premier gets ESSENTIAL + COMPLETE + PREMIER templates.
        List<Long> allTemplateIds = visitTemplateRepository
                .findByMinTierIn(List.of(PlanCode.ESSENTIAL, PlanCode.COMPLETE, PlanCode.PREMIER))
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

    // ── PREMIER gets MORE than ESSENTIAL ─────────────────────────────────────

    @Test
    void scheduleInitialVisits_premierGetsMostVisitsDueToThreeTierCumulative() {
        int essentialCount = expectedVisitCount(PlanCode.ESSENTIAL);
        int premierCount   = expectedVisitCount(PlanCode.PREMIER);

        // Premier should have at least as many visits as Essential (cumulative calendar).
        assertThat(premierCount).isGreaterThanOrEqualTo(essentialCount);
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void scheduleInitialVisits_idempotency_secondCallCreatesNoNewVisits() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-idempotent@test.local", PlanCode.ESSENTIAL);

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
                .findByMinTierIn(List.of(PlanCode.ESSENTIAL, PlanCode.COMPLETE, PlanCode.PREMIER));
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
        VisitTemplate preScheduled = inWindow.get(0);
        visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), preScheduled.getId(),
                java.time.Instant.now().plus(java.time.Duration.ofDays(30)),
                120, VisitType.ROUTINE));

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
     * once it enters the current window — the idempotency guard is scoped to the current
     * window, not "has this subscriber ever had a visit for this template" (see
     * {@link VisitSchedulingService} class Javadoc "Idempotency"). An unbounded guard would
     * permanently cap a subscriber at one lifetime visit per template instead of a fresh one
     * every year.
     */
    @Test
    void scheduleInitialVisits_priorYearVisitForTemplate_stillSchedulesCurrentYearOccurrence() {
        Subscriber subscriber = seedActiveSubscriber("scheduling-annual@test.local", PlanCode.PREMIER);

        List<VisitTemplate> allTemplates = visitTemplateRepository
                .findByMinTierIn(List.of(PlanCode.ESSENTIAL, PlanCode.COMPLETE, PlanCode.PREMIER));
        LocalDate today = LocalDate.now(TORONTO);
        LocalDate windowEnd = today.plusMonths(VisitSchedulingService.LOOKAHEAD_MONTHS);
        List<VisitTemplate> inWindow = allTemplates.stream()
                .filter(t -> VisitSchedulingService.nextOccurrenceInWindow(t.getMonth(), today, windowEnd, TORONTO) != null)
                .toList();
        assertThat(inWindow).isNotEmpty();

        VisitTemplate template = inWindow.get(0);

        // Pre-seed a visit for this same template dated a full year in the past — simulates
        // last year's occurrence, well outside the current [today, windowEnd) window.
        visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), template.getId(),
                java.time.Instant.now().minus(java.time.Duration.ofDays(365)),
                120, VisitType.ROUTINE));

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
        createdUserIds.add(user.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Scheduling St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        Long planTierId = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, planCode.name());
        if (planTierId == null) {
            throw new IllegalStateException("Plan tier not seeded for code: " + planCode);
        }

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        sub.setPlanTierId(planTierId);
        sub = subscriberRepository.save(sub);
        createdSubscriberIds.add(sub.getId());
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
        createdUserIds.add(user.getId());

        Property property = propertyRepository.save(new Property(
                nano + " NoPlan St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        // planTierId intentionally left null
        sub = subscriberRepository.save(sub);
        createdSubscriberIds.add(sub.getId());
        return sub;
    }
}
