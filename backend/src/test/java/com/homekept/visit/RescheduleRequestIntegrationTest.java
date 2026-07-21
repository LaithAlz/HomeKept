package com.homekept.visit;

import com.homekept.TestcontainersConfiguration;
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
import com.homekept.visit.exception.RescheduleRequestConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the reschedule-request slice (#54):
 * customer {@code POST /api/app/visits/{id}/reschedule-request} and the admin queue
 * ({@code GET /api/admin/reschedule-requests}, {@code .../{id}/confirm}, {@code .../{id}/decline}).
 *
 * <p>Runs against real Postgres via Testcontainers — exercises the partial unique index
 * (duplicate-pending guard) and the visit reschedule swap (RESCHEDULED old + new SCHEDULED).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RescheduleRequestIntegrationTest {

    private static final String LOGIN_URL  = "/api/auth/login";
    private static final String ADMIN_LIST_URL = "/api/admin/reschedule-requests";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired RescheduleRequestRepository rescheduleRequestRepository;
    @Autowired RescheduleService rescheduleService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds        = new ArrayList<>();

    private Subscriber subscriber;
    private Visit visit;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void seed() throws Exception {
        long nano = System.nanoTime();

        User customer = userRepository.save(new User(
                "resched-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Resched", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customer.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Reschedule Rd", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        // A SCHEDULED visit, two weeks out.
        Instant scheduledFor = Instant.now().plus(14, ChronoUnit.DAYS);
        visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                scheduledFor, 120, VisitType.ROUTINE));

        customerToken = loginAs(customer.getEmail(), "Cust1234!");
        adminToken = loginAsNewAdmin();
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM reschedule_request_slot WHERE reschedule_request_id IN "
                    + "(SELECT id FROM reschedule_request WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM reschedule_request WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM visit WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM subscription_event WHERE subscriber_id = ?", subId);
        }
        for (Long subId : createdSubscriberIds) {
            subscriberRepository.deleteById(subId);
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

    private String rescheduleUrl(Long visitId) {
        return "/api/app/visits/" + visitId + "/reschedule-request";
    }

    // ── Customer: create ────────────────────────────────────────────────────────

    @Test
    void createRequest_scheduledVisit_returns201Pending_andPersistsSlots() throws Exception {
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\",\"2026-08-02T17:00:00Z\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.visitId").value(visit.getId()))
                .andExpect(jsonPath("$.preferredDates.length()").value(2));

        Integer slotCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reschedule_request_slot s "
                        + "JOIN reschedule_request r ON r.id = s.reschedule_request_id "
                        + "WHERE r.subscriber_id = ?", Integer.class, subscriber.getId());
        assertThat(slotCount).isEqualTo(2);
    }

    @Test
    void confirm_twoConcurrent_onlyOneSucceeds_singleReplacementVisit() throws Exception {
        // Seed a PENDING request via the customer endpoint.
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}"))
                .andExpect(status().isCreated());
        Long requestId = rescheduleRequestRepository
                .findByVisitIdAndStatus(visit.getId(), RescheduleRequestStatus.PENDING)
                .orElseThrow().getId();

        // Two admins (or one double-clicked confirm) resolve the SAME request at once. The
        // PESSIMISTIC_WRITE lock must let exactly one through; the other blocks, re-reads the
        // now-CONFIRMED status, and is rejected BEFORE it can create a second replacement visit.
        Instant newSlot = Instant.now().plus(21, ChronoUnit.DAYS);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch fire = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> confirmTask = () -> {
            fire.await();
            try {
                rescheduleService.confirm(requestId, newSlot, "confirmed");
                return Boolean.TRUE;   // won
            } catch (RescheduleRequestConflictException e) {
                return Boolean.FALSE;  // correctly rejected
            }
        };
        var f1 = pool.submit(confirmTask);
        var f2 = pool.submit(confirmTask);
        fire.countDown();
        boolean a = f1.get();
        boolean b = f2.get();
        pool.shutdown();

        assertThat(a ^ b).as("exactly one concurrent confirm should win").isTrue();
        // The original visit is RESCHEDULED and exactly ONE replacement SCHEDULED visit exists.
        Integer scheduled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit WHERE subscriber_id = ? AND status = 'SCHEDULED'",
                Integer.class, subscriber.getId());
        assertThat(scheduled).isEqualTo(1);
    }

    @Test
    void createRequest_notOwnedVisit_returns404() throws Exception {
        // Another subscriber's visit.
        long nano = System.nanoTime();
        User other = userRepository.save(new User(
                "resched-other-" + nano + "@test.local",
                passwordEncoder.encode("Other1234!"),
                "Other", "Customer", Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(other.getId());
        Property otherProp = propertyRepository.save(new Property(
                nano + " Other Rd", null, "Mississauga", "L5L 1A1", "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(otherProp.getId());
        Subscriber otherSub = subscriberRepository.save(new Subscriber(
                other.getId(), otherProp.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(otherSub.getId());
        Visit otherVisit = visitRepository.save(new Visit(
                otherSub.getId(), otherProp.getId(), null,
                Instant.now().plus(10, ChronoUnit.DAYS), 120, VisitType.ROUTINE));

        mockMvc.perform(post(rescheduleUrl(otherVisit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequest_visitNotScheduled_returns409() throws Exception {
        visit.setStatus(VisitStatus.COMPLETED);
        visitRepository.save(visit);

        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void createRequest_duplicatePending_returns409() throws Exception {
        String body = "{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}";
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Second pending request for the same visit — blocked by the partial unique index.
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void createRequest_emptyPreferredDates_returns400() throws Exception {
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createRequest_anonymous_returns401() throws Exception {
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequest_asAdmin_returns403() throws Exception {
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\"]}"))
                .andExpect(status().isForbidden());
    }

    // ── Customer: cancel ────────────────────────────────────────────────────────

    @Test
    void cancelRequest_pendingRequest_returns204_freesIndex_andDeletesSlots() throws Exception {
        Long requestId = createPendingRequest();

        mockMvc.perform(delete(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNoContent());

        // The detail endpoint no longer reports a pending request.
        mockMvc.perform(get("/api/app/visits/" + visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRescheduleRequest").value(false));

        // The request row and its slots are gone.
        assertThat(rescheduleRequestRepository.findById(requestId)).isEmpty();
        Integer slotCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reschedule_request_slot WHERE reschedule_request_id = ?",
                Integer.class, requestId);
        assertThat(slotCount).isZero();

        // The partial unique index is freed — a new request can be submitted.
        mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-10T15:00:00Z\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelRequest_noPendingRequest_returns404() throws Exception {
        mockMvc.perform(delete(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelRequest_notOwnedVisit_returns404() throws Exception {
        long nano = System.nanoTime();
        User other = userRepository.save(new User(
                "resched-cancel-other-" + nano + "@test.local",
                passwordEncoder.encode("Other1234!"),
                "Other", "Customer", Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(other.getId());
        Property otherProp = propertyRepository.save(new Property(
                nano + " Other Cancel Rd", null, "Mississauga", "L5L 1A1", "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(otherProp.getId());
        Subscriber otherSub = subscriberRepository.save(new Subscriber(
                other.getId(), otherProp.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(otherSub.getId());
        Visit otherVisit = visitRepository.save(new Visit(
                otherSub.getId(), otherProp.getId(), null,
                Instant.now().plus(10, ChronoUnit.DAYS), 120, VisitType.ROUTINE));

        mockMvc.perform(delete(rescheduleUrl(otherVisit.getId()))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelRequest_alreadyResolved_returns404() throws Exception {
        Long requestId = createPendingRequest();

        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"2026-08-05T16:00:00Z\"}"))
                .andExpect(status().isOk());

        // The request is now CONFIRMED, not PENDING — nothing left to cancel.
        mockMvc.perform(delete(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelRequest_anonymous_returns401() throws Exception {
        mockMvc.perform(delete(rescheduleUrl(visit.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelRequest_asAdmin_returns403() throws Exception {
        mockMvc.perform(delete(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── Admin: list / confirm / decline ──────────────────────────────────────────

    @Test
    void adminList_returnsPendingRequest() throws Exception {
        Long requestId = createPendingRequest();

        mockMvc.perform(get(ADMIN_LIST_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + requestId + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + requestId + ")].status").value(org.hamcrest.Matchers.hasItem("PENDING")));
    }

    @Test
    void adminConfirm_reschedulesVisit_andMarksConfirmed() throws Exception {
        Long requestId = createPendingRequest();

        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"2026-08-05T16:00:00Z\",\"adminNote\":\"Booked the 5th\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedVisitId").isNumber());

        // Old visit is now RESCHEDULED (terminal); the request points at a new visit.
        Visit oldVisit = visitRepository.findById(visit.getId()).orElseThrow();
        assertThat(oldVisit.getStatus()).isEqualTo(VisitStatus.RESCHEDULED);

        RescheduleRequest req = rescheduleRequestRepository.findById(requestId).orElseThrow();
        assertThat(req.getStatus()).isEqualTo(RescheduleRequestStatus.CONFIRMED);
        assertThat(req.getConfirmedVisitId()).isNotNull();
        Visit newVisit = visitRepository.findById(req.getConfirmedVisitId()).orElseThrow();
        assertThat(newVisit.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(newVisit.getScheduledFor()).isEqualTo(Instant.parse("2026-08-05T16:00:00Z"));
    }

    @Test
    void adminConfirm_alreadyResolved_returns409() throws Exception {
        Long requestId = createPendingRequest();
        String confirmBody = "{\"scheduledFor\":\"2026-08-05T16:00:00Z\"}";
        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody))
                .andExpect(status().isOk());

        // Confirming again — request is no longer PENDING.
        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody))
                .andExpect(status().isConflict());
    }

    @Test
    void adminConfirm_missingScheduledFor_returns400() throws Exception {
        Long requestId = createPendingRequest();
        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"no date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void adminConfirm_unknownRequest_returns404() throws Exception {
        mockMvc.perform(post(ADMIN_LIST_URL + "/999999/confirm")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"2026-08-05T16:00:00Z\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDecline_marksDeclined_withNote() throws Exception {
        Long requestId = createPendingRequest();

        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/decline")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"No availability those days\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        RescheduleRequest req = rescheduleRequestRepository.findById(requestId).orElseThrow();
        assertThat(req.getStatus()).isEqualTo(RescheduleRequestStatus.DECLINED);
        assertThat(req.getAdminNote()).isEqualTo("No availability those days");
        // Original visit untouched by a decline.
        assertThat(visitRepository.findById(visit.getId()).orElseThrow().getStatus())
                .isEqualTo(VisitStatus.SCHEDULED);
    }

    @Test
    void adminDecline_missingNote_returns400() throws Exception {
        Long requestId = createPendingRequest();
        mockMvc.perform(post(ADMIN_LIST_URL + "/" + requestId + "/decline")
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminList_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(ADMIN_LIST_URL).cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminList_anonymous_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_LIST_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a PENDING request for the seeded visit via the customer endpoint; returns its id. */
    private Long createPendingRequest() throws Exception {
        MvcResult result = mockMvc.perform(post(rescheduleUrl(visit.getId()))
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDates\":[\"2026-08-01T15:00:00Z\",\"2026-08-02T17:00:00Z\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return rescheduleRequestRepository
                .findByStatusOrderByIdAsc(RescheduleRequestStatus.PENDING)
                .stream()
                .filter(r -> r.getVisitId().equals(visit.getId()))
                .findFirst().orElseThrow().getId();
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
        String email = "resched-admin-" + nano + "@test.local";
        User admin = userRepository.save(new User(
                email, passwordEncoder.encode("Admin1234!"),
                "Admin", "Test", Role.ADMIN, UserStatus.ACTIVE));
        createdUserIds.add(admin.getId());
        return loginAs(email, "Admin1234!");
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie '" + name + "' not found in Set-Cookie headers"));
    }
}
