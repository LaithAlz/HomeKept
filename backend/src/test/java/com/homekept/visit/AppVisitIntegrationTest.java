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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AppVisitController} — {@code GET /api/app/visits} and
 * {@code GET /api/app/visits/{id}}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authenticated CUSTOMER can list their own visits (paginated).</li>
 *   <li>Authenticated CUSTOMER can get their own visit detail including visit_services.</li>
 *   <li>Another subscriber's visit → 404 (ownership, not 403).</li>
 *   <li>Anonymous → 401.</li>
 *   <li>ADMIN on CUSTOMER endpoint → 403 (wrong role).</li>
 *   <li>{@code hasPendingRescheduleRequest} reflects whether a PENDING
 *       {@link RescheduleRequest} exists for the visit, on both the detail endpoint and
 *       (batch-computed for the whole page) the list endpoint.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppVisitIntegrationTest {

    private static final String LIST_URL   = "/api/app/visits";
    private static final String DETAIL_URL = "/api/app/visits/{id}";
    private static final String LOGIN_URL  = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
    @Autowired RescheduleRequestRepository rescheduleRequestRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds       = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdVisitIds      = new ArrayList<>();

    /** The CUSTOMER under test. */
    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerToken;

    /** A second CUSTOMER used for ownership isolation tests. */
    private User otherUser;
    private Subscriber otherSubscriber;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        // Primary customer.
        customerUser = userRepository.save(new User(
                "app-visit-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "App", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        Property customerProp = propertyRepository.save(new Property(
                nano + " Customer Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(customerProp.getId());

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), customerProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(customerSubscriber.getId());

        // Other customer (different subscriber).
        otherUser = userRepository.save(new User(
                "app-visit-other-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Other", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(otherUser.getId());

        Property otherProp = propertyRepository.save(new Property(
                nano + " Other Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(otherProp.getId());

        otherSubscriber = subscriberRepository.save(new Subscriber(
                otherUser.getId(), otherProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(otherSubscriber.getId());

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        // visit_service → visit (cascade on delete visit) → subscriber ON DELETE RESTRICT.
        // Delete visit_service and visit explicitly before subscriber.
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

    // ── GET /api/app/visits — list ────────────────────────────────────────────

    @Test
    void listVisits_authenticatedCustomer_returns200WithArray() throws Exception {
        seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        seedVisitForCustomer(Instant.now().plus(60, ChronoUnit.DAYS));

        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listVisits_anonymous_returns401() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listVisits_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();
        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listVisits_returnsOnlyOwnVisits_notOtherSubscriberVisits() throws Exception {
        // Seed one visit for customerSubscriber and one for otherSubscriber.
        Visit customerVisit = seedVisitForSubscriber(customerSubscriber,
                Instant.now().plus(30, ChronoUnit.DAYS));
        seedVisitForSubscriber(otherSubscriber,
                Instant.now().plus(30, ChronoUnit.DAYS));

        MvcResult result = mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> returnedIds = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        // Only the customer's own visit should appear.
        assertThat(returnedIds).hasSize(1);
        assertThat(returnedIds.get(0).longValue()).isEqualTo(customerVisit.getId());
    }

    @Test
    void listVisits_cursorPagination_respectsLimit() throws Exception {
        seedVisitForCustomer(Instant.now().plus(10, ChronoUnit.DAYS));
        seedVisitForCustomer(Instant.now().plus(20, ChronoUnit.DAYS));
        seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(LIST_URL + "?limit=2")
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── GET /api/app/visits — hasPendingRescheduleRequest (batch) ───────────

    @Test
    void listVisits_noRescheduleRequest_hasPendingRescheduleRequestIsFalse() throws Exception {
        seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasPendingRescheduleRequest").value(false));
    }

    @Test
    void listVisits_pendingRescheduleRequest_hasPendingRescheduleRequestIsTrue() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        rescheduleRequestRepository.save(
                new RescheduleRequest(visit.getId(), customerSubscriber.getId()));

        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasPendingRescheduleRequest").value(true));
    }

    @Test
    void listVisits_mixOfVisits_flagsOnlyTheOneWithAPendingRequest() throws Exception {
        // Three visits: one with a PENDING request, one with a resolved (DECLINED) request
        // (should not count), one with none — exercises the batch query across a page of
        // more than one visit.
        Visit pendingVisit = seedVisitForCustomer(Instant.now().plus(10, ChronoUnit.DAYS));
        rescheduleRequestRepository.save(
                new RescheduleRequest(pendingVisit.getId(), customerSubscriber.getId()));

        Visit declinedVisit = seedVisitForCustomer(Instant.now().plus(20, ChronoUnit.DAYS));
        RescheduleRequest declined = new RescheduleRequest(declinedVisit.getId(), customerSubscriber.getId());
        declined.setStatus(RescheduleRequestStatus.DECLINED);
        declined.setAdminNote("No availability");
        rescheduleRequestRepository.save(declined);

        Visit plainVisit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        MvcResult result = mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Map<String, Object>> items = com.jayway.jsonpath.JsonPath.read(body, "$");
        Map<Long, Boolean> flagById = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> Long.valueOf(String.valueOf(item.get("id"))),
                        item -> (Boolean) item.get("hasPendingRescheduleRequest")));

        assertThat(flagById.get(pendingVisit.getId())).isTrue();
        assertThat(flagById.get(declinedVisit.getId())).isFalse();
        assertThat(flagById.get(plainVisit.getId())).isFalse();
    }

    // ── GET /api/app/visits/{id} — detail ────────────────────────────────────

    @Test
    void getVisit_ownVisit_returns200WithServices() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        // Seed one service row so we can assert it appears in the response.
        VisitService svc = visitServiceRepository.save(
                new VisitService(visit.getId(), firstServiceId(), VisitServiceSource.TEMPLATE));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visit.getId()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.type").value("ROUTINE"))
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].id").value(svc.getId()));
    }

    @Test
    void getVisit_otherSubscribersVisit_returns404() throws Exception {
        // Visit belongs to otherSubscriber — customerUser must NOT see it.
        Visit otherVisit = seedVisitForSubscriber(otherSubscriber,
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(DETAIL_URL, otherVisit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVisit_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get(DETAIL_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVisit_anonymous_returns401() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(DETAIL_URL, visit.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getVisit_asAdmin_returns403() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVisit_technicianFirstName_isNullWhenNotAssigned() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianFirstName").value(nullValue()));
    }

    // ── GET /api/app/visits/{id} — hasPendingRescheduleRequest ──────────────

    @Test
    void getVisit_noRescheduleRequest_hasPendingRescheduleRequestIsFalse() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRescheduleRequest").value(false));
    }

    @Test
    void getVisit_pendingRescheduleRequest_hasPendingRescheduleRequestIsTrue() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        rescheduleRequestRepository.save(
                new RescheduleRequest(visit.getId(), customerSubscriber.getId()));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRescheduleRequest").value(true));
    }

    @Test
    void getVisit_resolvedRescheduleRequest_hasPendingRescheduleRequestIsFalse() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        RescheduleRequest request = new RescheduleRequest(visit.getId(), customerSubscriber.getId());
        request.setStatus(RescheduleRequestStatus.DECLINED);
        request.setAdminNote("No availability");
        rescheduleRequestRepository.save(request);

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRescheduleRequest").value(false));
    }

    // ── GET /api/app/visits/{id} — photos ────────────────────────────────────

    @Test
    void getVisit_noPhotoRows_returnsEmptyPhotosArray() throws Exception {
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos").isArray())
                .andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void getVisit_photoRowsButR2Unconfigured_returnsEmptyPhotosArray() throws Exception {
        // R2 is unconfigured in the test profile (no FakeStorageServiceConfig imported on
        // this test class) — presignDownload throws StorageUnavailableException, and the
        // service degrades to an empty photos[] rather than a dead or fabricated URL.
        Visit visit = seedVisitForCustomer(Instant.now().plus(30, ChronoUnit.DAYS));
        visitPhotoRepository.save(new VisitPhoto(
                visit.getId(), "visits/" + visit.getId() + "/some-uuid", "North wall crack",
                Instant.now().minus(1, ChronoUnit.DAYS)));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos").isArray())
                .andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void getVisit_otherSubscribersVisit_photosNeverLeak() throws Exception {
        // Even though the ownership check already 404s before photos are loaded, seed a
        // photo on the other subscriber's visit to make the isolation explicit.
        Visit otherVisit = seedVisitForSubscriber(otherSubscriber,
                Instant.now().plus(30, ChronoUnit.DAYS));
        visitPhotoRepository.save(new VisitPhoto(
                otherVisit.getId(), "visits/" + otherVisit.getId() + "/some-uuid",
                "Not yours", Instant.now()));

        mockMvc.perform(get(DETAIL_URL, otherVisit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Visit seedVisitForCustomer(Instant scheduledFor) {
        return seedVisitForSubscriber(customerSubscriber, scheduledFor);
    }

    private Visit seedVisitForSubscriber(Subscriber subscriber, Instant scheduledFor) {
        Visit visit = visitRepository.save(new Visit(
                subscriber.getId(),
                subscriber.getPropertyId(),
                null,
                scheduledFor,
                120,
                VisitType.ROUTINE
        ));
        createdVisitIds.add(visit.getId());
        return visit;
    }

    /**
     * Returns the id of the first seeded service from the catalog (the 4 standing items
     * are seeded by V2__catalog.sql; their ids start at 1).
     */
    private Long firstServiceId() {
        Long id = jdbc.queryForObject(
                "SELECT id FROM service WHERE is_free_with_every_visit = TRUE ORDER BY id LIMIT 1",
                Long.class);
        if (id == null) {
            throw new IllegalStateException("No standing-item services found in catalog seed");
        }
        return id;
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
        String email = "app-visit-admin-" + nano + "@test.local";
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
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }
}
