package com.homekept.visit;

import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.subscription.SubscriberStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VisitTopUpScheduler#topUpScheduledVisits} per-subscriber failure
 * isolation. No Spring context / database needed — {@link SubscriberQueryService} and
 * {@link VisitSchedulingService} are mocked, mirroring the plain-Mockito style already used
 * for other pure-logic classes in this codebase (e.g. {@code ClientIpResolverTest}).
 *
 * <p>The "which subscribers actually get topped up" and "is a second run idempotent" behavior
 * is covered against real Postgres in {@link VisitTopUpSchedulerIntegrationTest}; this class
 * covers only the loop's exception handling, which cannot be triggered through seed data alone
 * since every guard clause in {@link VisitSchedulingService#scheduleInitialVisits} fails
 * gracefully (log + return) rather than throwing.
 */
class VisitTopUpSchedulerTest {

    @Test
    void topUpScheduledVisits_oneSubscriberThrows_othersStillGetScheduled() {
        SubscriberQueryService subscriberQueryService = mock(SubscriberQueryService.class);
        VisitSchedulingService visitSchedulingService = mock(VisitSchedulingService.class);

        // Subscriber has no equals/hashCode override, so these three distinct instances are
        // never equal to one another — reference identity is enough for Mockito's argument
        // matching and verification below.
        Subscriber good1 = newSubscriber();
        Subscriber bad = newSubscriber();
        Subscriber good2 = newSubscriber();

        when(subscriberQueryService.findByStatus(SubscriberStatus.ACTIVE))
                .thenReturn(List.of(good1, bad, good2));
        doThrow(new RuntimeException("simulated scheduling failure"))
                .when(visitSchedulingService).scheduleInitialVisits(bad);

        VisitTopUpScheduler scheduler = new VisitTopUpScheduler(subscriberQueryService, visitSchedulingService);

        // Must not propagate — a bad subscriber must not abort the run.
        scheduler.topUpScheduledVisits();

        // Every subscriber, including the one after the failure, was attempted.
        verify(visitSchedulingService).scheduleInitialVisits(good1);
        verify(visitSchedulingService).scheduleInitialVisits(bad);
        verify(visitSchedulingService).scheduleInitialVisits(good2);
        verify(visitSchedulingService, times(3)).scheduleInitialVisits(any());
    }

    @Test
    void topUpScheduledVisits_noActiveSubscribers_callsSchedulingServiceZeroTimes() {
        SubscriberQueryService subscriberQueryService = mock(SubscriberQueryService.class);
        VisitSchedulingService visitSchedulingService = mock(VisitSchedulingService.class);

        when(subscriberQueryService.findByStatus(SubscriberStatus.ACTIVE)).thenReturn(List.of());

        VisitTopUpScheduler scheduler = new VisitTopUpScheduler(subscriberQueryService, visitSchedulingService);
        scheduler.topUpScheduledVisits();

        verify(visitSchedulingService, times(0)).scheduleInitialVisits(any());
    }

    private Subscriber newSubscriber() {
        return new Subscriber(100L, 200L, SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
    }
}
