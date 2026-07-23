package com.homekept;

import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.analytics.AnalyticsEvent;
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
import com.stripe.net.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Stripe webhook endpoint using REAL signature verification.
 *
 * <p>Does NOT import {@link FakeStripeServiceConfig} — the real
 * {@link com.homekept.subscription.StripeServiceImpl} is used so that
 * {@code Webhook.constructEvent} actually verifies HMAC-SHA256 signatures against the
 * test webhook secret configured in {@code src/test/resources/application.yml}.
 *
 * <p>{@link com.homekept.subscription.SubscriptionEventRepository} is package-private
 * so we query {@code subscription_event} rows via {@link JdbcTemplate} instead.
 *
 * <p>Runs against a real Postgres via Testcontainers. Teardown follows the FK-safe
 * order established in {@link ActivationIntegrationTest}:
 * subscription_event → subscriber → property → user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingAnalyticsConfig.class})
class StripeWebhookIntegrationTest {

    /**
     * Must match {@code app.stripe.webhook-secret} in
     * {@code src/test/resources/application.yml}. NON-{@code whsec_} prefix so
     * GitGuardian does not flag it as a real Stripe webhook secret.
     */
    private static final String WEBHOOK_SECRET = "test-only-webhook-signing-placeholder";
    private static final String WEBHOOK_URL     = "/api/webhooks/stripe";

    @Autowired MockMvc mockMvc;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingAnalyticsService recording;

    @BeforeEach
    void clearRecorder() {
        recording.clear();
    }

    // Track row ids in creation order for FK-safe teardown.
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds       = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // visit_service and visit rows reference subscriber via visit.subscriber_id
        // (ON DELETE RESTRICT), so they must be removed before the subscriber row.
        // visit_service has ON DELETE CASCADE from visit, but we delete explicitly in
        // the correct FK order: visit_service → visit → subscriber.
        for (Long id : createdSubscriberIds) {
            jdbc.update(
                    "DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", id);
            jdbc.update("DELETE FROM visit WHERE subscriber_id = ?", id);
        }

        // subscription_event rows reference subscriber_id — delete them next.
        for (Long id : createdSubscriberIds) {
            jdbc.update("DELETE FROM subscription_event WHERE subscriber_id = ?", id);
        }

        // subscriber references property and user.
        for (Long id : createdSubscriberIds) {
            subscriberRepository.deleteById(id);
        }
        createdSubscriberIds.clear();

        for (Long id : createdPropertyIds) {
            propertyRepository.deleteById(id);
        }
        createdPropertyIds.clear();

        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ── Signature verification ────────────────────────────────────────────────

    @Test
    void badSignature_returns400() throws Exception {
        String payload = checkoutSessionPayload("evt_bad_sig_1", "9999", "1", "false");

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ── checkout.session.completed (mode=subscription) ────────────────────────

    @Test
    void checkoutSessionCompleted_activatesSubscriber() throws Exception {
        Subscriber sub = seedPendingSubscriber("checkout-activate@test.local");

        String eventId = "evt_checkout_completed_1";
        // planTierId=1 corresponds to ESSENTIAL in the seeded catalog.
        String payload = checkoutSessionPayload(eventId, String.valueOf(sub.getId()), "1", "false");

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.ACTIVE);
        assertThat(updated.getStripeCustomerId()).isEqualTo("cus_test");
        assertThat(updated.getStripeSubscriptionId()).isEqualTo("sub_test");
        assertThat(updated.getStartedAt()).isNotNull();

        // One subscription_event row must exist for this stripeEventId.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE stripe_event_id = ?",
                Integer.class, eventId);
        assertThat(count).isEqualTo(1);

