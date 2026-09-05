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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/admin/visits/day-load} — the admin Routes
 * month-sidebar aggregate ({@link AdminVisitController#dayLoad}).
 *
 * <p>Covers: the shape and ordering of the aggregate, omission of empty days, exclusion of
 * a CANCELLED visit from the counts, the 62-day span rejection, and CUSTOMER role gating.
 */
class AdminVisitDayLoadIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/visits/day-load";
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Autowired VisitRepository visitRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;

    private String adminToken;
    private String customerToken;
    private Subscriber targetSubscriber;
    private Long technicianUserId;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        User adminUser = userRepository.save(new User(
                "day-load-admin-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Admin", "DayLoad",
                Role.ADMIN, UserStatus.ACTIVE));
        adminToken = loginAs(adminUser.getEmail(), "Test1234!");

        User customerUser = userRepository.save(new User(
                "day-load-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Customer", "DayLoad",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Test1234!");

        User targetUser = userRepository.save(new User(
                "day-load-target-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Target", "DayLoad",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property targetProp = propertyRepository.save(new Property(
                nano + " Day Load Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        targetSubscriber = subscriberRepository.save(new Subscriber(
                targetUser.getId(), targetProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        // visit.technician_id is a real FK to users: an assigned visit needs an actual row.
        technicianUserId = userRepository.save(new User(
                "day-load-tech-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Tech", "DayLoad",
                Role.TECHNICIAN, UserStatus.ACTIVE)).getId();
    }

    /** Local-midnight Instant for the given Toronto-local date, offset by hour-of-day. */
    private static Instant atLocalTime(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(TORONTO).toInstant();
    }

    private Visit seedVisit(Instant scheduledFor, VisitStatus status, Long technicianId) {
        Visit visit = new Visit(
                targetSubscriber.getId(),
                targetSubscriber.getPropertyId(),
                null,
                scheduledFor,
                120,
                VisitType.ROUTINE);
        visit.setStatus(status);
        if (technicianId != null) {
            visit.setTechnicianId(technicianId);
        }
        return visitRepository.save(visit);
    }

    @Test
    void dayLoad_returnsShapeOmitsEmptyDays_andExcludesCancelledVisits() throws Exception {
        LocalDate day1 = LocalDate.now(TORONTO).plusDays(10);
        LocalDate day2 = day1.plusDays(1);
        LocalDate emptyDay = day1.plusDays(2); // between day1/day2 range end and nothing else — just unused

        // day1: two SCHEDULED visits, one unassigned.
        seedVisit(atLocalTime(day1, 9), VisitStatus.SCHEDULED, technicianUserId);
        seedVisit(atLocalTime(day1, 13), VisitStatus.SCHEDULED, null);
        // day2: one SCHEDULED visit, assigned.
        seedVisit(atLocalTime(day2, 10), VisitStatus.SCHEDULED, technicianUserId);
        // A CANCELLED visit on day1 must not be counted.
        seedVisit(atLocalTime(day1, 15), VisitStatus.CANCELLED, null);

        MvcResult result = mockMvc.perform(get(URL)
                        .param("from", day1.toString())
                        .param("to", day2.plusDays(3).toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].day").value(day1.toString()))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[0].unassigned").value(1))
                .andExpect(jsonPath("$[1].day").value(day2.toString()))
                .andExpect(jsonPath("$[1].total").value(1))
                .andExpect(jsonPath("$[1].unassigned").value(0))
                .andReturn();

        // The empty day in between/around the range never appears.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(emptyDay.toString());
    }

    @Test
    void dayLoad_dayOutsideRequestedRange_isOmitted() throws Exception {
        LocalDate inRange = LocalDate.now(TORONTO).plusDays(20);
        LocalDate outOfRange = inRange.plusDays(30);

        seedVisit(atLocalTime(inRange, 9), VisitStatus.SCHEDULED, null);
        seedVisit(atLocalTime(outOfRange, 9), VisitStatus.SCHEDULED, null);

        mockMvc.perform(get(URL)
                        .param("from", inRange.toString())
                        .param("to", inRange.toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day").value(inRange.toString()));
    }

    @Test
    void dayLoad_spanLongerThan62Days_returns400() throws Exception {
        LocalDate from = LocalDate.now(TORONTO);
        LocalDate to = from.plus(62, ChronoUnit.DAYS); // 63 inclusive days — one over the cap

        mockMvc.perform(get(URL)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void dayLoad_spanOf62Days_isAccepted() throws Exception {
        LocalDate from = LocalDate.now(TORONTO);
        LocalDate to = from.plus(61, ChronoUnit.DAYS); // 62 inclusive days — exactly the cap

        mockMvc.perform(get(URL)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void dayLoad_toBeforeFrom_returns400() throws Exception {
        LocalDate from = LocalDate.now(TORONTO);
        LocalDate to = from.minusDays(1);

        mockMvc.perform(get(URL)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void dayLoad_missingDate_returns400() throws Exception {
        mockMvc.perform(get(URL)
                        .param("from", LocalDate.now(TORONTO).toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dayLoad_invalidDate_returns400() throws Exception {
        mockMvc.perform(get(URL)
                        .param("from", "not-a-date")
                        .param("to", LocalDate.now(TORONTO).toString())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dayLoad_asCustomer_returns403() throws Exception {
        LocalDate today = LocalDate.now(TORONTO);
        mockMvc.perform(get(URL)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dayLoad_anonymous_returns401() throws Exception {
        LocalDate today = LocalDate.now(TORONTO);
        mockMvc.perform(get(URL)
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isUnauthorized());
    }
}
