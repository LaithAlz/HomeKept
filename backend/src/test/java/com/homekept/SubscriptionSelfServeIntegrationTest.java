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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.homekept.subscription.SubscriptionController}:
 * {@code POST /api/app/subscription/cancel}.
 *
 * <p>Imports {@link FakeStripeServiceConfig} so no live Stripe API calls are made; its
 * {@link FakeStripeServiceConfig.RecordingStripeService} records which subscription ids
 * were cancelled so we can assert the controller reached the Stripe seam. The actual
 * CANCELLED status transition is driven by webhooks and is covered by
 * {@link StripeWebhookIntegrationTest} — so these tests assert the response still reports
 * the <em>current</em> status (the request is accepted, not yet applied).
 *
 * <p>There is no self-serve pause/resume — see
 * {@link com.homekept.subscription.SubscriptionSelfServeService}'s class javadoc.
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@Import(FakeStripeServiceConfig.class)
class SubscriptionSelfServeIntegrationTest extends AbstractIntegrationTest {

    private static final String CANCEL_URL = "/api/app/subscription/cancel";

    private static final String STRIPE_SUB_ID = "sub_test_selfserve_1";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeServiceConfig.RecordingStripeService recordingStripe;

    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerAccessToken;

    @BeforeEach
    void seedCustomer() throws Exception {
        recordingStripe.reset();
        long nano = System.nanoTime();

        customerUser = userRepository.save(new User(
                "selfserve-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Test", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property prop = propertyRepository.save(new Property(
                nano + " Self Serve Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        // ACTIVE subscriber with a Stripe subscription id — the default billed state.
        customerSubscriber = new Subscriber(
                customerUser.getId(), prop.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        customerSubscriber.setStripeCustomerId("cus_test_selfserve_1");
        customerSubscriber.setStripeSubscriptionId(STRIPE_SUB_ID);
        customerSubscriber = subscriberRepository.save(customerSubscriber);

        customerAccessToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    // ── cancel ──────────────────────────────────────────────────────────────────

    @Test
    void cancel_activeSubscriber_returns200_callsStripe_andRecordsChurnReason() throws Exception {
        mockMvc.perform(post(CANCEL_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too expensive for me right now\"}"))
                .andExpect(status().isOk())
                // cancel-at-period-end: status is still ACTIVE until the period actually ends.
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(recordingStripe.cancelledSubscriptionIds).containsExactly(STRIPE_SUB_ID);

        // Churn reason persisted as a MANUAL subscription_event with the reason in the payload.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, source, payload::text AS payload FROM subscription_event "
                        + "WHERE subscriber_id = ? AND event_type = 'CANCELLATION_REQUESTED'",
                customerSubscriber.getId());
        assertThat(row.get("source")).isEqualTo("MANUAL");
        assertThat(String.valueOf(row.get("payload"))).contains("Too expensive for me right now");
    }

    @Test
    void cancel_missingReason_returns400() throws Exception {
        mockMvc.perform(post(CANCEL_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        assertThat(recordingStripe.cancelledSubscriptionIds).isEmpty();
    }

    @Test
    void cancel_whenAlreadyCancelled_returns409() throws Exception {
        setStatus(SubscriberStatus.CANCELLED);

        mockMvc.perform(post(CANCEL_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.cancelledSubscriptionIds).isEmpty();
        // No churn row written when the cancel is rejected.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_event WHERE subscriber_id = ? "
                        + "AND event_type = 'CANCELLATION_REQUESTED'",
                Integer.class, customerSubscriber.getId());
        assertThat(count).isZero();
    }

    @Test
    void cancel_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(post(CANCEL_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cookie authCookie() {
        return new Cookie("hk_access", customerAccessToken);
    }

    private void setStatus(SubscriberStatus status) {
        customerSubscriber.setStatus(status);
        customerSubscriber = subscriberRepository.save(customerSubscriber);
    }

    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "selfserve-admin-" + nano + "@test.local";
        userRepository.save(new User(
                email,
                passwordEncoder.encode("Test1234!"),
                "Admin", "Test",
                Role.ADMIN, UserStatus.ACTIVE));
        return loginAs(email, "Test1234!");
    }
}
