package com.homekept;

import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.analytics.AnalyticsEvent;
import com.homekept.catalog.PlanCode;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.CheckoutService;
import com.homekept.subscription.FoundingRateAvailabilityImpl;
import com.homekept.subscription.FoundingRateExhaustedException;
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
 * <p>Runs against a real Postgres via Testcontainers. Teardown follows the FK-safe
 * order from {@link ActivationIntegrationTest}: subscription_event → subscriber →
 * property → user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeStripeServiceConfig.class, RecordingAnalyticsConfig.class})
class CheckoutControllerIntegrationTest {

    private static final String CHECKOUT_SESSION_URL = "/api/checkout/session";
    private static final String PORTAL_SESSION_URL   = "/api/billing/portal-session";
    private static final String LOGIN_URL            = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutService checkoutService;
    @Autowired RecordingAnalyticsService recording;

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds       = new ArrayList<>();

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
        // (pre-payment) below, which is what the founding-cap/checkout logic actually reads.
        customerUser = userRepository.save(new User(
                "checkout-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Test", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        Property prop = propertyRepository.save(new Property(
                nano + " Checkout Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(prop.getId());

        customerSubscriber = new Subscriber(
                customerUser.getId(), prop.getId(),
                SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
        customerSubscriber = subscriberRepository.save(customerSubscriber);
        createdSubscriberIds.add(customerSubscriber.getId());

        customerAccessToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        // subscription_event rows reference subscriber_id — delete them first.
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

    // ── POST /api/checkout/session — happy path ───────────────────────────────

    @Test
    void createCheckoutSession_asCustomer_returns200WithCheckoutUrl() throws Exception {
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeStripeServiceConfig.FAKE_CHECKOUT_URL));

        // Analytics: checkout_started fired, attributed to the customer, enum/flag props only.
        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.CHECKOUT_STARTED);
            assertThat(e.distinctId()).isEqualTo(customerUser.getId());
            assertThat(e.props()).containsEntry("plan_code", "COMPLETE");
            assertThat(e.props()).containsEntry("billing_cycle", "MONTHLY");
            assertThat(e.props()).containsEntry("founding_rate", false);
        });
    }

    // ── POST /api/checkout/session — role gating ──────────────────────────────

    @Test
    void createCheckoutSession_anonymous_returns401() throws Exception {
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCheckoutSession_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/checkout/session — founding rate cap ────────────────────────

    @Test
    void createCheckoutSession_foundingRate_whenCapReached_returns409() throws Exception {
        // Fill all 15 founding-member slots.
        insertFoundingSubscribers(15);

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOUNDING_RATE_UNAVAILABLE"));
    }

    @Test
    void createCheckoutSession_foundingRate_whenSlotsAvailable_returns200() throws Exception {
        // COMPLETE plan has a founding price; no founding subscribers seeded in this test.
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(FakeStripeServiceConfig.FAKE_CHECKOUT_URL));
    }

    // ── POST /api/checkout/session — status gate (B1) ─────────────────────────

    @Test
    void createCheckoutSession_whenSubscriberCancelled_returns409_ineligibleForCheckout() throws Exception {
        // A churned customer still has a login + a terminal CANCELLED subscriber row. Re-checkout
        // on that row would let Stripe charge them while the webhook can never activate a terminal
        // row (money taken, no service). Checkout must be refused before any Stripe call — a
        // returning customer is a NEW subscriber row (see SubscriberStatus).
        customerSubscriber.setStatus(SubscriberStatus.CANCELLED);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void createCheckoutSession_whenSubscriberAlreadyActive_returns409() throws Exception {
        // An ACTIVE subscriber already has a live Stripe subscription; a second checkout would
        // create a duplicate (double billing). Plan/billing changes go through the billing portal.
        customerSubscriber.setStatus(SubscriberStatus.ACTIVE);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── POST /api/checkout/session — founding slot claimed at checkout (B2) ────

    @Test
    void createCheckoutSession_foundingRate_claimsSlotDurablyAtCheckout_beforeAnyWebhook() throws Exception {
        // B2: the founding slot must be claimed at checkout — before the founding price id is ever
        // committed to Stripe — so concurrent checkouts cannot oversell the discount. Proven by
        // founding_rate=true persisting on the row immediately after checkout, with NO webhook yet.
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":true}"))
                .andExpect(status().isOk());

        Subscriber after = subscriberRepository.findById(customerSubscriber.getId()).orElseThrow();
        assertThat(after.isFoundingRate()).isTrue();
    }