        // The AFTER_COMMIT listener must have scheduled the subscriber's initial visits.
        // ESSENTIAL (planTierId=1) always has at least one template month (Jan/Apr/Jul/Oct)
        // inside the 4-month scheduling window, so at least one SCHEDULED visit is created —
        // proving the activation → SubscriberActivatedEvent → scheduling wiring works end-to-end.
        Integer visitCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit WHERE subscriber_id = ? AND status = 'SCHEDULED'",
                Integer.class, sub.getId());
        assertThat(visitCount).isGreaterThan(0);

        // Analytics: the revenue-truth activation event fired on the webhook, attributed to
        // the subscriber's user, with enum/flag props only (no PII).
        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.SUBSCRIPTION_ACTIVATED);
            assertThat(e.distinctId()).isEqualTo(sub.getUserId());
            assertThat(e.props().get("plan_code")).isNotNull();
            assertThat(e.props()).containsEntry("billing_cycle", "MONTHLY");
            assertThat(e.props()).containsEntry("founding_rate", false);
        });
    }

    @Test
    void checkoutSessionCompleted_idempotency_secondPostIsNoOp() throws Exception {
        Subscriber sub = seedPendingSubscriber("checkout-idempotent@test.local");

        String eventId = "evt_checkout_idempotent_1";
        String payload = checkoutSessionPayload(eventId, String.valueOf(sub.getId()), "1", "false");

        // First POST — activates the subscriber.
        postSignedWebhook(payload);
        assertThat(subscriberRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriberStatus.ACTIVE);

        Integer rowsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE stripe_event_id = ?",
                Integer.class, eventId);

        // Second POST — same stripeEventId — must be a no-op (idempotency guard).
        postSignedWebhook(payload);
        assertThat(subscriberRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriberStatus.ACTIVE);

        Integer rowsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE stripe_event_id = ?",
                Integer.class, eventId);
        // Must still be exactly 1 row — no duplicate written.
        assertThat(rowsAfter).isEqualTo(rowsBefore);
    }

    // ── invoice.payment_failed → PAYMENT_ISSUE ────────────────────────────────

    @Test
    void invoicePaymentFailed_transitionsActiveToPaymentIssue() throws Exception {
        Subscriber sub = seedActiveSubscriber("payment-failed@test.local");

        String eventId = "evt_invoice_payment_failed_1";
        String payload = invoicePayload(eventId, "invoice.payment_failed",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        postSignedWebhook(payload);

        assertThat(subscriberRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriberStatus.PAYMENT_ISSUE);
    }

    // ── invoice.payment_succeeded → ACTIVE (recovery) ─────────────────────────

    @Test
    void invoicePaymentSucceeded_fromPaymentIssue_transitionsToActive() throws Exception {
        Subscriber sub = seedSubscriberWithStatus("payment-recovered@test.local",
                SubscriberStatus.PAYMENT_ISSUE);

        String eventId = "evt_invoice_payment_succeeded_1";
        String payload = invoicePayload(eventId, "invoice.payment_succeeded",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        postSignedWebhook(payload);

        assertThat(subscriberRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriberStatus.ACTIVE);
    }

    // ── customer.subscription.deleted → CANCELLED ─────────────────────────────

    @Test
    void subscriptionDeleted_transitionsActiveToCancelled() throws Exception {
        Subscriber sub = seedActiveSubscriber("sub-deleted@test.local");

        String eventId = "evt_sub_deleted_1";
        String payload = subscriptionPayload(eventId, "customer.subscription.deleted",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.CANCELLED);
        assertThat(updated.getCancelledAt()).isNotNull();

        // Analytics: subscription_cancelled fired with months_subscribed only (plus plan_code
        // when resolvable) — the free-text cancel reason never reaches analytics.
        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.SUBSCRIPTION_CANCELLED);
            assertThat(e.distinctId()).isEqualTo(sub.getUserId());
            assertThat(e.props()).containsKey("months_subscribed");
        });
    }

    // ── customer.subscription.paused → PAUSED ────────────────────────────────

    @Test
    void subscriptionPaused_transitionsActiveToPaused() throws Exception {
        Subscriber sub = seedActiveSubscriber("sub-paused@test.local");

        String eventId = "evt_sub_paused_1";
        String payload = subscriptionPayload(eventId, "customer.subscription.paused",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.PAUSED);
        assertThat(updated.getPausedAt()).isNotNull();

        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.SUBSCRIPTION_PAUSED);
            assertThat(e.distinctId()).isEqualTo(sub.getUserId());
            assertThat(e.props()).isEmpty();
        });
    }

    // ── customer.subscription.resumed → ACTIVE ────────────────────────────────

    @Test
    void subscriptionResumed_transitionsPausedToActive() throws Exception {
        Subscriber sub = seedSubscriberWithStatus("sub-resumed@test.local",
                SubscriberStatus.PAUSED);

        String eventId = "evt_sub_resumed_1";
        String payload = subscriptionPayload(eventId, "customer.subscription.resumed",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.ACTIVE);
        assertThat(updated.getPausedAt()).isNull();

        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.SUBSCRIPTION_RESUMED);
            assertThat(e.distinctId()).isEqualTo(sub.getUserId());
            assertThat(e.props()).isEmpty();
        });
    }

    // ── Ignored event ─────────────────────────────────────────────────────────

    @Test
    void ignoredEvent_customerCreated_returns200_noSubscriptionEventRow() throws Exception {
        // customer.created is in the explicit ignore list in StripeWebhookService.
        String eventId = "evt_customer_created_ignored_1";
        String payload = """
                {"id":"%s","object":"event","type":"customer.created",\
"data":{"object":{"id":"cus_ignored","object":"customer"}}}""".formatted(eventId);

        long rowsBefore = countAllSubscriptionEvents();

        postSignedWebhook(payload);

        // No subscription_event row should be written for an ignored event.
        assertThat(countAllSubscriptionEvents()).isEqualTo(rowsBefore);
    }

    // ── Out-of-order / illegal transition ─────────────────────────────────────

    @Test
    void illegalTransition_pausedOnCancelledSubscriber_returns200_statusUnchanged() throws Exception {
        // CANCELLED is terminal — customer.subscription.paused must be rejected by the state
        // machine. The handler should ack with 200 and not write an event row.
        Subscriber sub = seedSubscriberWithStatus("cancelled-paused@test.local",
                SubscriberStatus.CANCELLED);

        String eventId = "evt_paused_on_cancelled_1";
        String payload = subscriptionPayload(eventId, "customer.subscription.paused",
                sub.getStripeSubscriptionId(), sub.getStripeCustomerId());

        // Must return 200 (Stripe should not retry) — the state machine rejects silently.
        postSignedWebhook(payload);

        // Status must remain CANCELLED.
        assertThat(subscriberRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriberStatus.CANCELLED);

        // No event row written for skipped (out-of-order) transitions.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE stripe_event_id = ?",
                Integer.class, eventId);
        assertThat(count).isZero();
    }

    // ── checkout.session.completed on an ineligible subscriber (B1: no dirty-flush) ──

    @Test
    void checkoutSessionCompleted_onCancelledSubscriber_doesNotActivateOrMutateAnyField() throws Exception {
        // B1 regression. The Subscriber is a managed JPA entity: if the handler mutated its
        // Stripe ids / plan / period BEFORE the transition guard and then returned on the illegal
        // CANCELLED → ACTIVE transition, dirty-checking would still flush those writes onto a dead
        // row (corrupting Stripe linkage / burning a founding slot). The guard now runs first, so
        // an ineligible event mutates nothing. CheckoutService already blocks this checkout from
        // starting; this proves the webhook is safe as defense in depth.
        Subscriber sub = seedSubscriberWithStatus("checkout-on-cancelled@test.local",
                SubscriberStatus.CANCELLED);
        String originalCustomerId = sub.getStripeCustomerId();
        String originalSubscriptionId = sub.getStripeSubscriptionId();

        String eventId = "evt_checkout_on_cancelled_1";
        String payload = checkoutSessionPayload(eventId, String.valueOf(sub.getId()), "1", "false");

        postSignedWebhook(payload); // asserts 200 — Stripe must not retry

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.CANCELLED);
        // The webhook's cus_test / sub_test must NOT have overwritten the seeded ids.
        assertThat(updated.getStripeCustomerId()).isEqualTo(originalCustomerId);
        assertThat(updated.getStripeSubscriptionId()).isEqualTo(originalSubscriptionId);
        assertThat(updated.getStartedAt()).isNull();

        // No event row written for a skipped transition.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE stripe_event_id = ?",
                Integer.class, eventId);
        assertThat(count).isZero();
    }

    // ── checkout.session.completed with a founding slot claimed at checkout (B2) ──

    @Test
    void checkoutSessionCompleted_foundingClaimedAtCheckout_activatesAndStampsExpiry() throws Exception {
        // B2: the founding slot is claimed at checkout (founding_rate already true on the row).
        // The webhook must NOT re-grant or re-count it — it only stamps the 12-month expiry on
        // activation. Verifies founding_rate survives activation and the expiry is set.
        Subscriber sub = seedSubscriberWithStatus("founding-preclaimed@test.local",
                SubscriberStatus.PENDING_ACTIVATION);
        sub.setFoundingRate(true);
        subscriberRepository.save(sub);

        String eventId = "evt_checkout_founding_preclaimed_1";
        String payload = checkoutSessionPayload(eventId, String.valueOf(sub.getId()), "1", "true");

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.ACTIVE);
        assertThat(updated.isFoundingRate()).isTrue();
        assertThat(updated.getFoundingRateExpiresAt()).isNotNull();
    }

    @Test
    void checkoutSessionCompleted_reservedFoundingButCompletedNormalSession_reconcilesFlagToFalse() throws Exception {
        // HIGH regression: a customer can reserve a founding slot at checkout (founding_rate
        // set true) and then complete a NORMAL-price session. The webhook must reconcile the
        // recorded flag to what Stripe actually billed — otherwise the customer is recorded
        // founding while billed normal and permanently consumes one of 15 founding slots.
        Subscriber sub = seedSubscriberWithStatus("reserved-then-normal@test.local",
                SubscriberStatus.PENDING_ACTIVATION);
        sub.setFoundingRate(true); // reserved a founding slot at a prior founding checkout
        subscriberRepository.save(sub);

        String eventId = "evt_reserved_then_normal_1";
        // The COMPLETED session is normal-price: foundingRate metadata = false.
        String payload = checkoutSessionPayload(eventId, String.valueOf(sub.getId()), "1", "false");

        postSignedWebhook(payload);

        Subscriber updated = subscriberRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriberStatus.ACTIVE);
        // Reconciled to false — the slot is released, matching the normal price billed.
        assertThat(updated.isFoundingRate()).isFalse();
        assertThat(updated.getFoundingRateExpiresAt()).isNull();
    }

    // ── Helpers: payload builders ──────────────────────────────────────────────

    /**
     * Builds a minimal {@code checkout.session.completed} (mode=subscription) JSON payload.
     *
     * <p>The Stripe SDK verifies only the HMAC signature in {@code Webhook.constructEvent}.
     * The typed deserialization of {@code data.object} into a {@link com.stripe.model.checkout.Session}
     * happens inside {@code StripeWebhookService} via the SDK's GSON deserializer, which
     * reads the {@code object} discriminator field to select the right class.
     */
    private String checkoutSessionPayload(String eventId, String subscriberId,
                                          String planTierId, String foundingRate) {
        return """
                {"id":"%s","object":"event","type":"checkout.session.completed",\
"data":{"object":{"id":"cs_test","object":"checkout.session","mode":"subscription",\
"customer":"cus_test","subscription":"sub_test",\
"metadata":{"subscriberId":"%s","planTierId":"%s","foundingRate":"%s"}}}}"""
                .formatted(eventId, subscriberId, planTierId, foundingRate);
    }

    /**
     * Builds a minimal invoice event payload.
     * The handler resolves the subscriber by subscription id then falls back to customer id.
     */
    private String invoicePayload(String eventId, String eventType,
                                   String subscriptionId, String customerId) {
        return """
                {"id":"%s","object":"event","type":"%s",\
"data":{"object":{"id":"in_test","object":"invoice",\
"subscription":"%s","customer":"%s","status":"open"}}}"""
                .formatted(eventId, eventType, subscriptionId, customerId);
    }

    /**
     * Builds a minimal {@code customer.subscription.*} event payload.
     * The handler resolves the subscriber by {@code data.object.id} (the Stripe subscription id).
     */
    private String subscriptionPayload(String eventId, String eventType,
                                        String subscriptionId, String customerId) {
        long periodStart = System.currentTimeMillis() / 1000L;
        long periodEnd   = periodStart + 30L * 24 * 3600;
        return """
                {"id":"%s","object":"event","type":"%s",\
"data":{"object":{"id":"%s","object":"subscription","customer":"%s","status":"active",\
"current_period_start":%d,"current_period_end":%d,\
"items":{"object":"list","data":[{"id":"si_test","object":"subscription_item",\
"price":{"id":"price_test","object":"price","recurring":{"interval":"month","interval_count":1}}}]}}}}"""
                .formatted(eventId, eventType, subscriptionId, customerId, periodStart, periodEnd);
    }

    // ── Helpers: seed data ────────────────────────────────────────────────────

    /**
     * Seeds a {@code PENDING_ACTIVATION} subscriber with no Stripe ids set.
     * Used for {@code checkout.session.completed} tests.
     */
    private Subscriber seedPendingSubscriber(String email) {
        return seedSubscriberWithStatus(email, SubscriberStatus.PENDING_ACTIVATION);
    }

    /**
     * Seeds an {@code ACTIVE} subscriber whose Stripe customer and subscription ids are
     * set to deterministic values ({@code cus_<id>} / {@code sub_<id>}).
     * Used for invoice and subscription webhook handler tests.
     */
    private Subscriber seedActiveSubscriber(String email) {
        Subscriber sub = seedSubscriberWithStatus(email, SubscriberStatus.ACTIVE);
        sub.setStripeCustomerId("cus_" + sub.getId());
        sub.setStripeSubscriptionId("sub_" + sub.getId());
        return subscriberRepository.save(sub);
    }

    /**
     * Creates a user + property + subscriber row in the given status and tracks all created
     * ids for FK-safe teardown.
     *
     * <p>For non-{@code PENDING_ACTIVATION} statuses, deterministic Stripe ids are set so
     * that invoice / subscription webhook handlers can find the subscriber by those ids.
     */
    private Subscriber seedSubscriberWithStatus(String email, SubscriberStatus status) {
        long nano = System.nanoTime();

        User user = userRepository.save(new User(
                email + "." + nano,
                passwordEncoder.encode("placeholder"),
                "Test", "Webhook",
                Role.CUSTOMER, UserStatus.PENDING_ACTIVATION));
        createdUserIds.add(user.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Webhook St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                status, BillingCycle.MONTHLY);

        // For statuses reachable after checkout, set Stripe ids so the handlers find the row.
        if (status != SubscriberStatus.PENDING_ACTIVATION) {
            // Use a nano-based id here; the real Stripe id is set by seedActiveSubscriber
            // for ACTIVE seeds, or kept here for other statuses.
            sub.setStripeCustomerId("cus_seed_" + nano);
            sub.setStripeSubscriptionId("sub_seed_" + nano);
        }

        sub = subscriberRepository.save(sub);
        createdSubscriberIds.add(sub.getId());
        return sub;
    }

    // ── Helpers: assertions ───────────────────────────────────────────────────

    /**
     * Signs the payload with the test webhook secret and POSTs it to the webhook endpoint.
     * Asserts HTTP 200 (handled, ignored, or duplicate — Stripe must not retry).
     *
     * <p>The {@code Stripe-Signature} header format is:
     * {@code t=<unix_seconds>,v1=<hmac_sha256(secret, t + "." + payload)>}.
     * We use the current wall-clock second so the 300-second Stripe timestamp tolerance
     * (default in {@code Webhook.constructEvent}) is never exceeded.
     */
    private void postSignedWebhook(String payload) throws Exception {
        long t = System.currentTimeMillis() / 1000L;
        String signed = t + "." + payload;
        String v1;
        try {
            v1 = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signed);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed in test", e);
        }
        String sigHeader = "t=" + t + ",v1=" + v1;

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sigHeader)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private long countAllSubscriptionEvents() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM subscription_event", Long.class);
        return count != null ? count : 0L;
    }
}
