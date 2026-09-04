package com.homekept;

import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.analytics.AnalyticsEvent;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.homekept.subscription.CheckoutController}:
 * {@code POST /api/checkout/session} and {@code POST /api/billing/portal-session}.
 *
 * <p>Imports {@link FakeStripeServiceConfig} so no live Stripe API calls are made.
 * The fake returns canned URLs ({@link FakeStripeServiceConfig#FAKE_CHECKOUT_URL} /
 * {@link FakeStripeServiceConfig#FAKE_PORTAL_URL}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@Import(FakeStripeServiceConfig.class)
class CheckoutControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String CHECKOUT_SESSION_URL = "/api/checkout/session";
    private static final String PORTAL_SESSION_URL   = "/api/billing/portal-session";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired RecordingAnalyticsService recording;
    @Autowired JdbcTemplate jdbc;

    /** CUSTOMER user + subscriber shared across the checkout/portal tests. */
    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerAccessToken;

    @BeforeEach
    void seedCustomer() throws Exception {
        recording.clear();
        long nano = System.nanoTime();

        // ACTIVE so loginAs (/api/auth/login) can authenticate. The checkout endpoint gates
        // on the CUSTOMER role, not user status; the subscriber stays PENDING_ACTIVATION
        // (pre-payment) below, which is what the checkout logic actually reads.
        customerUser = userRepository.save(new User(
                "checkout-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Test", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property prop = propertyRepository.save(new Property(
                nano + " Checkout Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        customerSubscriber = new Subscriber(
                customerUser.getId(), prop.getId(),
                SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
        customerSubscriber = subscriberRepository.save(customerSubscriber);

        customerAccessToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    /**
     * Restores COMPLETE's Stripe price ids after every test — V11 cleared them to NULL
     * pending the founder's new Stripe prices, so most tests here stamp placeholder ids
     * back in via {@link #stampCompletePriceIds()} to exercise the happy path; this
     * undoes that so state never leaks between tests (plan_tier is seed data, not
     * truncated by {@link AbstractIntegrationTest}).
     */
    @AfterEach
    void restoreCompletePriceIds() {
        jdbc.update("UPDATE plan_tier SET stripe_price_id_monthly = NULL, "
                + "stripe_price_id_annual = NULL WHERE code = 'COMPLETE'");
    }

    /**
     * COMPLETE's Stripe price ids were cleared to NULL by
     * V11__remove_essential_and_founding.sql (pending the founder's new Stripe prices).
     * Tests that exercise the checkout happy path stamp placeholder ids back in first.
     */
    private void stampCompletePriceIds() {
        jdbc.update("UPDATE plan_tier SET stripe_price_id_monthly = 'price_test_monthly', "
                + "stripe_price_id_annual = 'price_test_annual' WHERE code = 'COMPLETE'");
    }

    // ── POST /api/checkout/session — happy path ───────────────────────────────

    @Test
    void createCheckoutSession_asCustomer_returns200WithCheckoutUrl() throws Exception {
        stampCompletePriceIds();

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeStripeServiceConfig.FAKE_CHECKOUT_URL));

        // Analytics: checkout_started fired, attributed to the customer, enum props only.
        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.CHECKOUT_STARTED);
            assertThat(e.distinctId()).isEqualTo(customerUser.getId());
            assertThat(e.props()).containsEntry("plan_code", "COMPLETE");
            assertThat(e.props()).containsEntry("billing_cycle", "MONTHLY");
        });
    }

    // ── POST /api/checkout/session — role gating ──────────────────────────────

    @Test
    void createCheckoutSession_anonymous_returns401() throws Exception {
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCheckoutSession_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/checkout/session — fail closed on a missing Stripe price id ────

    @Test
    void createCheckoutSession_planWithNoStripePriceId_returns409PlanNotPurchasable() throws Exception {
        // COMPLETE's price ids are NULL after V11 (pending the founder's new Stripe
        // prices) — checkout must fail closed, never reach Stripe, never charge the old
        // $149 price.
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_PURCHASABLE"));

        // The block is not silent — it shows up in the funnel as checkout_blocked.
        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.CHECKOUT_BLOCKED);
            assertThat(e.distinctId()).isEqualTo(customerUser.getId());
            assertThat(e.props()).containsEntry("plan_code", "COMPLETE");
            assertThat(e.props()).containsEntry("billing_cycle", "MONTHLY");
            assertThat(e.props()).containsEntry("reason", "no_price");
        });
    }

    @Test
    void createCheckoutSession_annualCycleWithNoAnnualPriceId_returns409() throws Exception {
        // Only the monthly id is set — the annual cycle must still fail closed.
        jdbc.update("UPDATE plan_tier SET stripe_price_id_monthly = 'price_test_monthly' "
                + "WHERE code = 'COMPLETE'");

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"ANNUAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_PURCHASABLE"));
    }

    // ── POST /api/checkout/session — status gate (B1) ─────────────────────────

    @Test
    void createCheckoutSession_whenSubscriberCancelled_returns409_ineligibleForCheckout() throws Exception {
        // A churned customer still has a login + a terminal CANCELLED subscriber row. Re-checkout
        // on that row would let Stripe charge them while the webhook can never activate a terminal
        // row (money taken, no service). Checkout must be refused before any Stripe call — a
        // returning customer is a NEW subscriber row (see SubscriberStatus).
        stampCompletePriceIds();
        customerSubscriber.setStatus(SubscriberStatus.CANCELLED);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void createCheckoutSession_whenSubscriberAlreadyActive_returns409() throws Exception {
        // An ACTIVE subscriber already has a live Stripe subscription; a second checkout would
        // create a duplicate (double billing). Plan/billing changes go through the billing portal.
        stampCompletePriceIds();
        customerSubscriber.setStatus(SubscriberStatus.ACTIVE);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── POST /api/billing/portal-session ──────────────────────────────────────

    @Test
    void createPortalSession_noStripeCustomerId_returns409() throws Exception {
        // The seeded subscriber has no stripeCustomerId — checkout not completed.
        mockMvc.perform(post(PORTAL_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_BILLING_ACCOUNT"));
    }

    @Test
    void createPortalSession_withStripeCustomerId_returns200WithPortalUrl() throws Exception {
        // Simulate checkout.session.completed having fired by setting the Stripe customer id.
        customerSubscriber.setStripeCustomerId("cus_test_portal_1");
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(PORTAL_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalUrl").value(FakeStripeServiceConfig.FAKE_PORTAL_URL));
    }

    @Test
    void createPortalSession_anonymous_returns401() throws Exception {
        mockMvc.perform(post(PORTAL_SESSION_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPortalSession_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(post(PORTAL_SESSION_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a fresh ADMIN user and logs in, returning the access token.
     */
    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "checkout-admin-" + nano + "@test.local";
        userRepository.save(new User(
                email,
                passwordEncoder.encode("Test1234!"),
                "Admin", "Test",
                Role.ADMIN, UserStatus.ACTIVE));
        return loginAs(email, "Test1234!");
    }
}
