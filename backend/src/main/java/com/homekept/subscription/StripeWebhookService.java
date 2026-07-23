package com.homekept.subscription;

import com.homekept.analytics.AnalyticsEvent;
import com.homekept.analytics.AnalyticsService;
import com.homekept.catalog.CatalogService;
import com.homekept.catalog.PlanTier;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles verified Stripe webhook events and drives subscriber state transitions.
 *
 * <h2>Idempotency</h2>
 * <p>Before processing any event the service checks whether a {@link SubscriptionEvent}
 * row with the same {@code stripeEventId} already exists. If so it short-circuits
 * (sequential duplicate — fast path). After successful processing a new row is persisted
 * via {@code saveAndFlush} inside the <em>same</em> {@code @Transactional} method. The
 * flush forces the partial unique index ({@code idx_subscription_event_stripe_id}) check
 * immediately; a concurrent duplicate throws {@link org.springframework.dao.DataIntegrityViolationException},
 * which rolls back the <em>entire</em> transaction (state change + event row) — so only
 * one delivery applies. The controller catches that exception and returns 200.
 *
 * <p>The previous {@code SubscriptionEventPersister} ({@code REQUIRES_NEW}) split has
 * been removed. It protected the state change from a concurrent event-row violation but
 * had the opposite problem: two concurrent deliveries could both apply the state change
 * before either inserted the event row, since the unique constraint only prevented two
 * event rows, not two state changes. The in-tx approach is the correct fix.
 *
 * <h2>Response code philosophy</h2>
 * <ul>
 *   <li><b>400</b> — signature verification failure or blank webhook secret. Returned by
 *       the controller before this service is called.</li>
 *   <li><b>200</b> — duplicate event (sequential: early return; concurrent:
 *       {@code DataIntegrityViolationException} caught in controller), unknown/ignored
 *       event type, or out-of-order state machine rejection. Stripe does not retry.</li>
 *   <li><b>5xx</b> — unexpected processing exception. Stripe retries on 5xx, which is
 *       correct for transient failures (DB down, etc.). We do NOT swallow unexpected
 *       exceptions and return 200 — that would silently drop real data.</li>
 * </ul>
 *
 * <h2>Out-of-order events</h2>
 * <p>When {@link SubscriberStateMachine} rejects a transition (e.g. a
 * {@code customer.subscription.paused} for an already-cancelled subscriber) the service
 * logs the skip and returns normally. The controller returns 200 so Stripe does not retry.
 * No idempotency row is written for skipped transitions (since no state changed).
 *
 * <h2>Domain boundary</h2>
 * <p>Catalog is accessed only via {@link CatalogService} — never its repository.
 * Visit scheduling is decoupled via a {@link SubscriberActivatedEvent} published after
 * the activation transaction commits. The visit domain listens via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} so a scheduling failure can never
 * poison or roll back the activation transaction.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final SubscriberRepository subscriberRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final SubscriberStateMachine stateMachine;
    private final CatalogService catalogService;
    private final SubscriptionStartedNotifier subscriptionStartedNotifier;
    private final PaymentFailedNotifier paymentFailedNotifier;
    private final SubscriptionCancelledNotifier subscriptionCancelledNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private final AnalyticsService analytics;

    public StripeWebhookService(SubscriberRepository subscriberRepository,
                                SubscriptionEventRepository subscriptionEventRepository,
                                SubscriberStateMachine stateMachine,
                                CatalogService catalogService,
                                SubscriptionStartedNotifier subscriptionStartedNotifier,
                                PaymentFailedNotifier paymentFailedNotifier,
                                SubscriptionCancelledNotifier subscriptionCancelledNotifier,
                                ApplicationEventPublisher eventPublisher,
                                AnalyticsService analytics) {
        this.subscriberRepository = subscriberRepository;
        this.subscriptionEventRepository = subscriptionEventRepository;
        this.stateMachine = stateMachine;
        this.catalogService = catalogService;
        this.subscriptionStartedNotifier = subscriptionStartedNotifier;
        this.paymentFailedNotifier = paymentFailedNotifier;
        this.subscriptionCancelledNotifier = subscriptionCancelledNotifier;
        this.eventPublisher = eventPublisher;
        this.analytics = analytics;
    }

    /**
     * Entry point called by the webhook controller for every verified event.
     *
     * <p>Unexpected runtime exceptions propagate to the controller and result in 500
     * so Stripe retries. Only intentional "skip" scenarios (duplicate, ignored type,
     * out-of-order transition) return normally without throwing.
     *
     * @param event      the verified Stripe event
     * @param rawPayload the raw JSON payload (stored verbatim in the event row)
     */
    @Transactional
    public void handle(Event event, String rawPayload) {
        // ── Idempotency check ────────────────────────────────────────────────
        Optional<SubscriptionEvent> existing =
                subscriptionEventRepository.findByStripeEventId(event.getId());
        if (existing.isPresent()) {
            log.info("webhook_duplicate stripeEventId={} type={} — skipping",
                    event.getId(), event.getType());
            return;
        }

        // ── Dispatch ─────────────────────────────────────────────────────────
        switch (event.getType()) {
            case "checkout.session.completed"    -> handleCheckoutSessionCompleted(event, rawPayload);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event, rawPayload);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event, rawPayload);
            case "invoice.payment_failed"        -> handlePaymentFailed(event, rawPayload);
            case "invoice.payment_succeeded"     -> handlePaymentSucceeded(event, rawPayload);
            case "customer.subscription.paused"  -> handleSubscriptionPaused(event, rawPayload);
            case "customer.subscription.resumed" -> handleSubscriptionResumed(event, rawPayload);
            // Explicitly ignored — acknowledge with 200, no row written.
            case "customer.created",
                 "customer.updated",
                 "invoice.created"               -> log.debug("webhook_ignored type={}", event.getType());
            default -> log.debug("webhook_unhandled type={} — acknowledged and ignored", event.getType());
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /**
     * checkout.session.completed (mode=subscription)
     *
     * <p>Activates the subscriber: sets Stripe ids, plan tier, period dates, billing
     * cycle, founding-rate flag (if requested), started_at; transitions
     * PENDING_ACTIVATION → ACTIVE; fires the "subscription started" notification.
     *
     * <p>mode=payment (extra-pick checkout) is acknowledged without state change —
     * handled by the picks slice.
     */
    private void handleCheckoutSessionCompleted(Event event, String rawPayload) {
        Session session = deserialize(event, Session.class);
        if (session == null) {
            log.warn("webhook_parse_error type=checkout.session.completed stripeEventId={}", event.getId());
            return;
        }

        // Only handle subscription-mode sessions here.
        if (!"subscription".equals(session.getMode())) {
            log.debug("webhook_ignored checkout.session.completed mode={} — not a subscription",
                    session.getMode());
            return;
        }

        // Resolve subscriber from metadata embedded at checkout session creation.
        String subscriberIdMeta = session.getMetadata() != null
                ? session.getMetadata().get("subscriberId") : null;
        if (subscriberIdMeta == null) {
            log.warn("webhook_missing_metadata checkout.session.completed stripeEventId={}", event.getId());
            return;
        }

        Long subscriberId;
        try {
            subscriberId = Long.parseLong(subscriberIdMeta);
        } catch (NumberFormatException e) {
            log.warn("webhook_bad_metadata subscriberId={} stripeEventId={}", subscriberIdMeta, event.getId());
            return;
        }

        Optional<Subscriber> subscriberOpt = subscriberRepository.findById(subscriberId);
        if (subscriberOpt.isEmpty()) {
            log.warn("webhook_subscriber_not_found subscriberId={} stripeEventId={}", subscriberId, event.getId());
            return;
        }
        Subscriber subscriber = subscriberOpt.get();

        // GUARD FIRST — before mutating any field. The Subscriber is a managed JPA entity,
        // so any setter called before an early return is still flushed by dirty-checking at
        // commit. checkout.session.completed may ONLY activate a brand-new subscription:
        // guard on PENDING_ACTIVATION specifically (PAUSED/PAYMENT_ISSUE also satisfy the
        // generic → ACTIVE transition, but those are reached via subscription.resumed /
        // invoice.payment_succeeded, never checkout). A returning customer is a NEW row (see
        // SubscriberStatus), and CheckoutService only lets PENDING_ACTIVATION subscribers
        // reach checkout — so an event here for an ineligible (e.g. CANCELLED) subscriber
        // means Stripe may have charged with no eligible row to activate. Log it as an alarm
        // for a manual refund and return WITHOUT touching the entity, so nothing is silently
        // overwritten on a row that never activates. The canTransition clause keeps the write
        // routed through SubscriberStateMachine (the non-negotiable) and fails closed if the
        // PENDING_ACTIVATION → ACTIVE transition is ever made illegal.
        if (subscriber.getStatus() != SubscriberStatus.PENDING_ACTIVATION
                || !stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.ACTIVE)) {
            log.error("webhook_activation_on_ineligible_subscriber status={} subscriberId={} "
                    + "stripeSubscriptionId={} stripeEventId={} — NOT activating; payment may need a "
                    + "manual refund, no fields mutated",
                    subscriber.getStatus(), subscriber.getId(), session.getSubscription(), event.getId());
            return;
        }

        Instant now = Instant.now();

        // Set Stripe identifiers.
        subscriber.setStripeCustomerId(session.getCustomer());
        subscriber.setStripeSubscriptionId(session.getSubscription());

        // Resolve plan tier from metadata.
        String planTierIdMeta = session.getMetadata().get("planTierId");
        if (planTierIdMeta != null) {
            try {
                subscriber.setPlanTierId(Long.parseLong(planTierIdMeta));
            } catch (NumberFormatException e) {
                log.warn("webhook_bad_metadata planTierId={} stripeEventId={}", planTierIdMeta, event.getId());
            }
        }

        // Reconcile founding status from THIS completed session's metadata — the session
        // Stripe is actually billing — so the recorded flag always matches what is charged.
        // The cap is enforced at checkout (the slot is reserved under the advisory lock
        // before the founding price is ever offered); here we SETTLE that reservation to
        // reality. A customer who reserved founding but ultimately completed a normal-price
        // session is released back to non-founding, so "recorded founding" can never diverge
        // from "billed founding" (which would otherwise leak a slot forever).
        boolean billedFounding = "true".equals(session.getMetadata().get("foundingRate"));
        subscriber.setFoundingRate(billedFounding);
        if (billedFounding) {
            subscriber.setFoundingRateExpiresAt(now.plusSeconds(365L * 24 * 3600));
        }

        // Period dates: Stripe does not expose them directly on the session. We set
        // approximate values here; customer.subscription.updated (which fires immediately
        // after this event for new subscriptions) syncs the real dates.
        subscriber.setCurrentPeriodStart(now);
        subscriber.setCurrentPeriodEnd(now.plusSeconds(30L * 24 * 3600));

        subscriber.setStatus(SubscriberStatus.ACTIVE);
        subscriber.setStartedAt(now);

        subscriberRepository.save(subscriber);

        // Resolve plan code for the notification (null-safe).
        String planCode = subscriber.getPlanTierId() != null
                ? catalogService.getPlanCode(subscriber.getPlanTierId())
                : null;

        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        subscriptionStartedNotifier.onSubscriptionStarted(subscriber.getId(), planCode);

        // Publish a SubscriberActivatedEvent so the visit domain can schedule initial
        // visits AFTER this transaction commits (TransactionalEventListener AFTER_COMMIT).
        // Decoupled this way so a scheduling failure can never roll back the activation.
        eventPublisher.publishEvent(new SubscriberActivatedEvent(subscriber.getId()));

        log.info("subscription_activated subscriberId={} planCode={} foundingRate={}",
                subscriber.getId(), planCode, subscriber.isFoundingRate());

        // Analytics (arch doc §5.7) — the revenue truth event, captured here on the webhook
        // (never trusting the client). Attributed to the subscriber's user; enum/flag props
        // only. Reuses the planCode already resolved above (no extra query). The billed cycle
        // comes from THIS session's metadata, not subscriber.getBillingCycle(): the subscriber
        // row still holds its default (MONTHLY) until the later customer.subscription.updated
        // syncs it, so reading the entity here would misreport every ANNUAL checkout.
        // Best-effort + commit-gated, wrapped so it can never roll back the activation.
        final String activatedPlanCode = planCode;
        final String billedCycle = session.getMetadata().get("billingCycle");
        captureSubscriptionEvent(subscriber, AnalyticsEvent.SUBSCRIPTION_ACTIVATED, () -> {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("plan_code", activatedPlanCode);
            props.put("billing_cycle", billedCycle);
            props.put("founding_rate", subscriber.isFoundingRate());
            return props;
        });
    }

    /**
     * customer.subscription.updated
     *
     * <p>Syncs plan tier (from the price) and current period dates.
     */
    private void handleSubscriptionUpdated(Event event, String rawPayload) {
        Subscription sub = deserialize(event, Subscription.class);
        if (sub == null) return;

        Optional<Subscriber> subscriberOpt =
                subscriberRepository.findByStripeSubscriptionId(sub.getId());
        if (subscriberOpt.isEmpty()) {
            log.warn("webhook_subscriber_not_found stripeSubscriptionId={} stripeEventId={}",
                    sub.getId(), event.getId());
            return;
        }
        Subscriber subscriber = subscriberOpt.get();

        // Sync period dates.
        if (sub.getCurrentPeriodStart() != null) {
            subscriber.setCurrentPeriodStart(Instant.ofEpochSecond(sub.getCurrentPeriodStart()));
        }
        if (sub.getCurrentPeriodEnd() != null) {
            subscriber.setCurrentPeriodEnd(Instant.ofEpochSecond(sub.getCurrentPeriodEnd()));
        }

        // Sync plan tier from price id.
        String priceId = extractPriceId(sub);
        if (priceId != null) {
            PlanTier tier = catalogService.findPlanTierByStripePriceId(priceId);
            if (tier != null) {
                subscriber.setPlanTierId(tier.getId());
            } else {
                log.warn("webhook_unknown_price_id priceId={} stripeEventId={}", priceId, event.getId());
            }
        }

        // Sync billing cycle.
        BillingCycle cycle = resolveBillingCycle(sub);
        if (cycle != null) {
            subscriber.setBillingCycle(cycle);
        }

        subscriberRepository.save(subscriber);
        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        log.info("subscription_updated subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());
    }

    /**
     * customer.subscription.deleted
     *
     * <p>Transitions the subscriber to CANCELLED, sets cancelledAt.
     */
    private void handleSubscriptionDeleted(Event event, String rawPayload) {
        Subscription sub = deserialize(event, Subscription.class);
        if (sub == null) return;

        Optional<Subscriber> subscriberOpt =
                subscriberRepository.findByStripeSubscriptionId(sub.getId());
        if (subscriberOpt.isEmpty()) {
            log.warn("webhook_subscriber_not_found stripeSubscriptionId={} stripeEventId={}",
                    sub.getId(), event.getId());
            return;
        }
        Subscriber subscriber = subscriberOpt.get();

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.CANCELLED)) {
            log.warn("webhook_illegal_transition from={} to=CANCELLED subscriberId={} stripeEventId={} — skip",
                    subscriber.getStatus(), subscriber.getId(), event.getId());
            return;
        }

        subscriber.setStatus(SubscriberStatus.CANCELLED);
        subscriber.setCancelledAt(Instant.now());
        subscriberRepository.save(subscriber);
        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        // Best-effort email — never throws, so it can't roll back the cancellation.
        subscriptionCancelledNotifier.onSubscriptionCancelled(subscriber.getId());
        log.info("subscription_cancelled subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());

        // Analytics (arch doc §5.7) — plan_code + months_subscribed only (the reason enum is
        // deferred until a cancel-reason is persisted; the free-text detail never leaves the
        // DB). Prop-gathering (incl. the plan-code lookup) runs inside the guarded helper so
        // it can never roll back the cancellation.
        captureSubscriptionEvent(subscriber, AnalyticsEvent.SUBSCRIPTION_CANCELLED, () -> {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("plan_code", subscriber.getPlanTierId() != null
                    ? catalogService.getPlanCode(subscriber.getPlanTierId()) : null);
            props.put("months_subscribed",
                    monthsBetween(subscriber.getStartedAt(), subscriber.getCancelledAt()));
            return props;
        });
    }

    /**
     * invoice.payment_failed
     *
     * <p>Transitions ACTIVE → PAYMENT_ISSUE.
     */
    private void handlePaymentFailed(Event event, String rawPayload) {
        Invoice invoice = deserialize(event, Invoice.class);
        if (invoice == null) return;

        Optional<Subscriber> subscriberOpt = resolveSubscriberFromInvoice(invoice, event.getId());
        if (subscriberOpt.isEmpty()) return;

        Subscriber subscriber = subscriberOpt.get();

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.PAYMENT_ISSUE)) {
            log.warn("webhook_illegal_transition from={} to=PAYMENT_ISSUE subscriberId={} stripeEventId={} — skip",
                    subscriber.getStatus(), subscriber.getId(), event.getId());
            return;
        }

        subscriber.setStatus(SubscriberStatus.PAYMENT_ISSUE);
        subscriberRepository.save(subscriber);
        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        // Best-effort email — never throws, so it can't roll back the state change.
        paymentFailedNotifier.onPaymentFailed(subscriber.getId());
        log.info("payment_issue subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());
    }

    /**
     * invoice.payment_succeeded
     *
     * <p>If the subscriber is in PAYMENT_ISSUE, restores to ACTIVE.
     * For subscribers already ACTIVE (normal recurring payment), records the event but
     * makes no state change.
     */
    private void handlePaymentSucceeded(Event event, String rawPayload) {
        Invoice invoice = deserialize(event, Invoice.class);
        if (invoice == null) return;

        Optional<Subscriber> subscriberOpt = resolveSubscriberFromInvoice(invoice, event.getId());
        if (subscriberOpt.isEmpty()) return;

        Subscriber subscriber = subscriberOpt.get();

        if (subscriber.getStatus() == SubscriberStatus.PAYMENT_ISSUE) {
            if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.ACTIVE)) {
                log.warn("webhook_illegal_transition from=PAYMENT_ISSUE to=ACTIVE subscriberId={} stripeEventId={} — skip",
                        subscriber.getId(), event.getId());
                return;
            }
            subscriber.setStatus(SubscriberStatus.ACTIVE);
            subscriberRepository.save(subscriber);
            persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
            log.info("payment_recovered subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());
        } else {
            // Normal recurring payment — no state change; record for audit.
            persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
            log.debug("payment_succeeded_no_state_change subscriberId={} status={}",
                    subscriber.getId(), subscriber.getStatus());
        }
    }

    /**
     * customer.subscription.paused
     *
     * <p>Transitions ACTIVE → PAUSED, sets pausedAt.
     */
    private void handleSubscriptionPaused(Event event, String rawPayload) {
        Subscription sub = deserialize(event, Subscription.class);
        if (sub == null) return;

        Optional<Subscriber> subscriberOpt =
                subscriberRepository.findByStripeSubscriptionId(sub.getId());
        if (subscriberOpt.isEmpty()) {
            log.warn("webhook_subscriber_not_found stripeSubscriptionId={} stripeEventId={}",
                    sub.getId(), event.getId());
            return;
        }
        Subscriber subscriber = subscriberOpt.get();

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.PAUSED)) {
            log.warn("webhook_illegal_transition from={} to=PAUSED subscriberId={} stripeEventId={} — skip",
                    subscriber.getStatus(), subscriber.getId(), event.getId());
            return;
        }

        subscriber.setStatus(SubscriberStatus.PAUSED);
        subscriber.setPausedAt(Instant.now());
        subscriberRepository.save(subscriber);
        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        log.info("subscription_paused subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());

        // Analytics (arch doc §5.7) — no props. Best-effort + commit-gated.
        captureSubscriptionEvent(subscriber, AnalyticsEvent.SUBSCRIPTION_PAUSED, () -> Map.of());
    }

    /**
     * customer.subscription.resumed
     *
     * <p>Transitions PAUSED → ACTIVE, clears pausedAt.
     */
    private void handleSubscriptionResumed(Event event, String rawPayload) {
        Subscription sub = deserialize(event, Subscription.class);
        if (sub == null) return;

        Optional<Subscriber> subscriberOpt =
                subscriberRepository.findByStripeSubscriptionId(sub.getId());
        if (subscriberOpt.isEmpty()) {
            log.warn("webhook_subscriber_not_found stripeSubscriptionId={} stripeEventId={}",
                    sub.getId(), event.getId());
            return;
        }
        Subscriber subscriber = subscriberOpt.get();

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.ACTIVE)) {
            log.warn("webhook_illegal_transition from={} to=ACTIVE subscriberId={} stripeEventId={} — skip",
                    subscriber.getStatus(), subscriber.getId(), event.getId());
            return;
        }

        subscriber.setStatus(SubscriberStatus.ACTIVE);
        subscriber.setPausedAt(null);
        subscriberRepository.save(subscriber);
        persistEvent(subscriber.getId(), event.getType(), rawPayload, event.getId());
        log.info("subscription_resumed subscriberId={} stripeEventId={}", subscriber.getId(), event.getId());

        // Analytics (arch doc §5.7) — no props. Best-effort + commit-gated.
        captureSubscriptionEvent(subscriber, AnalyticsEvent.SUBSCRIPTION_RESUMED, () -> Map.of());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Emits a subscription analytics event (arch doc §5.7), attributed to the subscriber's
     * user id. Best-effort: both the property-gathering ({@code propsSupplier}, which for the
     * cancelled event runs a plan-code {@code SELECT}) and the {@code capture} are wrapped, so
     * an analytics failure is logged and swallowed rather than returned to the caller.
     * {@code capture} is itself commit-gated, so a rolled-back webhook transaction emits no
     * event.
     *
     * <p>Caveat on the one supplier that reads the DB (cancelled): the read runs inside the
     * webhook transaction, so if it faulted, Spring could mark the transaction rollback-only
     * and the swallowed exception would not clear that flag — the outer commit would then throw
     * {@code UnexpectedRollbackException} and the state change would be undone. This is the same
     * risk the pre-existing activation path already carries (it too resolves the plan code
     * in-transaction), it only fires when the connection is already faulted (a primary-key
     * lookup otherwise cannot throw), and it self-heals: the controller lets the 500 through,
     * Stripe retries, and the idempotency ledger rolled back with the state change so the
     * retry re-processes cleanly. The suppliers that read no DB (activated reuses a local,
     * paused/resumed have none) cannot roll anything back.
     */
    private void captureSubscriptionEvent(Subscriber subscriber, String event,
                                          java.util.function.Supplier<Map<String, Object>> propsSupplier) {
        try {
            analytics.capture(subscriber.getUserId(), event, propsSupplier.get());
        } catch (RuntimeException e) {
            log.warn("analytics_capture_failed event={} subscriberId={}: {}",
                    event, subscriber.getId(), e.toString());
        }
    }

    /**
     * Whole months between two instants (floored, never negative). Returns 0 if either bound
     * is null. Used for the {@code months_subscribed} analytics property.
     */
    private static long monthsBetween(Instant from, Instant to) {
        if (from == null || to == null) return 0L;
        long months = ChronoUnit.MONTHS.between(
                from.atZone(java.time.ZoneOffset.UTC), to.atZone(java.time.ZoneOffset.UTC));
        return Math.max(0L, months);
    }

    /**
     * Deserializes the event's data object into the given type using the Stripe SDK.
     * Returns {@code null} and logs on failure.
     */
    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserialize(Event event, Class<T> type) {
        com.stripe.model.EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        // getObject() returns empty when the event's Stripe API version differs from the SDK's
        // pinned version — and it actually THROWS an NPE when the event carries no api_version
        // at all (Stripe SDK's apiVersionMatch dereferences a null). Guard both cases, then fall
        // back to deserializeUnsafe(), which ignores the version and deserializes with the SDK
        // model — correct for our own server-emitted events and resilient to account version drift.
        try {
            java.util.Optional<StripeObject> obj = deserializer.getObject();
            if (obj.isPresent()) {
                return type.cast(obj.get());
            }
        } catch (Exception e) {
            log.debug("webhook_getObject_failed type={} stripeEventId={} — falling back to unsafe",
                    event.getType(), event.getId());
        }
        try {
            return type.cast(deserializer.deserializeUnsafe());
        } catch (Exception e) {
            log.warn("webhook_deserialize_error type={} stripeEventId={}", event.getType(), event.getId(), e);
            return null;
        }
    }

    /**
     * Resolves the subscriber from an invoice event by trying:
     * 1. The invoice's subscription id.
     * 2. The invoice's customer id (fallback).
     */
    private Optional<Subscriber> resolveSubscriberFromInvoice(Invoice invoice, String stripeEventId) {
        if (invoice.getSubscription() != null && !invoice.getSubscription().isBlank()) {
            Optional<Subscriber> bySubscription =
                    subscriberRepository.findByStripeSubscriptionId(invoice.getSubscription());
            if (bySubscription.isPresent()) return bySubscription;
        }
        if (invoice.getCustomer() != null && !invoice.getCustomer().isBlank()) {
            Optional<Subscriber> byCustomer =
                    subscriberRepository.findByStripeCustomerId(invoice.getCustomer());
            if (byCustomer.isPresent()) return byCustomer;
        }
        log.warn("webhook_subscriber_not_found stripeEventId={} subscription={} customer={}",
                stripeEventId, invoice.getSubscription(), invoice.getCustomer());
        return Optional.empty();
    }

    /**
     * Extracts the first price id from a Stripe subscription's line items.
     * Returns {@code null} if the subscription has no items or no price.
     */
    private String extractPriceId(Subscription sub) {
        try {
            if (sub.getItems() == null || sub.getItems().getData() == null
                    || sub.getItems().getData().isEmpty()) {
                return null;
            }
            var item = sub.getItems().getData().get(0);
            return item.getPrice() != null ? item.getPrice().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Infers the billing cycle from a Stripe subscription's recurring interval.
     * Returns {@code null} if the interval cannot be determined.
     */
    private BillingCycle resolveBillingCycle(Subscription sub) {
        try {
            if (sub.getItems() == null || sub.getItems().getData() == null
                    || sub.getItems().getData().isEmpty()) {
                return null;
            }
            var price = sub.getItems().getData().get(0).getPrice();
            if (price == null || price.getRecurring() == null) return null;
            return "year".equals(price.getRecurring().getInterval())
                    ? BillingCycle.ANNUAL : BillingCycle.MONTHLY;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Persists a {@link SubscriptionEvent} row in the <em>current</em> transaction.
     *
     * <p>{@code saveAndFlush} forces an immediate SQL INSERT so the partial unique index
     * ({@code idx_subscription_event_stripe_id}) is checked before the transaction commits.
     * A concurrent duplicate delivery will fail here with
     * {@link org.springframework.dao.DataIntegrityViolationException}, which rolls back
     * the entire transaction (state change + event row undone) — preventing double-apply.
     * The controller catches that exception and returns 200 to Stripe.
     *
     * @param subscriberId  HomeKept subscriber id
     * @param eventType     Stripe event type string
     * @param rawPayload    raw JSON from the webhook request body
     * @param stripeEventId Stripe-generated event id (idempotency key)
     */
    private void persistEvent(Long subscriberId, String eventType, String rawPayload,
                              String stripeEventId) {
        SubscriptionEvent row = new SubscriptionEvent(
                subscriberId,
                eventType,
                rawPayload,
                SubscriptionEventSource.STRIPE_WEBHOOK,
                stripeEventId
        );
        row.setProcessedAt(Instant.now());
        subscriptionEventRepository.saveAndFlush(row);
    }
}
