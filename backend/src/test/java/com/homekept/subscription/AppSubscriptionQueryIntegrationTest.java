package com.homekept.subscription;

import com.homekept.TestcontainersConfiguration;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.visit.Visit;
import com.homekept.visit.VisitRepository;
import com.homekept.visit.VisitType;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SubscriptionController}'s read endpoints:
 * {@code GET /api/app/subscription} and {@code GET /api/app/account}.
 *
 * <p>Covers:
 * <ul>
 *   <li>An ACTIVE subscriber sees their plan, price, status, and next visit date.</li>
 *   <li>Founding-rate pricing and annual-cycle pricing resolve correctly.</li>
 *   <li>A subscriber with no plan tier assigned yet (pre-checkout) gets null plan fields.</li>
 *   <li>A user with no subscriber row → 404 (ownership rule, matches {@code /api/app/visits}).</li>
 *   <li>Anonymous → 401; ADMIN on a CUSTOMER endpoint → 403.</li>
 * </ul>
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppSubscriptionQueryIntegrationTest {

    private static final String SUBSCRIPTION_URL = "/api/app/subscription";
    private static final String ACCOUNT_URL = "/api/app/account";
    private static final String LOGIN_URL = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds = new ArrayList<>();
    private final List<Long> createdVisitIds = new ArrayList<>();

    private User customerUser;
    private Property customerProperty;
    private Subscriber customerSubscriber;
    private String customerToken;

    @BeforeEach
    void seedCustomer() throws Exception {
        long nano = System.nanoTime();

        customerUser = userRepository.save(new User(
                "app-sub-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Priya", "Sharma",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        customerProperty = propertyRepository.save(new Property(
                nano + " Maple Ridge Crt", "Unit 4", "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(customerProperty.getId());

        customerSubscriber = new Subscriber(
                customerUser.getId(), customerProperty.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        customerSubscriber = subscriberRepository.save(customerSubscriber);
        createdSubscriberIds.add(customerSubscriber.getId());

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM visit WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM subscription_event WHERE subscriber_id = ?", subId);
        }
        for (Long subId : createdSubscriberIds) {
            subscriberRepository.deleteById(subId);
        }
        createdSubscriberIds.clear();

        for (Long propId : createdPropertyIds) {
            propertyRepository.deleteById(propId);
        }
        createdPropertyIds.clear();

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
        createdVisitIds.clear();
    }

    // ── GET /api/app/subscription ────────────────────────────────────────────

    @Test
    void getSubscription_activeSubscriberWithPlan_returns200WithPlanAndPrice() throws Exception {
        assignPlan(customerSubscriber, "COMPLETE");
        customerSubscriber.setCurrentPeriodStart(Instant.now());
        customerSubscriber.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planCode").value("COMPLETE"))
                .andExpect(jsonPath("$.planDisplayName").value("Complete"))
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.priceCents").value(14900))
                .andExpect(jsonPath("$.foundingRate").value(false))
                .andExpect(jsonPath("$.currentPeriodEnd").exists());
    }

    @Test
    void getSubscription_annualBillingCycle_returnsAnnualPrice() throws Exception {
        assignPlan(customerSubscriber, "COMPLETE");
        customerSubscriber.setBillingCycle(BillingCycle.ANNUAL);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.priceCents").value(149000));
    }

    @Test
    void getSubscription_foundingRate_returnsFoundingPriceRegardlessOfCycle() throws Exception {
        assignPlan(customerSubscriber, "COMPLETE");
        customerSubscriber.setFoundingRate(true);
        Instant expiresAt = Instant.now().plus(365, ChronoUnit.DAYS);
        customerSubscriber.setFoundingRateExpiresAt(expiresAt);
        subscriberRepository.save(customerSubscriber);

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foundingRate").value(true))
                .andExpect(jsonPath("$.priceCents").value(12900))
                .andExpect(jsonPath("$.foundingRateExpiresAt").exists());
    }

    @Test
    void getSubscription_noPlanAssignedYet_returnsNullPlanFields() throws Exception {
        // customerSubscriber has no planTierId set (pre-checkout PENDING_ACTIVATION path).
        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").doesNotExist())
                .andExpect(jsonPath("$.planDisplayName").doesNotExist())
                .andExpect(jsonPath("$.priceCents").doesNotExist());
    }

    @Test
    void getSubscription_withScheduledVisits_returnsSoonestAsNextVisitDate() throws Exception {
        Instant soonest = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        seedVisit(later);
        seedVisit(soonest);

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextVisitDate").value(soonest.toString()));
    }

    @Test
    void getSubscription_noScheduledVisits_nextVisitDateIsNull() throws Exception {
        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextVisitDate").doesNotExist());
    }

    @Test
    void getSubscription_noSubscriberRow_returns404() throws Exception {
        String token = createCustomerWithNoSubscriber();

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(new Cookie("hk_access", token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getSubscription_anonymous_returns401() throws Exception {
        mockMvc.perform(get(SUBSCRIPTION_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSubscription_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/app/account ─────────────────────────────────────────────────

    @Test
    void getAccount_ownAccount_returns200WithProfileAndAddress() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.lastName").value("Sharma"))
                .andExpect(jsonPath("$.email").value(customerUser.getEmail()))
                .andExpect(jsonPath("$.streetAddress").value(customerProperty.getStreetAddress()))
                .andExpect(jsonPath("$.unit").value("Unit 4"))
                .andExpect(jsonPath("$.city").value("Mississauga"))
                .andExpect(jsonPath("$.postalCode").value("L5L 1A1"));
    }

    @Test
    void getAccount_noSubscriberRow_returns404() throws Exception {
        String token = createCustomerWithNoSubscriber();

        mockMvc.perform(get(ACCOUNT_URL).cookie(new Cookie("hk_access", token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getAccount_anonymous_returns401() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccount_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(get(ACCOUNT_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assignPlan(Subscriber subscriber, String planCode) {
        Long planTierId = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, planCode);
        subscriber.setPlanTierId(planTierId);
        subscriberRepository.save(subscriber);
    }

    private Visit seedVisit(Instant scheduledFor) {
        Visit visit = visitRepository.save(new Visit(
                customerSubscriber.getId(),
                customerSubscriber.getPropertyId(),
                null,
                scheduledFor,
                120,
                VisitType.ROUTINE));
        createdVisitIds.add(visit.getId());
        return visit;
    }

    /**
     * Creates a second CUSTOMER-role user with no {@link Subscriber} row and logs them in.
     * Status is ACTIVE (not PENDING_ACTIVATION) purely so login succeeds — the point of
     * this fixture is "no subscriber row", which the real activation flow never actually
     * produces (User + Property + Subscriber are created together), but the subscription
     * app service must still defend against it (see {@link SubscriptionAppService}).
     */
    private String createCustomerWithNoSubscriber() throws Exception {
        long nano = System.nanoTime();
        String email = "app-sub-nosub-" + nano + "@test.local";
        User user = userRepository.save(new User(
                email,
                passwordEncoder.encode("Test1234!"),
                "No", "Subscriber",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(user.getId());
        return loginAs(email, "Test1234!");
    }

    private Cookie authCookie() {
        return new Cookie("hk_access", customerToken);
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
        String email = "app-sub-admin-" + nano + "@test.local";
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
