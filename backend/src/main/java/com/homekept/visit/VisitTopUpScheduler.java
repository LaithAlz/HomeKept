package com.homekept.visit;

import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.subscription.SubscriberStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Recurring daily top-up of every ACTIVE subscriber's rolling visit-scheduling window
 * (#57's remaining piece).
 *
 * <h2>Why this exists</h2>
 * <p>{@link VisitSchedulingService#scheduleInitialVisits} is only ever invoked once, at
 * activation, by {@link VisitSchedulingListener}. Its lookahead window is
 * {@code LOOKAHEAD_MONTHS} (4) months wide, so a subscriber who has been active for longer
 * than that would otherwise never get visits scheduled for the templates whose month has
 * since entered the window — the calendar would stop after the first 4 months and never
 * extend. This job re-runs the same call for every ACTIVE subscriber on a daily cadence so
 * the rolling window stays populated as time passes.
 *
 * <h2>Cadence</h2>
 * <p>Runs once daily at 4am in the render zone (a low-traffic hour). Daily is more than
 * enough headroom given the 4-month window and the 7-day
 * {@code VisitSchedulingService.MIN_DAYS_AHEAD} buffer — missing a run or two has no
 * customer-visible effect, it just gets picked up the next day. Per arch doc §5.3 MVP
 * guidance: plain {@code @Scheduled}, no durable job queue yet (Post-MVP: Jobrunr) — mirrors
 * {@link com.homekept.notification.ReminderScheduler} (#89). The zone is read from
 * {@code app.timezone} (the same property {@code TimeZoneConfig} binds {@code renderZoneId}
 * from) rather than a hardcoded "America/Toronto" literal.
 *
 * <h2>Idempotency</h2>
 * <p>{@link VisitSchedulingService#scheduleInitialVisits} is per-template idempotent (see its
 * class Javadoc): for each of a subscriber's tier-eligible templates that falls in the
 * lookahead window, it creates a visit only if the subscriber doesn't already have one tied
 * to that template. Running this job twice in a row (or every day, indefinitely) is always
 * safe — already-handled templates are skipped every time, and only templates newly inside
 * the window on a given day produce a new visit.
 *
 * <h2>Failure isolation</h2>
 * <p>Each subscriber is topped up independently inside its own try/catch. One subscriber's
 * failure (e.g. an unexpected data issue) is logged at WARN and the run continues — it must
 * never abort the rest of the batch, mirroring {@code ReminderScheduler}'s per-target
 * isolation.
 *
 * <h2>Domain boundary</h2>
 * <p>Lives in {@code com.homekept.visit} and calls {@link VisitSchedulingService} — this
 * domain's own service — directly. Subscribers are obtained via {@link SubscriberQueryService}
 * (the subscription domain's service, never its repository), the same cross-domain read
 * {@link VisitSchedulingListener} already performs.
 *
 * <h2>No status changes</h2>
 * <p>This job never writes a status field directly. It only calls
 * {@link VisitSchedulingService#scheduleInitialVisits}, which creates new visits through its
 * existing (already-reviewed) creation path.
 *
 * <h2>Scale note</h2>
 * <p>At MVP the active-subscriber set is small enough to load as a single list
 * ({@link SubscriberQueryService#findByStatus}). If the subscriber base grows large, this
 * should move to a paginated or streaming query instead of loading the whole set at once.
 */
@Service
public class VisitTopUpScheduler {

    private static final Logger log = LoggerFactory.getLogger(VisitTopUpScheduler.class);

    private final SubscriberQueryService subscriberQueryService;
    private final VisitSchedulingService visitSchedulingService;

    public VisitTopUpScheduler(SubscriberQueryService subscriberQueryService,
                               VisitSchedulingService visitSchedulingService) {
        this.subscriberQueryService = subscriberQueryService;
        this.visitSchedulingService = visitSchedulingService;
    }

    /**
     * Tops up the rolling scheduling window for every ACTIVE subscriber. Runs daily at 4am in
     * the render zone. Package-private so tests can trigger a run directly without waiting on
     * the cron tick (mirrors {@code ReminderScheduler}).
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "${app.timezone}")
    void topUpScheduledVisits() {
        List<Subscriber> activeSubscribers = subscriberQueryService.findByStatus(SubscriberStatus.ACTIVE);

        int succeeded = 0;
        int failed = 0;
        for (Subscriber subscriber : activeSubscribers) {
            try {
                visitSchedulingService.scheduleInitialVisits(subscriber);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("visit_top_up_subscriber_failed subscriberId={}", subscriber.getId(), e);
            }
        }

        log.info("visit_top_up_complete active_subscribers={} succeeded={} failed={}",
                activeSubscribers.size(), succeeded, failed);
    }
}
