package com.homekept.dashboard;

import com.homekept.AbstractIntegrationTest;
import com.homekept.booking.BookingRateLimiter;
import com.homekept.catalog.PlanCode;
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
import com.homekept.visit.Visit;
import com.homekept.visit.VisitRepository;
import com.homekept.visit.VisitType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminDashboardController} ({@code GET /api/admin/dashboard}).
 *
 * <p>Runs against a real Postgres via Testcontainers. Because the dashboard aggregates
 * across the whole database, most correctness tests compare a "before" reading to an
 * "after" reading around a single seeded change, rather than asserting an absolute value
 * (which would be brittle against other data in the shared test database).
 *
 * <p>Covers:
 * <ul>
 *   <li>GET as ADMIN → 200 with all five metric fields present.</li>
 *   <li>GET as CUSTOMER → 403; anonymous → 401.</li>
 *   <li>A new ACTIVE subscriber increases {@code activeSubscribers} by 1 and
 *       {@code mrrCents} by the plan's monthly price.</li>
 *   <li>A new founding-rate subscriber decreases {@code foundingRateSlotsRemaining} by 1.</li>
 *   <li>A new PENDING walk-through booking increases {@code pendingWalkthroughs} by 1.</li>
 *   <li>A new future SCHEDULED visit increases {@code upcomingVisits} by 1.</li>
 * </ul>
 */
class AdminDashboardIntegrationTest extends AbstractIntegrationTest {

    private static final String DASHBOARD_URL   = "/api/admin/dashboard";
    private static final String WALKTHROUGH_URL = "/api/bookings/walkthrough";

    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired BookingRateLimiter bookingRateLimiter;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();
        bookingRateLimiter.reset("127.0.0.1");

        User adminUser = userRepository.save(new User(
                "admin-dash-admin-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Admin", "Dash",
                Role.ADMIN, UserStatus.ACTIVE));
        adminToken = loginAs(adminUser.getEmail(), "Test1234!");

        User customerUser = userRepository.save(new User(
                "admin-dash-cust-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Customer", "Dash",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    // ── Role gating ───────────────────────────────────────────────────────────

    @Test
    void getDashboard_anonymous_returns401() throws Exception {
        mockMvc.perform(get(DASHBOARD_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboard_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(DASHBOARD_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_asAdmin_returns200WithAllFields() throws Exception {
        mockMvc.perform(get(DASHBOARD_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSubscribers").isNumber())
                .andExpect(jsonPath("$.mrrCents").isNumber())
                .andExpect(jsonPath("$.pendingWalkthroughs").isNumber())
                .andExpect(jsonPath("$.upcomingVisits").isNumber())
                .andExpect(jsonPath("$.foundingRateSlotsRemaining").isNumber());
    }

    // ── Aggregate correctness ─────────────────────────────────────────────────

    @Test
    void getDashboard_activeSubscriber_increasesActiveCountAndMrr() throws Exception {
        long baselineActive = readLong("$.activeSubscribers");
        long baselineMrr = readLong("$.mrrCents");

        Long planTierId = essentialPlanTierId();
        Integer monthlyPriceCents = jdbc.queryForObject(
                "SELECT monthly_price_cents FROM plan_tier WHERE id = ?", Integer.class, planTierId);

        seedActiveSubscriber(planTierId, false);

        long afterActive = readLong("$.activeSubscribers");
        long afterMrr = readLong("$.mrrCents");

        assertThat(afterActive).isEqualTo(baselineActive + 1);
        assertThat(afterMrr).isEqualTo(baselineMrr + monthlyPriceCents);
    }

    @Test
    void getDashboard_foundingRateSubscriber_decreasesSlotsRemaining() throws Exception {
        long baselineSlots = readLong("$.foundingRateSlotsRemaining");

        seedActiveSubscriber(essentialPlanTierId(), true);

        long afterSlots = readLong("$.foundingRateSlotsRemaining");
        assertThat(afterSlots).isEqualTo(baselineSlots - 1);
    }

    @Test
    void getDashboard_pendingWalkthrough_increasesCount() throws Exception {
        long baseline = readLong("$.pendingWalkthroughs");

        createPendingBooking();

        long after = readLong("$.pendingWalkthroughs");
        assertThat(after).isEqualTo(baseline + 1);
    }

    @Test
    void getDashboard_futureScheduledVisit_increasesUpcomingVisits() throws Exception {
        long baseline = readLong("$.upcomingVisits");

        Subscriber sub = seedActiveSubscriber(essentialPlanTierId(), false);

        visitRepository.save(new Visit(
                sub.getId(), sub.getPropertyId(), null,
                Instant.now().plus(10, ChronoUnit.DAYS), 120, VisitType.ROUTINE));

        long after = readLong("$.upcomingVisits");
        assertThat(after).isEqualTo(baseline + 1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long readLong(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(DASHBOARD_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        Number n = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return n.longValue();
    }

    private Long essentialPlanTierId() {
        Long id = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, PlanCode.ESSENTIAL.name());
        if (id == null) {
            throw new IllegalStateException("ESSENTIAL plan tier not seeded");
        }
        return id;
    }

    private Subscriber seedActiveSubscriber(Long planTierId, boolean foundingRate) {
        long nano = System.nanoTime();

        User user = userRepository.save(new User(
                "admin-dash-sub-" + nano + "@test.local",
                passwordEncoder.encode("placeholder"),
                "Dash", "Subscriber",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Dashboard Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        Subscriber sub = new Subscriber(user.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        sub.setPlanTierId(planTierId);
        sub.setFoundingRate(foundingRate);
        return subscriberRepository.save(sub);
    }

    private void createPendingBooking() throws Exception {
        String body = """
                {
                  "fullName": "Dashboard Test",
                  "email": "dash-pending-%d@test.local",
                  "phone": "(905) 555-0123",
                  "streetAddress": "14 Maple Ridge Crt",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "AFTERNOON",
                  "contactConsent": true
                }
                """.formatted(System.nanoTime());

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