    @Test
    void createCheckoutSession_nonFounding_doesNotClaimFoundingSlot() throws Exception {
        // Control: a non-founding checkout must NOT claim a founding slot.
        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":false}"))
                .andExpect(status().isOk());

        Subscriber after = subscriberRepository.findById(customerSubscriber.getId()).orElseThrow();
        assertThat(after.isFoundingRate()).isFalse();
    }

    @Test
    void createCheckoutSession_foundingRate_reClaimByExistingHolder_atCap_stillSucceeds() throws Exception {
        // MED regression: the caller's own reservation counts toward the cap. If the customer
        // already holds a founding slot (e.g. re-opening an expired checkout) and 14 OTHERS
        // hold the remaining slots (15 total), a re-checkout must NOT 409 the holder out of
        // their own slot — the claim is idempotent for a row that already holds one.
        customerSubscriber.setFoundingRate(true);
        subscriberRepository.save(customerSubscriber);
        insertFoundingSubscribers((int) (FoundingRateAvailabilityImpl.FOUNDING_CAP - 1)); // 14 others => 15 total

        mockMvc.perform(post(CHECKOUT_SESSION_URL)
                        .cookie(new Cookie("hk_access", customerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"COMPLETE\",\"billingCycle\":\"MONTHLY\",\"foundingRate\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void createCheckoutSession_twoConcurrentFoundingClaims_withOneSlotLeft_onlyOneWins() throws Exception {
        // B2 concurrency guard: 14 slots taken (1 left). Two simultaneous founding checkouts
        // for two different PENDING customers must not both claim it. The advisory lock
        // serialises the count-then-claim, so exactly one succeeds, one is rejected, and the
        // founding count settles at exactly the cap.
        insertFoundingSubscribers((int) (FoundingRateAvailabilityImpl.FOUNDING_CAP - 1)); // 14 taken

        Long userA = newPendingCustomer("concurrent-a");
        Long userB = newPendingCustomer("concurrent-b");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch fire = new java.util.concurrent.CountDownLatch(1);
        java.util.function.Function<Long, java.util.concurrent.Callable<Boolean>> claimFor = uid -> () -> {
            fire.await();
            try {
                checkoutService.createCheckoutSession(uid, PlanCode.COMPLETE, BillingCycle.MONTHLY, true);
                return Boolean.TRUE;   // claimed the slot
            } catch (FoundingRateExhaustedException e) {
                return Boolean.FALSE;  // correctly rejected
            }
        };

        var fa = pool.submit(claimFor.apply(userA));
        var fb = pool.submit(claimFor.apply(userB));
        fire.countDown(); // release both at once

        boolean aWon = fa.get();
        boolean bWon = fb.get();
        pool.shutdown();

        // Exactly one of the two concurrent claims won.
        assertThat(aWon ^ bWon).isTrue();
        // And the cap is exactly full — never exceeded.
        assertThat(subscriberRepository.countByFoundingRateTrue())
                .isEqualTo(FoundingRateAvailabilityImpl.FOUNDING_CAP);
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
     * Logs in with the given credentials and returns the {@code hk_access} cookie value.
     */
    private String loginAs(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return extractCookieValue(result.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }

    /**
     * Creates a fresh ADMIN user, logs in, and returns the access token.
     * The admin user is tracked for teardown.
     */
    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "checkout-admin-" + nano + "@test.local";
        User admin = userRepository.save(new User(
                email,
                passwordEncoder.encode("Test1234!"),
                "Admin", "Test",
                Role.ADMIN, UserStatus.ACTIVE));
        createdUserIds.add(admin.getId());
        return loginAs(email, "Test1234!");
    }

    /**
     * Inserts {@code count} founding subscribers (founding_rate=true) directly via
     * repositories. Mirrors the helper in {@link FoundingRateIntegrationTest}.
     */
    private void insertFoundingSubscribers(int count) {
        for (int i = 0; i < count; i++) {
            long nano = System.nanoTime();

            User user = userRepository.save(new User(
                    "founding-checkout-" + nano + "@test.local",
                    passwordEncoder.encode("placeholder"),
                    "Founding", "Stub",
                    Role.CUSTOMER, UserStatus.PENDING_ACTIVATION));
            createdUserIds.add(user.getId());

            Property property = propertyRepository.save(new Property(
                    nano + " Founding Checkout St", null, "Mississauga", "L5L 1A1",
                    "L5L", null, null, PropertyType.DETACHED));
            createdPropertyIds.add(property.getId());

            Subscriber sub = new Subscriber(
                    user.getId(), property.getId(),
                    SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
            sub.setFoundingRate(true);
            sub = subscriberRepository.save(sub);
            createdSubscriberIds.add(sub.getId());
        }
    }

    /**
     * Creates a CUSTOMER user + property + PENDING_ACTIVATION subscriber and returns the
     * user id (the argument {@code CheckoutService.createCheckoutSession} resolves by).
     * Used by the concurrency test, which calls the service directly on two threads.
     */
    private Long newPendingCustomer(String tag) {
        long nano = System.nanoTime();
        User user = userRepository.save(new User(
                tag + "-" + nano + "@test.local",
                passwordEncoder.encode("placeholder"),
                "Test", "Concurrent",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(user.getId());

        Property prop = propertyRepository.save(new Property(
                nano + " Concurrent St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(prop.getId());

        Subscriber sub = new Subscriber(user.getId(), prop.getId(),
                SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
        sub = subscriberRepository.save(sub);
        createdSubscriberIds.add(sub.getId());
        return user.getId();
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie '" + name + "' not found in Set-Cookie headers"));
    }
}
