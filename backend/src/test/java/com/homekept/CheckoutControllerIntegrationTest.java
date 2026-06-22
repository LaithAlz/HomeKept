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
@Import({TestcontainersConfiguration.class, FakeStripeServiceConfig.class})
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

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds       = new ArrayList<>();

    /** CUSTOMER user + subscriber shared across the checkout/portal tests. */
    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerAccessToken;

    @BeforeEach
    void seedCustomer() throws Exception {
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

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie '" + name + "' not found in Set-Cookie headers"));
    }
}
