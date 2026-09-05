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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminVisitController} —
 * {@code GET /api/admin/visits}, {@code POST /api/admin/visits},
 * {@code GET /api/admin/visits/{id}}, {@code PATCH /api/admin/visits/{id}}, and
 * {@code GET /api/admin/visits/{id}/events}.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET list → 200 array, newest first; includes a freshly created visit; each row
 *       carries the customer's name and the property's street address/city, batch-resolved
 *       (never per-row) via the identity and property domains' services.</li>
 *   <li>GET list with status filter → only matching visits; invalid status → 400.</li>
 *   <li>GET list cursor pagination → page 2 ids all less than the cursor.</li>
 *   <li>POST → 201; creates visit + visit_service rows; technicianUserId optional.</li>
 *   <li>POST with unknown serviceIds → 400.</li>
 *   <li>GET detail → full detail incl. customer/technician identity and property address;
 *       unassigned technician → fields absent; unknown id → 404.</li>
 *   <li>PATCH reschedule → updates {@code scheduledFor} IN PLACE (same visit id, no
 *       replacement row, no duplicated {@code visit_service} rows) and records a
 *       {@code RESCHEDULED} {@code visit_event} (source ADMIN).</li>
 *   <li>PATCH cancel → CANCELLED; records a {@code CANCELLED} {@code visit_event}.</li>
 *   <li>PATCH illegal transition (cancel a COMPLETED visit, reschedule a CANCELLED visit)
 *       → 409.</li>
 *   <li>PATCH assign technician → technician_id updated; no status change; records a
 *       {@code TECHNICIAN_ASSIGNED} {@code visit_event} exactly once (a repeat assignment
 *       of the SAME technician does not record a second event).</li>
 *   <li>GET events → newest first; unknown visit → 404; no history → empty array.</li>
 *   <li>CUSTOMER on admin endpoint → 403.</li>
 *   <li>Anonymous → 401.</li>
 * </ul>
 */
class AdminVisitIntegrationTest extends AbstractIntegrationTest {

    private static final String CREATE_URL = "/api/admin/visits";
    private static final String LIST_URL   = "/api/admin/visits";
    private static final String PATCH_URL  = "/api/admin/visits/{id}";

    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired JdbcTemplate jdbc;

    /** ADMIN user for admin endpoint tests. */
    private User adminUser;
    private String adminToken;

    /** A CUSTOMER + subscriber used as the target of admin-created visits. */
    private Subscriber targetSubscriber;

    /** A CUSTOMER user for role-gating tests. */
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
        adminToken = loginAs(adminUser.getEmail(), "Test1234!");

