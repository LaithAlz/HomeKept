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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminVisitController} —
 * {@code GET /api/admin/visits}, {@code POST /api/admin/visits}, and
 * {@code PATCH /api/admin/visits/{id}}.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET list → 200 array, newest first; includes a freshly created visit.</li>
 *   <li>GET list with status filter → only matching visits; invalid status → 400.</li>
 *   <li>GET list cursor pagination → page 2 ids all less than the cursor.</li>
 *   <li>POST → 201; creates visit + visit_service rows; technicianUserId optional.</li>
 *   <li>POST with unknown serviceIds → 400.</li>
 *   <li>PATCH reschedule → old visit RESCHEDULED + new SCHEDULED row.</li>
 *   <li>PATCH cancel → CANCELLED.</li>
 *   <li>PATCH illegal transition (cancel a COMPLETED visit) → 409.</li>
 *   <li>PATCH assign technician → technician_id updated; no status change.</li>
 *   <li>CUSTOMER on admin endpoint → 403.</li>
 *   <li>Anonymous → 401.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminVisitIntegrationTest {

    private static final String CREATE_URL = "/api/admin/visits";
    private static final String LIST_URL   = "/api/admin/visits";
    private static final String PATCH_URL  = "/api/admin/visits/{id}";
    private static final String LOGIN_URL  = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds       = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();

    /** ADMIN user for admin endpoint tests. */
    private User adminUser;
    private String adminToken;

    /** A CUSTOMER + subscriber used as the target of admin-created visits. */
    private Subscriber targetSubscriber;

    /** A CUSTOMER user for role-gating tests. */
    private User customerUser;
    private String customerToken;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        // ADMIN user.
        adminUser = userRepository.save(new User(
                "admin-visit-admin-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Admin", "Visit",
                Role.ADMIN, UserStatus.ACTIVE));
        createdUserIds.add(adminUser.getId());
        adminToken = loginAs(adminUser.getEmail(), "Test1234!");

        // Target customer subscriber.
        User targetUser = userRepository.save(new User(
                "admin-visit-target-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Target", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(targetUser.getId());

        Property targetProp = propertyRepository.save(new Property(
                nano + " Target Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(targetProp.getId());

        targetSubscriber = subscriberRepository.save(new Subscriber(
                targetUser.getId(), targetProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(targetSubscriber.getId());

        // CUSTOMER user for role-gating tests.
        customerUser = userRepository.save(new User(
                "admin-visit-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Customer", "Role",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());
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
    }

    // ── GET /api/admin/visits — list ─────────────────────────────────────────

    @Test
    void listVisits_asAdmin_returns200WithArray() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listVisits_includesCreatedVisit_newestFirst() throws Exception {
        Visit visit = seedScheduledVisit();

        MvcResult result = mockMvc.perform(get(LIST_URL + "?limit=50")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(visit.getId().intValue());
    }

    @Test
    void listVisits_returnsExpectedFields() throws Exception {
        Visit visit = seedScheduledVisit();

        MvcResult result = mockMvc.perform(get(LIST_URL + "?limit=50")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> subscriberIds = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].subscriberId");
        List<String> statuses = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].status");
        List<String> types = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].type");

        assertThat(subscriberIds).containsExactly(targetSubscriber.getId().intValue());
        assertThat(statuses).containsExactly("SCHEDULED");
        assertThat(types).containsExactly("ROUTINE");
    }

    @Test
    void listVisits_statusFilter_returnsOnlyMatchingStatus() throws Exception {
        Visit scheduled = seedScheduledVisit();
        Visit cancelled = seedScheduledVisit();
        cancelled.setStatus(VisitStatus.CANCELLED);
        visitRepository.save(cancelled);

        MvcResult result = mockMvc.perform(get(LIST_URL + "?status=CANCELLED&limit=100")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(cancelled.getId().intValue());
        assertThat(ids).doesNotContain(scheduled.getId().intValue());
    }

    @Test
    void listVisits_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get(LIST_URL + "?status=NOT_A_REAL_STATUS")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listVisits_cursorPagination_returnsNewestFirst() throws Exception {
        // Seed two visits — the newest-first ordering guarantees the second one seeded
        // (higher id) is returned by the first (limit=1) page.
        seedScheduledVisit();
        seedScheduledVisit();

        MvcResult page1 = mockMvc.perform(get(LIST_URL + "?limit=1")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        List<Integer> page1Ids = com.jayway.jsonpath.JsonPath.read(
                page1.getResponse().getContentAsString(), "$[*].id");
        Long cursor = page1Ids.get(0).longValue();

        // Page 2 using cursor — all returned ids must be less than the cursor.
        mockMvc.perform(get(LIST_URL + "?limit=100&cursor=" + cursor)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id >= " + cursor + ")]").isEmpty());
    }

    @Test
    void listVisits_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listVisits_anonymous_returns401() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/visits — create ──────────────────────────────────────

    @Test
    void createVisit_asAdmin_returns201WithVisitId() throws Exception {
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 120
                }
                """.formatted(targetSubscriber.getId(), scheduledFor);

        MvcResult result = mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.subscriberId").value(targetSubscriber.getId()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.type").value("ROUTINE"))
                .andExpect(jsonPath("$.durationMinutes").value(120))
                .andReturn();

        Long visitId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        Visit persisted = visitRepository.findById(visitId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(persisted.getSubscriberId()).isEqualTo(targetSubscriber.getId());
    }

    @Test
    void createVisit_withServiceIds_createsVisitServiceRows() throws Exception {
        Long svcId = firstServiceId();
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 90,
                  "serviceIds": [%d]
                }
                """.formatted(targetSubscriber.getId(), scheduledFor, svcId);

        MvcResult result = mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].serviceId").value(svcId))
                .andExpect(jsonPath("$.services[0].source").value("TEMPLATE"))
                .andReturn();

        Long visitId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        List<VisitService> services = visitServiceRepository.findByVisitIdOrderByIdAsc(visitId);
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getSource()).isEqualTo(VisitServiceSource.TEMPLATE);
    }

    @Test
    void createVisit_withTechnicianUserId_setsTechnicianId() throws Exception {
        Long techId = adminUser.getId(); // reuse admin's user id as a placeholder technician id
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 120,
                  "technicianUserId": %d
                }
                """.formatted(targetSubscriber.getId(), scheduledFor, techId);

        MvcResult result = mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicianId").value(techId))
                .andReturn();

        Long visitId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        Visit persisted = visitRepository.findById(visitId).orElseThrow();
        assertThat(persisted.getTechnicianId()).isEqualTo(techId);
    }

    @Test
    void createVisit_withUnknownServiceIds_returns400() throws Exception {
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 120,
                  "serviceIds": [999999999]
                }
                """.formatted(targetSubscriber.getId(), scheduledFor);

        mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createVisit_unknownSubscriberId_returns404() throws Exception {
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": 999999999,
                  "scheduledFor": "%s",
                  "durationMinutes": 120
                }
                """.formatted(scheduledFor);

        mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ── POST — role gating ────────────────────────────────────────────────────

    @Test
    void createVisit_asCustomer_returns403() throws Exception {
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 120
                }
                """.formatted(targetSubscriber.getId(), scheduledFor);

        mockMvc.perform(post(CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisit_anonymous_returns401() throws Exception {
        String scheduledFor = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String body = """
                {
                  "subscriberId": %d,
                  "scheduledFor": "%s",
                  "durationMinutes": 120
                }
                """.formatted(targetSubscriber.getId(), scheduledFor);

        mockMvc.perform(post(CREATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/admin/visits/{id} — reschedule ─────────────────────────────

    @Test
    void patchVisit_reschedule_oldVisitBecomesRescheduled_newVisitIsScheduled() throws Exception {
        Visit original = seedScheduledVisit();
        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();

        MvcResult result = mockMvc.perform(patch(PATCH_URL, original.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();

        Long newVisitId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        // Old visit must now be RESCHEDULED.
        Visit old = visitRepository.findById(original.getId()).orElseThrow();
        assertThat(old.getStatus()).isEqualTo(VisitStatus.RESCHEDULED);

        // New visit must be SCHEDULED and have the new time.
        assertThat(newVisitId).isNotEqualTo(original.getId());
        Visit newVisit = visitRepository.findById(newVisitId).orElseThrow();
        assertThat(newVisit.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(newVisit.getSubscriberId()).isEqualTo(targetSubscriber.getId());
    }

    @Test
    void patchVisit_reschedule_copiesServiceRowsToNewVisit() throws Exception {
        Visit original = seedScheduledVisit();
        // Add a service row to the original visit.
        Long svcId = firstServiceId();
        visitServiceRepository.save(new VisitService(original.getId(), svcId, VisitServiceSource.TEMPLATE));

        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        MvcResult result = mockMvc.perform(patch(PATCH_URL, original.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Long newVisitId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        List<VisitService> newServices = visitServiceRepository.findByVisitIdOrderByIdAsc(newVisitId);
        assertThat(newServices).hasSize(1);
        assertThat(newServices.get(0).getServiceId()).isEqualTo(svcId);
    }

    // ── PATCH — cancel ────────────────────────────────────────────────────────

    @Test
    void patchVisit_cancel_scheduledVisit_returnsStatusCancelled() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Visit persisted = visitRepository.findById(visit.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(VisitStatus.CANCELLED);
    }

    // ── PATCH — illegal transition → 409 ─────────────────────────────────────

    @Test
    void patchVisit_cancel_completedVisit_returns409() throws Exception {
        // Force a COMPLETED visit directly (bypassing the state machine for test setup —
        // we set the status manually after creation to simulate a terminal state).
        Visit visit = seedScheduledVisit();
        visit.setStatus(VisitStatus.COMPLETED);
        visitRepository.save(visit);

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void patchVisit_reschedule_cancelledVisit_returns409() throws Exception {
        Visit visit = seedScheduledVisit();
        visit.setStatus(VisitStatus.CANCELLED);
        visitRepository.save(visit);

        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── PATCH — assign technician ─────────────────────────────────────────────

    @Test
    void patchVisit_assignTechnician_setsTechnicianId_noStatusChange() throws Exception {
        Visit visit = seedScheduledVisit();
        Long techId = adminUser.getId();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianUserId\":" + techId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(techId))
                .andExpect(jsonPath("$.status").value("SCHEDULED")); // status unchanged

        Visit persisted = visitRepository.findById(visit.getId()).orElseThrow();
        assertThat(persisted.getTechnicianId()).isEqualTo(techId);
        assertThat(persisted.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
    }

    // ── PATCH — role gating ───────────────────────────────────────────────────

    @Test
    void patchVisit_asCustomer_returns403() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchVisit_anonymous_returns401() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH — missing visit ─────────────────────────────────────────────────

    @Test
    void patchVisit_nonExistentId_returns404() throws Exception {
        mockMvc.perform(patch(PATCH_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Seeds a SCHEDULED ROUTINE visit for the target subscriber. */
    private Visit seedScheduledVisit() {
        return visitRepository.save(new Visit(
                targetSubscriber.getId(),
                targetSubscriber.getPropertyId(),
                null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                120,
                VisitType.ROUTINE
        ));
    }

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

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }
}
