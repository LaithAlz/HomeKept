package com.homekept.subscription;

import com.homekept.subscription.dto.SubscriptionActionResponse;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

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
 * <p>Cancel captures the customer's reason as a {@code MANUAL} {@link SubscriptionEvent}
 * (JSONB payload {@code {"reason": ...}}) at request time — Stripe does not carry it. The
 * event and the Stripe call share one transaction, so a Stripe failure rolls back the
 * churn record (no orphan "cancelled" event when nothing was cancelled).
 */
@Service
public class SubscriptionSelfServeService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSelfServeService.class);

    /** Stripe event_type recorded for the churn reason. */
    private static final String CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";

    private final SubscriberRepository subscriberRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final SubscriberStateMachine stateMachine;
    private final StripeService stripeService;
    private final ObjectMapper objectMapper;

    public SubscriptionSelfServeService(SubscriberRepository subscriberRepository,
                                        SubscriptionEventRepository subscriptionEventRepository,
                                        SubscriberStateMachine stateMachine,
                                        StripeService stripeService,
                                        ObjectMapper objectMapper) {
        this.subscriberRepository = subscriberRepository;
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

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.PAUSED)) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.PAUSED);
        }

        stripeService.pauseSubscription(
                subscriber.getStripeSubscriptionId(),
                idempotencyKey("pause", subscriber));

        log.info("subscription_pause_requested subscriberId={}", subscriber.getId());
        return toResponse(subscriber);
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

        // Resume is specifically un-pausing: only a PAUSED subscriber qualifies. (The state
        // machine also allows PAYMENT_ISSUE → ACTIVE, but that recovery path is webhook-only,
        // not a customer "resume" action — so guard on PAUSED explicitly.)
        if (subscriber.getStatus() != SubscriberStatus.PAUSED) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.ACTIVE);
        }

        stripeService.resumeSubscription(
                subscriber.getStripeSubscriptionId(),
                idempotencyKey("resume", subscriber));

        log.info("subscription_resume_requested subscriberId={}", subscriber.getId());
        return toResponse(subscriber);
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

        if (!stateMachine.canTransition(subscriber.getStatus(), SubscriberStatus.CANCELLED)) {
            throw new IllegalSubscriptionStateException(subscriber.getStatus(), SubscriberStatus.CANCELLED);
        }

        // Record churn reason BEFORE the Stripe call so a Stripe failure rolls it back too.
        // Reason is serialized via Jackson — never string-concatenated into the JSON.
        SubscriptionEvent churn = new SubscriptionEvent(
                subscriber.getId(),
                CANCELLATION_REQUESTED,
                serializeReason(reason),
                SubscriptionEventSource.MANUAL);
        churn.setProcessedAt(Instant.now());
        subscriptionEventRepository.save(churn);

        stripeService.cancelSubscriptionAtPeriodEnd(
                subscriber.getStripeSubscriptionId(),
                idempotencyKey("cancel", subscriber));

        // No reason in the log — churn text is PII-ish free text and lives only in the row.
        log.info("subscription_cancel_requested subscriberId={}", subscriber.getId());
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
        Subscriber subscriber = subscriberRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "No subscriber row found for userId=" + userId));

        if (subscriber.getStripeSubscriptionId() == null
                || subscriber.getStripeSubscriptionId().isBlank()) {
            throw new NoBillingAccountException(
                    "No active subscription to manage. Complete checkout first.");
        }
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
        return StripeServiceImpl.sha256Hex(action + ":" + subscriber.getId() + ":"
                + subscriber.getStripeSubscriptionId() + ":" + Instant.now().getEpochSecond());
    }

    private String serializeReason(String reason) {
        // Jackson 3 (tools.jackson) throws an unchecked JacksonException; a single-entry
        // string map cannot realistically fail to serialize, so no checked handling needed.
        return objectMapper.writeValueAsString(Map.of("reason", reason));
    }
}
