package com.homekept.visit;

import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberActivatedEvent;
import com.homekept.subscription.SubscriberQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.Optional;

/**
 * Listens for {@link SubscriberActivatedEvent} and schedules the initial visits for the
 * newly-activated subscriber.
 *
 * <h2>Transaction boundaries</h2>
 * <p>The listener is invoked by Spring's
 * {@link org.springframework.transaction.event.TransactionalEventListener} mechanism
 * <em>after the activation transaction commits</em> ({@code phase = AFTER_COMMIT}).
 * This guarantees:
 * <ul>
 *   <li>Activation commits first — the subscriber is ACTIVE and the idempotency event
 *       row is durable before any scheduling work begins.</li>
 *   <li>If activation rolls back (e.g. concurrent-duplicate webhook), AFTER_COMMIT never
 *       fires — no orphan visits are created.</li>
 *   <li>This listener runs in a brand-new transaction ({@code REQUIRES_NEW}), fully
 *       isolated from the (already-committed) activation transaction. A scheduling
 *       failure rolls back only the scheduling transaction — the activation is already
 *       committed and unaffected.</li>
 * </ul>
 *
 * <h2>Domain boundary</h2>
 * <p>The event carries only a subscriber id. This listener re-loads the subscriber via
 * {@link SubscriberQueryService} (subscription domain's service — not its repository)
 * to obtain a fresh, attached entity for the scheduling work.
 *
 * <h2>Error isolation</h2>
 * <p>The listener body is wrapped in try/catch so a scheduling failure is logged and
 * silently swallowed — it never bubbles up to affect the already-committed activation.
 * Admin can re-trigger scheduling manually if needed (idempotency guard in
 * {@link VisitSchedulingService} prevents duplicates on retry).
 */
@Component
public class VisitSchedulingListener {

    private static final Logger log = LoggerFactory.getLogger(VisitSchedulingListener.class);

    private final VisitSchedulingService visitSchedulingService;
    private final SubscriberQueryService subscriberQueryService;

    public VisitSchedulingListener(VisitSchedulingService visitSchedulingService,
                                   SubscriberQueryService subscriberQueryService) {
        this.visitSchedulingService = visitSchedulingService;
        this.subscriberQueryService = subscriberQueryService;
    }

    /**
     * Schedules initial visits for a newly-activated subscriber.
     *
     * <p>Runs AFTER the activation transaction commits. Any exception thrown here is
     * caught and logged — it must never propagate back and affect the activation outcome.
     *
     * @param event the activation event carrying the subscriber id
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubscriberActivated(SubscriberActivatedEvent event) {
        try {
            Optional<Subscriber> subscriberOpt = subscriberQueryService.findById(event.subscriberId());
            if (subscriberOpt.isEmpty()) {
                log.error("visit_scheduling_error subscriberId={} reason=subscriber_not_found "
                        + "— visits not created; admin must schedule manually", event.subscriberId());
                return;
            }
            visitSchedulingService.scheduleInitialVisits(subscriberOpt.get());
        } catch (Exception e) {
            log.error("visit_scheduling_error subscriberId={} — visits not created; admin must schedule manually",
                    event.subscriberId(), e);
        }
    }
}
