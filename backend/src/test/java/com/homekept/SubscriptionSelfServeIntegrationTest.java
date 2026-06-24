package com.homekept;

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
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.homekept.subscription.SubscriptionController}:
 * {@code POST /api/app/subscription/pause|resume|cancel}.
 *
 * <p>Imports {@link FakeStripeServiceConfig} so no live Stripe API calls are made; its
 * {@link FakeStripeServiceConfig.RecordingStripeService} records which subscription ids
 * were paused/resumed/cancelled so we can assert the controller reached the Stripe seam.
 * The actual PAUSED/CANCELLED status transition is driven by webhooks and is covered by
 * {@link StripeWebhookIntegrationTest} — so these tests assert the response still reports
 * the <em>current</em> status (the request is accepted, not yet applied).
 *
 * <p>Runs against a real Postgres via Testcontainers. Teardown follows the FK-safe order
 * subscription_event → subscriber → property → user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeStripeServiceConfig.class})
class SubscriptionSelfServeIntegrationTest {

    private static final String PAUSE_URL  = "/api/app/subscription/pause";
    private static final String RESUME_URL = "/api/app/subscription/resume";
    private static final String CANCEL_URL = "/api/app/subscription/cancel";
    private static final String LOGIN_URL  = "/api/auth/login";

    private static final String STRIPE_SUB_ID = "sub_test_selfserve_1";

    @Autowired MockMvc mockMvc;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeServiceConfig.RecordingStripeService recordingStripe;

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds        = new ArrayList<>();

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
        createdUserIds.add(customerUser.getId());

        Property prop = propertyRepository.save(new Property(
                nano + " Self Serve Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(prop.getId());

        // ACTIVE subscriber with a Stripe subscription id — the default billed state.
        customerSubscriber = new Subscriber(
                customerUser.getId(), prop.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        customerSubscriber.setStripeCustomerId("cus_test_selfserve_1");
        customerSubscriber.setStripeSubscriptionId(STRIPE_SUB_ID);
        customerSubscriber = subscriberRepository.save(customerSubscriber);
        createdSubscriberIds.add(customerSubscriber.getId());

        customerAccessToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdSubscriberIds) {
            jdbc.update("DELETE FROM subscription_event WHERE subscriber_id = ?", id);
        }
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

    // ── pause ─────────────────────────────────────────────────────────────────

    @Test
    void pause_activeSubscriber_returns200_andCallsStripe() throws Exception {
        mockMvc.perform(post(PAUSE_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                // status is still ACTIVE — the PAUSED transition lands via the webhook.
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(recordingStripe.pausedSubscriptionIds).containsExactly(STRIPE_SUB_ID);
    }

    @Test
    void pause_whenAlreadyPaused_returns409() throws Exception {
        setStatus(SubscriberStatus.PAUSED);

        mockMvc.perform(post(PAUSE_URL).cookie(authCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.pausedSubscriptionIds).isEmpty();
    }

    @Test
    void pause_noStripeSubscription_returns409NoBillingAccount() throws Exception {
        customerSubscriber.setStripeSubscriptionId(null);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(PAUSE_URL).cookie(authCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_BILLING_ACCOUNT"));

        assertThat(recordingStripe.pausedSubscriptionIds).isEmpty();
    }

    @Test
    void pause_anonymous_returns401() throws Exception {
        mockMvc.perform(post(PAUSE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pause_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(post(PAUSE_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── resume ──────────────────────────────────────────────────────────────────

    @Test
    void resume_pausedSubscriber_returns200_andCallsStripe() throws Exception {
        setStatus(SubscriberStatus.PAUSED);

        mockMvc.perform(post(RESUME_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        assertThat(recordingStripe.resumedSubscriptionIds).containsExactly(STRIPE_SUB_ID);
    }

    @Test
    void resume_whenActive_returns409() throws Exception {
        // Subscriber is ACTIVE (not paused) — resume is not valid.
        mockMvc.perform(post(RESUME_URL).cookie(authCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));

        assertThat(recordingStripe.resumedSubscriptionIds).isEmpty();
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

    private String loginAs(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(result.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }

    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "selfserve-admin-" + nano + "@test.local";
        User admin = userRepository.save(new User(
                email,
                passwordEncoder.encode("Test1234!"),
                "Admin", "Test",
                Role.ADMIN, UserStatus.ACTIVE));
        createdUserIds.add(admin.getId());
        return loginAs(email, "Test1234!");
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie '" + name + "' not found in Set-Cookie headers"));
    }
}