        // Target customer subscriber.
        User targetUser = userRepository.save(new User(
                "admin-visit-target-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Target", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property targetProp = propertyRepository.save(new Property(
                nano + " Target Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        targetSubscriber = subscriberRepository.save(new Subscriber(
                targetUser.getId(), targetProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        // CUSTOMER user for role-gating tests.
        User customerUser = userRepository.save(new User(
                "admin-visit-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Customer", "Role",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
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
    void listVisits_includesCustomerIdentity_andPropertyAddress() throws Exception {
        Visit visit = seedScheduledVisit();

        MvcResult result = mockMvc.perform(get(LIST_URL + "?limit=50")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> firstNames = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].customerFirstName");
        List<String> lastNames = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].customerLastName");
        List<String> streets = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].propertyStreetAddress");
        List<String> cities = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + visit.getId() + ")].propertyCity");

        assertThat(firstNames).containsExactly("Target");
        assertThat(lastNames).containsExactly("Customer");
        assertThat(cities).containsExactly("Mississauga");
        assertThat(streets).hasSize(1);
        assertThat(streets.get(0)).endsWith("Target Ave");
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

        Long visitId = idFrom(result);

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

        Long visitId = idFrom(result);

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

        Long visitId = idFrom(result);
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

    // ── PATCH /api/admin/visits/{id} — reschedule (in place) ─────────────────

    @Test
    void patchVisit_reschedule_updatesScheduledForInPlace_sameVisitId() throws Exception {
        Visit original = seedScheduledVisit();
        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();

        MvcResult result = mockMvc.perform(patch(PATCH_URL, original.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andReturn();

        // The response is the SAME visit, not a new one — no replacement row is created.
        Long returnedId = idFrom(result);
        assertThat(returnedId).isEqualTo(original.getId());

        Visit persisted = visitRepository.findById(original.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(persisted.getScheduledFor()).isEqualTo(Instant.parse(newTime));

        // Exactly one visit row exists for this subscriber — the list never grew.
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit WHERE subscriber_id = ?", Integer.class, targetSubscriber.getId());
        assertThat(total).isEqualTo(1);
    }

    @Test
    void patchVisit_reschedule_doesNotDuplicateServiceRows() throws Exception {
        Visit original = seedScheduledVisit();
        // Add a service row to the original visit.
        Long svcId = firstServiceId();
        visitServiceRepository.save(new VisitService(original.getId(), svcId, VisitServiceSource.TEMPLATE));

        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        mockMvc.perform(patch(PATCH_URL, original.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isOk());

        // The same, single service row is still there on the same visit — never duplicated
        // or moved to a new visit id.
        List<VisitService> services = visitServiceRepository.findByVisitIdOrderByIdAsc(original.getId());
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getServiceId()).isEqualTo(svcId);
    }

    @Test
    void patchVisit_reschedule_recordsVisitEvent_sourceAdmin() throws Exception {
        Visit original = seedScheduledVisit();
        Instant oldTime = original.getScheduledFor();
        String newTime = Instant.now().plus(90, ChronoUnit.DAYS).toString();

        mockMvc.perform(patch(PATCH_URL, original.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledFor\":\"" + newTime + "\"}"))
                .andExpect(status().isOk());

        MvcResult events = mockMvc.perform(get(PATCH_URL + "/events", original.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String body = events.getResponse().getContentAsString();

        List<String> types = com.jayway.jsonpath.JsonPath.read(body, "$[*].type");
        assertThat(types).contains("RESCHEDULED");
        List<String> sources = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.type == 'RESCHEDULED')].source");
        assertThat(sources).containsExactly("ADMIN");
        List<Integer> byUserIds = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.type == 'RESCHEDULED')].byUserId");
        assertThat(byUserIds).containsExactly(adminUser.getId().intValue());
        List<String> froms = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.type == 'RESCHEDULED')].payload.from");
        assertThat(froms).containsExactly(oldTime.toString());
        List<String> tos = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.type == 'RESCHEDULED')].payload.to");
        assertThat(tos).containsExactly(Instant.parse(newTime).toString());
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

    @Test
    void patchVisit_cancel_recordsVisitEvent_sourceAdmin() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, source, by_user_id FROM visit_event WHERE visit_id = ?", visit.getId());
        assertThat(row.get("event_type")).isEqualTo("CANCELLED");
        assertThat(row.get("source")).isEqualTo("ADMIN");
        assertThat(((Number) row.get("by_user_id")).longValue()).isEqualTo(adminUser.getId());
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

    @Test
    void patchVisit_assignTechnician_recordsVisitEvent() throws Exception {
        Visit visit = seedScheduledVisit();
        Long techId = adminUser.getId();

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianUserId\":" + techId + "}"))
                .andExpect(status().isOk());

        // Extract via jsonb operators rather than string-matching the raw payload text —
        // Postgres's jsonb storage does not preserve object key order or whitespace.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, source, payload ->> 'to' AS to_val, payload ->> 'from' AS from_val "
                        + "FROM visit_event WHERE visit_id = ?", visit.getId());
        assertThat(row.get("event_type")).isEqualTo("TECHNICIAN_ASSIGNED");
        assertThat(row.get("source")).isEqualTo("ADMIN");
        assertThat(row.get("to_val")).isEqualTo(String.valueOf(techId));
        assertThat(row.get("from_val")).isNull();
    }

    @Test
    void patchVisit_assignSameTechnicianTwice_recordsEventOnlyOnce() throws Exception {
        Visit visit = seedScheduledVisit();
        Long techId = adminUser.getId();
        String body = "{\"technicianUserId\":" + techId + "}";

        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // Re-assigning the SAME technician is a no-op from the log's point of view.
        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM visit_event WHERE visit_id = ? AND event_type = 'TECHNICIAN_ASSIGNED'",
                Integer.class, visit.getId());
        assertThat(count).isEqualTo(1);
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

    // ── GET /api/admin/visits/{id} — detail ───────────────────────────────────

    @Test
    void getVisit_asAdmin_returnsFullDetail() throws Exception {
        Visit visit = seedScheduledVisit();
        Long techId = adminUser.getId();
        visit.setTechnicianId(techId);
        visitRepository.save(visit);

        mockMvc.perform(get(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visit.getId()))
                .andExpect(jsonPath("$.subscriberId").value(targetSubscriber.getId()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.type").value("ROUTINE"))
                .andExpect(jsonPath("$.durationMinutes").value(120))
                .andExpect(jsonPath("$.technicianId").value(techId))
                .andExpect(jsonPath("$.technicianFirstName").value("Admin"))
                .andExpect(jsonPath("$.technicianLastName").value("Visit"))
                .andExpect(jsonPath("$.customerFirstName").value("Target"))
                .andExpect(jsonPath("$.customerLastName").value("Customer"))
                .andExpect(jsonPath("$.customerEmail").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.property.city").value("Mississauga"))
                .andExpect(jsonPath("$.property.postalCode").value("L5L 1A1"))
                .andExpect(jsonPath("$.services").isArray());
    }

    @Test
    void getVisit_unassignedTechnician_technicianFieldsNull() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(get(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").doesNotExist())
                .andExpect(jsonPath("$.technicianFirstName").doesNotExist());
    }

    @Test
    void getVisit_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get(PATCH_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVisit_asCustomer_returns403() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(get(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVisit_anonymous_returns401() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(get(PATCH_URL, visit.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/admin/visits/{id}/events — the log ───────────────────────────

    @Test
    void getEvents_newestFirst() throws Exception {
        Visit visit = seedScheduledVisit();
        // Two lifecycle actions in sequence: assign a technician, then cancel.
        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianUserId\":" + adminUser.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(PATCH_URL, visit.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(PATCH_URL + "/events", visit.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        List<String> types = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$[*].type");
        // Newest first: CANCELLED was recorded after TECHNICIAN_ASSIGNED.
        assertThat(types).containsExactly("CANCELLED", "TECHNICIAN_ASSIGNED");
    }

    @Test
    void getEvents_nonExistentVisit_returns404() throws Exception {
        mockMvc.perform(get(PATCH_URL + "/events", 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEvents_noHistory_returnsEmptyArray() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(get(PATCH_URL + "/events", visit.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getEvents_asCustomer_returns403() throws Exception {
        Visit visit = seedScheduledVisit();

        mockMvc.perform(get(PATCH_URL + "/events", visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
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
}
