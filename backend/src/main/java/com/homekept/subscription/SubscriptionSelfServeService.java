package com.homekept.subscription;

import com.homekept.common.Hashing;
import com.homekept.subscription.dto.SubscriptionActionResponse;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Customer self-serve subscription lifecycle: pause, resume, and cancel.
 *
 * <h2>Webhooks are the source of truth for status</h2>
 * <p>These methods do <strong>not</strong> write {@code subscriber.status}. Each one
 * validates eligibility, then calls Stripe; the resulting status transition
 * (ACTIVE → PAUSED, PAUSED → ACTIVE, → CANCELLED) is applied later by the Stripe webhook
 * handler ({@link StripeWebhookService}) so there is exactly one writer of subscription
 * state. The response therefore reports the <em>current</em> status, not the pending one.
 *
 * <h2>Eligibility</h2>
 * <p>Eligibility is checked against {@link SubscriberStateMachine} (the same legality used
 * by the webhook) plus a Stripe-subscription-presence guard. A subscriber with no Stripe
 * subscription id (never completed checkout) gets a 409 {@link NoBillingAccountException}.
 *
 * <h2>Churn data</h2>
 * <p>Cancel captures the cancellation reason as a {@code MANUAL} {@link SubscriptionEvent}
 * (JSONB payload {@code {"reason": ..., "by": "CUSTOMER"}}) at request time — Stripe does not
 * carry it. The event and the Stripe call share one transaction, so a Stripe failure rolls
 * back the churn record (no orphan "cancelled" event when nothing was cancelled).
 *
 * <h2>Duplicate cancel guard</h2>
 * <p>Before writing a new churn event, cancel checks the subscriber's most recent
 * {@link SubscriptionEvent}: if it is already a {@code CANCELLATION_REQUESTED} and the
 * subscriber hasn't reached CANCELLED yet (the {@code customer.subscription.deleted} webhook
 * hasn't landed), a second cancel request — a double-click, or an admin retrying after an
 * immediate-cancel Stripe error — is rejected with 409 rather than writing a duplicate event
 * or resubmitting to Stripe.
 *
 * <h2>Shared with the admin console</h2>
 * <p>The package-private {@code *Subscriber(Subscriber ...)} methods below hold the actual
 * guard + Stripe-call bodies and are also called by {@link SubscriptionAdminService}, which
 * resolves the {@link Subscriber} by subscriber id (not user id) and applies its own 404 /
 * {@link NoBillingAccountException} guards before delegating here — so the pause/resume/
 * cancel mechanics (state-machine legality, idempotency keys, Stripe calls) have exactly one
 * implementation regardless of whether the customer or an admin triggers them. These methods
 * are NOT themselves {@code @Transactional}: the public entry points on this class
 * ({@link #pause}, {@link #resume}, {@link #cancel}) are, and so are
 * {@code SubscriptionAdminService}'s public mutation methods — the public caller always
 * supplies the transaction the shared method runs in.
 */
@Service
public class SubscriptionSelfServeService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSelfServeService.class);

    /** Stripe event_type recorded for the churn reason. */
    private static final String CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";

    private final SubscriberQueryService subscriberQueryService;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final SubscriberStateMachine stateMachine;
    private final StripeService stripeService;
    private final ObjectMapper objectMapper;

    public SubscriptionSelfServeService(SubscriberQueryService subscriberQueryService,
                                        SubscriptionEventRepository subscriptionEventRepository,
                                        SubscriberStateMachine stateMachine,
                                        StripeService stripeService,
                                        ObjectMapper objectMapper) {
        this.subscriberQueryService = subscriberQueryService;
        this.subscriptionEventRepository = subscriptionEventRepository;
        this.stateMachine = stateMachine;
        this.stripeService = stripeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Requests a pause on the authenticated subscriber's billing.
     * Eligible only from ACTIVE. The PAUSED transition is applied by the webhook.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @return the current status and period end
     */
    @Transactional(readOnly = true)
    public SubscriptionActionResponse pause(Long userId) {
        Subscriber subscriber = requireBilledSubscriber(userId);
        SubscriptionActionResponse response = pauseSubscriber(subscriber);
        log.info("subscription_pause_requested subscriberId={}", subscriber.getId());
        return response;
    }

    /**
     * Requests a resume on the authenticated subscriber's billing.
     * Eligible only from PAUSED. The ACTIVE transition is applied by the webhook.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @return the current status and period end
     */
    @Transactional(readOnly = true)
    public SubscriptionActionResponse resume(Long userId) {
        Subscriber subscriber = requireBilledSubscriber(userId);
        SubscriptionActionResponse response = resumeSubscriber(subscriber);
        log.info("subscription_resume_requested subscriberId={}", subscriber.getId());
        return response;
    }

    /**
     * Requests cancellation at period end and records the churn reason.
     * Eligible from any non-terminal billed status. CANCELLED is applied by the
     * {@code customer.subscription.deleted} webhook when the period ends.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @param reason the customer's free-text cancellation reason (required, churn data)
     * @return the current status and period end (when access runs through)
     */
    @Transactional
    public SubscriptionActionResponse cancel(Long userId, String reason) {
        Subscriber subscriber = requireBilledSubscriber(userId);
        SubscriptionActionResponse response =
                cancelSubscriber(subscriber, serializeReason(reason), false);

        // No reason in the log — churn text is PII-ish free text and lives only in the row.
        log.info("subscription_cancel_requested subscriberId={}", subscriber.getId());
        return response;
    }

    // ── Shared with SubscriptionAdminService ────────────────────────────────────

    /**
     * Guard-only: verifies the subscriber has a Stripe subscription id, else throws
     * {@link NoBillingAccountException} (409). Exposed for {@link SubscriptionAdminService},
     * which resolves the subscriber by id itself and needs the same billing-presence check
     * before delegating to {@link #pauseSubscriber}/{@link #resumeSubscriber}/
     * {@link #cancelSubscriber}.
     *
     * @throws NoBillingAccountException if no Stripe subscription id is set yet (409)
     */
    void requireBilled(Subscriber subscriber) {
        if (subscriber.getStripeSubscriptionId() == null
                || subscriber.getStripeSubscriptionId().isBlank()) {
            throw new NoBillingAccountException(
                    "No active subscription to manage. Complete checkout first.");
        }
    }

    /**
     * Pause mechanics shared by the customer self-serve and admin flows. Eligible only from
     * ACTIVE. Caller is responsible for resolving the subscriber and the not-found/billing
     * guards, and for running this in a transaction (see class javadoc).
     */
    SubscriptionActionResponse pauseSubscriber(Subscriber subscriber) {
        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.PAUSED)) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.PAUSED);
        }

        stripeService.pauseSubscription(
                subscriber.getStripeSubscriptionId(),
                idempotencyKey("pause", subscriber));

        return toResponse(subscriber);
    }

    /**
     * Resume mechanics shared by the customer self-serve and admin flows. Resume is
     * specifically un-pausing: only a PAUSED subscriber qualifies. (The state machine also
     * allows PAYMENT_ISSUE → ACTIVE, but that recovery path is webhook-only, not a "resume"
     * action — so guard on PAUSED explicitly.) Caller is responsible for resolving the
     * subscriber and the not-found/billing guards, and for running this in a transaction
     * (see class javadoc).
     */
    SubscriptionActionResponse resumeSubscriber(Subscriber subscriber) {
        if (subscriber.getStatus() != SubscriberStatus.PAUSED) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.ACTIVE);
        }

        stripeService.resumeSubscription(
                subscriber.getStripeSubscriptionId(),
                idempotencyKey("resume", subscriber));

        return toResponse(subscriber);
    }

    /**
     * Cancel mechanics shared by the customer self-serve and admin flows: rejects a duplicate
     * request (see class javadoc), records the churn reason BEFORE calling Stripe (so a
     * Stripe failure rolls back the churn record too), then either schedules cancellation at
     * period end or cancels immediately. Caller is responsible for resolving the subscriber,
     * the not-found/billing guards, building the {@code payload} JSON (self-serve:
     * {@code {"reason": ..., "by": "CUSTOMER"}}; admin builds its own payload with
     * {@code byUserId}/{@code immediate} — see {@code SubscriptionAdminService}), and running
     * this in a transaction (see class javadoc).
     *
     * @param subscriber  the subscriber to cancel
     * @param payload     the churn-reason JSONB payload, already serialized
     * @param immediately {@code false} = cancel at period end; {@code true} = cancel now
     * @throws IllegalSubscriptionStateException the subscriber cannot transition to CANCELLED,
     *                                            or a cancellation is already pending (409)
     */
    SubscriptionActionResponse cancelSubscriber(Subscriber subscriber, String payload, boolean immediately) {
        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.CANCELLED)) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.CANCELLED);
        }

        // Duplicate-request guard: if the subscriber's most recent event is already a pending
        // cancellation (the CANCELLED webhook hasn't landed yet — status still permits the
        // transition above), reject the retry instead of writing a second churn row or
        // resubmitting to Stripe (which would 500 on an immediate-cancel retry).
        Optional<SubscriptionEvent> mostRecent =
                subscriptionEventRepository.findTopBySubscriberIdOrderByCreatedAtDescIdDesc(subscriber.getId());
        if (mostRecent.isPresent()
                && CANCELLATION_REQUESTED.equals(mostRecent.get().getEventType())
                && subscriber.getStatus() != SubscriberStatus.CANCELLED) {
            throw new IllegalSubscriptionStateException(
                    "A cancellation has already been requested for this subscriber.");
        }

        // Record churn reason BEFORE the Stripe call so a Stripe failure rolls it back too.
        // Reason is serialized via Jackson — never string-concatenated into the JSON.
        SubscriptionEvent churn = new SubscriptionEvent(
                subscriber.getId(),
                CANCELLATION_REQUESTED,
                payload,
                SubscriptionEventSource.MANUAL);
        churn.setProcessedAt(Instant.now());
        subscriptionEventRepository.save(churn);

        if (immediately) {
            stripeService.cancelSubscriptionNow(
                    subscriber.getStripeSubscriptionId(),
                    idempotencyKey("cancel_now", subscriber));
        } else {
            stripeService.cancelSubscriptionAtPeriodEnd(
                    subscriber.getStripeSubscriptionId(),
                    idempotencyKey("cancel", subscriber));
        }

        return toResponse(subscriber);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the subscriber for the user and verifies a Stripe subscription exists.
     *
     * @throws SubscriberNotFoundException if the user has no subscriber row (404)
     * @throws NoBillingAccountException   if no Stripe subscription id is set yet (409)
     */
    private Subscriber requireBilledSubscriber(Long userId) {
        Subscriber subscriber = subscriberQueryService.requireByUserId(userId);
        requireBilled(subscriber);
        return subscriber;
    }

    private SubscriptionActionResponse toResponse(Subscriber subscriber) {
        return new SubscriptionActionResponse(
                subscriber.getStatus().name(),
                subscriber.getCurrentPeriodEnd());
    }

    /**
     * Deterministic-per-second idempotency key. The epoch-second bucket dedupes a
     * double-clicked request while still letting a genuine later toggle (e.g. pause again
     * after a resume) use a fresh key — a static key would make Stripe replay the first
     * response and silently skip the second toggle.
     */
    private String idempotencyKey(String action, Subscriber subscriber) {
        return Hashing.sha256Hex(action + ":" + subscriber.getId() + ":"
                + subscriber.getStripeSubscriptionId() + ":" + Instant.now().getEpochSecond());
    }

    private String serializeReason(String reason) {
        // Jackson 3 (tools.jackson) throws an unchecked JacksonException; a two-entry string
        // map cannot realistically fail to serialize, so no checked handling needed.
        return objectMapper.writeValueAsString(Map.of("reason", reason, "by", "CUSTOMER"));
    }
}
