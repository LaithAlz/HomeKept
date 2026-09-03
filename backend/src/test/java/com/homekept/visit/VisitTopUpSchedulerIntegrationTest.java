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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VisitTopUpScheduler#topUpScheduledVisits} (#57's remaining
 * piece: the recurring seasonal visit auto-scheduler). Runs against real Postgres via
 * Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>An ACTIVE subscriber with no visits yet gets scheduled by the job — the same visits
 *       {@link VisitSchedulingService#scheduleInitialVisits} would create if invoked
 *       directly.</li>
 *   <li>A non-ACTIVE subscriber (PAUSED, PENDING_ACTIVATION) is not touched by the job.</li>
 *   <li>Multiple ACTIVE subscribers are all topped up within a single run.</li>
 *   <li>Idempotency: running the job twice in a row creates no duplicate visits.</li>
 * </ul>
 *
 * <p>Per-subscriber failure isolation (one bad subscriber must not abort the rest of the
 * batch) is covered separately in {@link VisitTopUpSchedulerTest} with mocked collaborators —
 * there is no way to make {@link VisitSchedulingService#scheduleInitialVisits} throw via seed
 * data alone, since all of its guard clauses fail gracefully (log + return), not with
 * exceptions.
 */
class VisitTopUpSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired VisitTopUpScheduler visitTopUpScheduler;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired JdbcTemplate jdbc;

    // ── ACTIVE subscribers get topped up ────────────────────────────────────────

    @Test
    void topUpScheduledVisits_activeSubscriberWithNoVisits_getsScheduled() {
        Subscriber subscriber = seedSubscriber("topup-active", PlanCode.ESSENTIAL, SubscriberStatus.ACTIVE);

        visitTopUpScheduler.topUpScheduledVisits();

        assertThat(countVisitsForSubscriber(subscriber.getId())).isGreaterThan(0);
        assertThat(visitsForSubscriber(subscriber.getId()))
                .allMatch(v -> v.getStatus() == VisitStatus.SCHEDULED)
                .allMatch(v -> v.getType() == VisitType.ROUTINE);
    }

    @Test
    void topUpScheduledVisits_multipleActiveSubscribers_allToppedUpInOneRun() {
        Subscriber subscriberA = seedSubscriber("topup-multi-a", PlanCode.ESSENTIAL, SubscriberStatus.ACTIVE);
        Subscriber subscriberB = seedSubscriber("topup-multi-b", PlanCode.PREMIER, SubscriberStatus.ACTIVE);

        visitTopUpScheduler.topUpScheduledVisits();

        assertThat(countVisitsForSubscriber(subscriberA.getId())).isGreaterThan(0);
        assertThat(countVisitsForSubscriber(subscriberB.getId())).isGreaterThan(0);
    }

    // ── Idempotency ──────────────────────────────────────────────────────────────

    @Test
    void topUpScheduledVisits_secondRun_createsNoDuplicates() {
        Subscriber subscriber = seedSubscriber("topup-idempotent", PlanCode.COMPLETE, SubscriberStatus.ACTIVE);

        visitTopUpScheduler.topUpScheduledVisits();
        long countAfterFirst = countVisitsForSubscriber(subscriber.getId());
        assertThat(countAfterFirst).isGreaterThan(0);

        visitTopUpScheduler.topUpScheduledVisits();
        long countAfterSecond = countVisitsForSubscriber(subscriber.getId());

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    // ── Non-ACTIVE subscribers are skipped ──────────────────────────────────────

    @Test
    void topUpScheduledVisits_pausedSubscriber_isSkipped() {
        Subscriber subscriber = seedSubscriber("topup-paused", PlanCode.ESSENTIAL, SubscriberStatus.PAUSED);

        visitTopUpScheduler.topUpScheduledVisits();

        assertThat(countVisitsForSubscriber(subscriber.getId())).isZero();
    }

    @Test
    void topUpScheduledVisits_pendingActivationSubscriber_isSkipped() {
        Subscriber subscriber = seedSubscriber("topup-pending", PlanCode.ESSENTIAL, SubscriberStatus.PENDING_ACTIVATION);

        visitTopUpScheduler.topUpScheduledVisits();

        assertThat(countVisitsForSubscriber(subscriber.getId())).isZero();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private long countVisitsForSubscriber(Long subscriberId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit WHERE subscriber_id = ?", Long.class, subscriberId);
        return count != null ? count : 0L;
    }

    private List<Visit> visitsForSubscriber(Long subscriberId) {
        return visitRepository.findAll().stream()
                .filter(v -> v.getSubscriberId().equals(subscriberId))
                .toList();
    }

    /**
     * Seeds a subscriber with the given plan tier and status. The plan tier id is resolved
     * from the seeded catalog (V2__catalog.sql) by code. Status is set directly via the
     * constructor (bypassing the state machine), matching how other integration tests in this
     * suite seed fixed-status rows (e.g. {@code VisitSchedulingIntegrationTest}).
     */
    private Subscriber seedSubscriber(String emailPrefix, PlanCode planCode, SubscriberStatus status) {
        long nano = System.nanoTime();

        User user = userRepository.save(new User(
                emailPrefix + "." + nano + "@test.local",
                passwordEncoder.encode("placeholder"),
                "Test", "TopUp",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Top-Up St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        Long planTierId = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, planCode.name());
        if (planTierId == null) {
            throw new IllegalStateException("Plan tier not seeded for code: " + planCode);
        }

        Subscriber sub = new Subscriber(user.getId(), property.getId(), status, BillingCycle.MONTHLY);
        sub.setPlanTierId(planTierId);
        sub = subscriberRepository.save(sub);
        return sub;
    }
}
