package com.homekept.visit;

import com.homekept.AbstractIntegrationTest;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Home Health Score v1 (#53): the rubric (open-flag penalties +
 * checklist-completion deduction), the delta against the prior snapshot, and
 * {@code GET /api/app/health-score}. Runs against real Postgres via Testcontainers.
 */
class HealthScoreIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/app/health-score";

    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired FlagRepository flagRepository;
    @Autowired HealthScoreService healthScoreService;
    @Autowired JdbcTemplate jdbc;

    private Subscriber subscriber;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void seed() throws Exception {
        long nano = System.nanoTime();
        User customer = userRepository.save(new User(
                "hs-cust-" + nano + "@test.local", passwordEncoder.encode("Cust1234!"),
                "Health", "Customer", Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Health Rd", null, "Mississauga", "L5L 1A1", "L5L", null, null, PropertyType.DETACHED));

        subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        customerToken = loginAs(customer.getEmail(), "Cust1234!");
        adminToken = loginAsNewAdmin();
    }

    // ── Rubric ──────────────────────────────────────────────────────────────────

    @Test
    void noFlags_allChecklistDone_returns100() throws Exception {
        seedCompletedVisit(2, 2); // 2 checklist items, both done

        mockMvc.perform(get(URL).cookie(cookie(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.delta").value(0))
                .andExpect(jsonPath("$.flagged.length()").value(0));
    }

    @Test
    void openFlags_reduceScoreBySeverity_andListFlagged() throws Exception {
        seedFlag(FlagSeverity.URGENT);    // -20
        seedFlag(FlagSeverity.ATTENTION); // -10
        seedFlag(FlagSeverity.INFO);      // -3

        mockMvc.perform(get(URL).cookie(cookie(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(67))  // 100 - 33
                .andExpect(jsonPath("$.flagged.length()").value(3));
    }

    @Test
    void incompleteChecklist_deductsProportionally() throws Exception {
        seedCompletedVisit(2, 1); // 1 of 2 done → round(15 * 0.5) = 8

        mockMvc.perform(get(URL).cookie(cookie(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(92));
    }

    @Test
    void delta_reflectsPriorSnapshot() throws Exception {
        seedCompletedVisit(2, 2);                 // current score would be 100
        healthScoreService.snapshotOnCompletion(subscriber.getId()); // snapshot at 100
        seedFlag(FlagSeverity.URGENT);            // now current score = 80

        mockMvc.perform(get(URL).cookie(cookie(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.delta").value(-20)); // 80 - 100
    }

    @Test
    void noVisitsNoFlags_returns100() throws Exception {
        mockMvc.perform(get(URL).cookie(cookie(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100));
    }

    // ── Authz ─────────────────────────────────────────────────────────────────

    @Test
    void anonymous_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void asAdmin_returns403() throws Exception {
        mockMvc.perform(get(URL).cookie(cookie(adminToken))).andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void seedCompletedVisit(int totalItems, int completedItems) {
        Visit visit = new Visit(subscriber.getId(), subscriber.getPropertyId(), null,
                Instant.now().minus(2, ChronoUnit.DAYS), 120, VisitType.ROUTINE);
        visit.setStatus(VisitStatus.COMPLETED);
        visit = visitRepository.save(visit);

        Long serviceId = firstServiceId();
        for (int i = 0; i < totalItems; i++) {
            VisitService vs = new VisitService(visit.getId(), serviceId, VisitServiceSource.TEMPLATE);
            vs.setCompleted(i < completedItems);
            visitServiceRepository.save(vs);
        }
    }

    private void seedFlag(FlagSeverity severity) {
        flagRepository.save(new Flag(subscriber.getId(), null, "Observed issue", severity));
    }

    private Long firstServiceId() {
        return jdbc.queryForObject("SELECT id FROM service ORDER BY id LIMIT 1", Long.class);
    }

    private Cookie cookie(String token) {
        return new Cookie("hk_access", token);
    }

    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "hs-admin-" + nano + "@test.local";
        userRepository.save(new User(
                email, passwordEncoder.encode("Admin1234!"),
                "Admin", "Test", Role.ADMIN, UserStatus.ACTIVE));
        return loginAs(email, "Admin1234!");
    }
}
