package com.homekept;

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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for admin control of a subscriber's subscription:
 * {@code POST /api/admin/subscribers/{id}/cancel|pause|resume} and
 * {@code GET /api/admin/subscribers/{id}/events}.
 *
 * <p>Imports {@link FakeStripeServiceConfig} so no live Stripe API calls are made. The actual
 * PAUSED/CANCELLED status transition is driven by webhooks (covered by
 * {@link StripeWebhookIntegrationTest}) — these tests assert the response still reports the
 * <em>current</em> status, and that the controller reached the correct Stripe seam.
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@Import(FakeStripeServiceConfig.class)
class AdminSubscriptionLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String STRIPE_SUB_ID = "sub_test_admin_lifecycle_1";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeServiceConfig.RecordingStripeService recordingStripe;

    private Subscriber subscriber;
    private String adminToken;

    @BeforeEach
    void seedSubscriber() throws Exception {
        recordingStripe.reset();
        long nano = System.nanoTime();

        User customerUser = userRepository.save(new User(
                "admin-lifecycle-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Test", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property prop = propertyRepository.save(new Property(
                nano + " Admin Lifecycle Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        subscriber = new Subscriber(
                customerUser.getId(), prop.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        subscriber.setStripeCustomerId("cus_test_admin_lifecycle_1");
        subscriber.setStripeSubscriptionId(STRIPE_SUB_ID);
        subscriber = subscriberRepository.save(subscriber);

        adminToken = loginAs(Role.ADMIN);
    }

    private String cancelUrl(Long id) { return "/api/admin/subscribers/" + id + "/cancel"; }
    private String pauseUrl(Long id) { return "/api/admin/subscribers/" + id + "/pause"; }
    private String resumeUrl(Long id) { return "/api/admin/subscribers/" + id + "/resume"; }
    private String eventsUrl(Long id) { return "/api/admin/subscribers/" + id + "/events"; }

    private Cookie adminCookie() { return new Cookie("hk_access", adminToken); }

    private void setStatus(SubscriberStatus status) {
        subscriber.setStatus(status);
        subscriber = subscriberRepository.save(subscriber);
    }

    // ── cancel ──────────────────────────────────────────────────────────────────

    @Test
    void cancel_atPeriodEnd_returns200_recordsManualEvent_andCallsStripeAtPeriodEnd() throws Exception {
        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer called to cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(recordingStripe.cancelledSubscriptionIds).containsExactly(STRIPE_SUB_ID);
        assertThat(recordingStripe.cancelledNowSubscriptionIds).isEmpty();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, source, payload::text AS payload FROM subscription_event "
                        + "WHERE subscriber_id = ? AND event_type = 'CANCELLATION_REQUESTED'",
                subscriber.getId());
        assertThat(row.get("source")).isEqualTo("MANUAL");
        assertThat(String.valueOf(row.get("payload"))).contains("Customer called to cancel");
        assertThat(String.valueOf(row.get("payload"))).contains("\"by\"").contains("ADMIN");
    }

    @Test
    void cancel_immediately_returns200_andCallsStripeCancelNow() throws Exception {
        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Refund requested\",\"immediately\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(recordingStripe.cancelledNowSubscriptionIds).containsExactly(STRIPE_SUB_ID);
        assertThat(recordingStripe.cancelledSubscriptionIds).isEmpty();
    }

    @Test
    void cancel_alreadyCancelled_returns409() throws Exception {
        setStatus(SubscriberStatus.CANCELLED);

        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.cancelledSubscriptionIds).isEmpty();
        assertThat(recordingStripe.cancelledNowSubscriptionIds).isEmpty();
    }

    @Test
    void cancel_noStripeSubscription_returns409NoBillingAccount() throws Exception {
        subscriber.setStripeSubscriptionId(null);
        subscriberRepository.save(subscriber);

        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No billing account\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_BILLING_ACCOUNT"));
    }

    @Test
    void cancel_unknownSubscriber_returns404() throws Exception {
        mockMvc.perform(post(cancelUrl(999999999L))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Whoever this is\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_asCustomer_returns403() throws Exception {
        String customerToken = loginAs(Role.CUSTOMER);

        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ── pause ───────────────────────────────────────────────────────────────────

    @Test
    void pause_activeSubscriber_returns200_andCallsStripe() throws Exception {
        mockMvc.perform(post(pauseUrl(subscriber.getId())).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(recordingStripe.pausedSubscriptionIds).containsExactly(STRIPE_SUB_ID);
    }

    @Test
    void pause_whenAlreadyPaused_returns409() throws Exception {
        setStatus(SubscriberStatus.PAUSED);

        mockMvc.perform(post(pauseUrl(subscriber.getId())).cookie(adminCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.pausedSubscriptionIds).isEmpty();
    }

    // ── resume ──────────────────────────────────────────────────────────────────

    @Test
    void resume_pausedSubscriber_returns200_andCallsStripe() throws Exception {
        setStatus(SubscriberStatus.PAUSED);

        mockMvc.perform(post(resumeUrl(subscriber.getId())).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        assertThat(recordingStripe.resumedSubscriptionIds).containsExactly(STRIPE_SUB_ID);
    }

    @Test
    void resume_whenActive_returns409() throws Exception {
        mockMvc.perform(post(resumeUrl(subscriber.getId())).cookie(adminCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.resumedSubscriptionIds).isEmpty();
    }

    // ── events ──────────────────────────────────────────────────────────────────

    @Test
    void listEvents_returnsCancellationEventNewestFirst_withNote() throws Exception {
        mockMvc.perform(post(cancelUrl(subscriber.getId()))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Moving out of the service area\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(eventsUrl(subscriber.getId())).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("CANCELLATION_REQUESTED"))
                .andExpect(jsonPath("$[0].source").value("MANUAL"))
                .andExpect(jsonPath("$[0].occurredAt").exists())
                .andExpect(jsonPath("$[0].note").value("Moving out of the service area"));
    }

    @Test
    void listEvents_unknownSubscriber_returns404() throws Exception {
        mockMvc.perform(get(eventsUrl(999999999L)).cookie(adminCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listEvents_asCustomer_returns403() throws Exception {
        String customerToken = loginAs(Role.CUSTOMER);

        mockMvc.perform(get(eventsUrl(subscriber.getId()))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }
}
