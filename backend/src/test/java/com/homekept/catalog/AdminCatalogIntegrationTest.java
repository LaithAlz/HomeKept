package com.homekept.catalog;

import com.homekept.AbstractIntegrationTest;
import com.homekept.identity.Role;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.visit.Visit;
import com.homekept.visit.VisitRepository;
import com.homekept.visit.VisitService;
import com.homekept.visit.VisitServiceRepository;
import com.homekept.visit.VisitServiceSource;
import com.homekept.visit.VisitType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminCatalogController} — service CRUD
 * ({@code /api/admin/services}) and plan composition
 * ({@code /api/admin/plan-tiers/{planTierId}/services}).
 *
 * <p>Runs against a real Postgres via Testcontainers, sharing the container with every other
 * test class in this run. {@code service}, {@code plan_tier}, and {@code plan_tier_service}
 * are seeded once by Flyway and are deliberately EXCLUDED from
 * {@link AbstractIntegrationTest}'s per-test truncation (other tests, e.g.
 * {@code CatalogIntegrationTest}, assert exact seeded counts/prices against them). Every
 * test here therefore creates its own throwaway service via the API and removes it (and any
 * {@code plan_tier_service}/{@code visit_service} rows referencing it) in {@link #cleanUp()},
 * so no test in this class ever leaves the shared catalog seed data mutated for a test that
 * runs later in the same JVM.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /api/admin/services — ADMIN → 200 incl. inactive; CUSTOMER → 403; anonymous → 401</li>
 *   <li>POST /api/admin/services — 201 + persists; bad category/tierClass → 400 VALIDATION_FAILED</li>
 *   <li>PATCH /api/admin/services/{id} — partial update leaves other fields untouched;
 *       updatedAt moves; unknown id → 404; bad category → 400 VALIDATION_FAILED</li>
 *   <li>POST .../archive — sets active=false, idempotent, disappears from the public picks
 *       menu, and does NOT break a visit that already references it via visit_service;
 *       unknown id → 404</li>
 *   <li>POST .../restore — sets active=true, reappears in picks; unknown id → 404</li>
 *   <li>POST /api/admin/plan-tiers/{id}/services — adds a composition row; duplicate add →
 *       409 CONFLICT (not 500); unknown plan tier/service → 404; bad frequency → 400</li>
 *   <li>PATCH .../services/{serviceId} — updates frequency; not-in-composition → 404</li>
 *   <li>DELETE .../services/{serviceId} — removes the row; not-in-composition → 404</li>
 * </ul>
 */
class AdminCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String SERVICES_URL         = "/api/admin/services";
    private static final String SERVICE_URL          = "/api/admin/services/{id}";
    private static final String ARCHIVE_URL          = "/api/admin/services/{id}/archive";
    private static final String RESTORE_URL          = "/api/admin/services/{id}/restore";
    private static final String PICKS_URL            = "/api/catalog/picks";
    private static final String PLAN_TIER_SERVICES   = "/api/admin/plan-tiers/{planTierId}/services";
    private static final String PLAN_TIER_SERVICE    = "/api/admin/plan-tiers/{planTierId}/services/{serviceId}";

    @Autowired ServiceRepository serviceRepository;
    @Autowired PlanTierRepository planTierRepository;
    @Autowired PlanTierServiceRepository planTierServiceRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;

    private String adminToken;
    private String customerToken;

    /** Every service id created by a test — cleaned up in {@link #cleanUp()}. */
    private final List<Long> createdServiceIds = new ArrayList<>();

    private static final String VALID_CREATE_BODY = """
            {
              "name": "Test pick service",
              "category": "HVAC",
              "tierClass": "BASIC",
              "defaultDurationMinutes": 20,
              "aLaCartePriceCents": 4900,
              "description": "A throwaway service created by AdminCatalogIntegrationTest.",
              "isFreeWithEveryVisit": false
            }
            """;

    @BeforeEach
    void seedUsers() throws Exception {
        adminToken = loginAs(Role.ADMIN);
        customerToken = loginAs(Role.CUSTOMER);
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdServiceIds) {
            jdbcTemplate.update("DELETE FROM plan_tier_service WHERE service_id = ?", id);
            jdbcTemplate.update("DELETE FROM visit_service WHERE service_id = ?", id);
            jdbcTemplate.update("DELETE FROM service WHERE id = ?", id);
        }
        createdServiceIds.clear();
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    /** Creates a throwaway service via the real API and tracks it for cleanup. */
    private Long createTestService(String body) throws Exception {
        MvcResult result = mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = idFrom(result);
        createdServiceIds.add(id);
        return id;
    }

    private Long completeTierId() {
        return planTierRepository.findByCode(PlanCode.COMPLETE).orElseThrow().getId();
    }

    /** Creates a subscriber + property + SCHEDULED visit with a visit_service row for {@code serviceId}. */
    private void seedVisitReferencing(Long serviceId) {
        long nano = System.nanoTime();
        com.homekept.identity.User user = userRepository.save(new com.homekept.identity.User(
                "admin-catalog-visit-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Visit", "Owner",
                Role.CUSTOMER, com.homekept.identity.UserStatus.ACTIVE));
        Property property = propertyRepository.save(new Property(
                nano + " Catalog Test Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        Subscriber subscriber = subscriberRepository.save(new Subscriber(
                user.getId(), property.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        Visit visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                dbNow().plus(10, ChronoUnit.DAYS), 90, VisitType.ROUTINE));
        visitServiceRepository.save(new VisitService(visit.getId(), serviceId, VisitServiceSource.EXTRA));
    }

    // ── GET /api/admin/services ───────────────────────────────────────────────

    @Test
    void listServices_asAdmin_includesInactiveServices() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);
        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Boolean> actives = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.id == " + id + ")].active");
        assertThat(actives).containsExactly(false);
    }

    @Test
    void listServices_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(SERVICES_URL).cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listServices_anonymous_returns401() throws Exception {
        mockMvc.perform(get(SERVICES_URL)).andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/services ──────────────────────────────────────────────

    @Test
    void createService_asAdmin_returns201AndPersists() throws Exception {
        MvcResult result = mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test pick service"))
                .andExpect(jsonPath("$.category").value("HVAC"))
                .andExpect(jsonPath("$.tierClass").value("BASIC"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(20))
                .andExpect(jsonPath("$.aLaCartePriceCents").value(4900))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        Long id = idFrom(result);
        createdServiceIds.add(id);

        com.homekept.catalog.Service reloaded = serviceRepository.findById(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Test pick service");
        assertThat(reloaded.getALaCartePriceCents()).isEqualTo(4900);
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.isFreeWithEveryVisit()).isFalse();
    }

    @Test
    void createService_defaultsIsFreeWithEveryVisitToFalse_whenOmitted() throws Exception {
        MvcResult result = mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Standing item candidate",
                                  "category": "PLUMBING",
                                  "tierClass": "BASIC",
                                  "defaultDurationMinutes": 10,
                                  "description": "No isFreeWithEveryVisit field supplied."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isFreeWithEveryVisit").value(false))
                .andReturn();
        createdServiceIds.add(idFrom(result));
    }

    @Test
    void createService_invalidCategory_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad category service",
                                  "category": "ROOFING",
                                  "tierClass": "BASIC",
                                  "defaultDurationMinutes": 20,
                                  "description": "category is not one of the allowed values."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createService_invalidTierClass_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad tier class service",
                                  "category": "HVAC",
                                  "tierClass": "GOLD",
                                  "defaultDurationMinutes": 20,
                                  "description": "tierClass is not one of the allowed values."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createService_missingName_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "HVAC",
                                  "tierClass": "BASIC",
                                  "defaultDurationMinutes": 20,
                                  "description": "No name supplied."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createService_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/admin/services/{id} ────────────────────────────────────────

    @Test
    void updateService_partialPatch_leavesOtherFieldsUntouched() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(SERVICE_URL, id)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Updated description only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description only"))
                // Untouched fields keep their original values.
                .andExpect(jsonPath("$.name").value("Test pick service"))
                .andExpect(jsonPath("$.category").value("HVAC"))
                .andExpect(jsonPath("$.tierClass").value("BASIC"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(20))
                .andExpect(jsonPath("$.aLaCartePriceCents").value(4900));
    }

    @Test
    void updateService_updatesEveryField() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(SERVICE_URL, id)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Renamed service",
                                  "category": "EXTERIOR",
                                  "tierClass": "PREMIUM",
                                  "defaultDurationMinutes": 45,
                                  "aLaCartePriceCents": 14900,
                                  "description": "Fully replaced description.",
                                  "isFreeWithEveryVisit": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed service"))
                .andExpect(jsonPath("$.category").value("EXTERIOR"))
                .andExpect(jsonPath("$.tierClass").value("PREMIUM"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(45))
                .andExpect(jsonPath("$.aLaCartePriceCents").value(14900))
                .andExpect(jsonPath("$.isFreeWithEveryVisit").value(true));
    }

    @Test
    void updateService_movesUpdatedAt() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);
        java.time.Instant createdUpdatedAt = serviceRepository.findById(id).orElseThrow().getUpdatedAt();

        mockMvc.perform(patch(SERVICE_URL, id)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Bump updatedAt\"}"))
                .andExpect(status().isOk());

        java.time.Instant afterUpdate = serviceRepository.findById(id).orElseThrow().getUpdatedAt();
        assertThat(afterUpdate).isAfter(createdUpdatedAt);
    }

    @Test
    void updateService_unknownId_returns404() throws Exception {
        mockMvc.perform(patch(SERVICE_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"No such service\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateService_invalidCategory_returns400ValidationFailed() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(SERVICE_URL, id)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\": \"ROOFING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateService_asCustomer_returns403() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(SERVICE_URL, id)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"nope\"}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/services/{id}/archive & /restore ──────────────────────

    @Test
    void archiveService_setsInactive_andRemovesFromPublicPicksMenu() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.services[?(@.id == " + id + ")]").isNotEmpty());

        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(serviceRepository.findById(id).orElseThrow().isActive()).isFalse();

        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.services[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void archiveService_referencedByExistingVisit_doesNotFail_andVisitHistoryUnaffected() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);
        seedVisitReferencing(id);

        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // The visit_service row still points at the same service id — untouched by the archive.
        List<VisitService> visitServices = visitServiceRepository.findAll().stream()
                .filter(vs -> vs.getServiceId().equals(id))
                .toList();
        assertThat(visitServices).hasSize(1);

        // The service itself (id, name) is unchanged — only `active` moved.
        com.homekept.catalog.Service reloaded = serviceRepository.findById(id).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getName()).isEqualTo("Test pick service");
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void archiveService_isIdempotent() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void archiveService_unknownId_returns404() throws Exception {
        mockMvc.perform(post(ARCHIVE_URL, 999_999_999L).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveService_asCustomer_returns403() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);
        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreService_reactivates_andReappearsInPicksMenu() throws Exception {
        Long id = createTestService(VALID_CREATE_BODY);
        mockMvc.perform(post(ARCHIVE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post(RESTORE_URL, id).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.services[?(@.id == " + id + ")]").isNotEmpty());
    }

    @Test
    void restoreService_unknownId_returns404() throws Exception {
        mockMvc.perform(post(RESTORE_URL, 999_999_999L).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/admin/plan-tiers/{planTierId}/services ──────────────────────

    @Test
    void addServiceToPlan_asAdmin_createsCompositionRow() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planTierId").value(planTierId))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.frequencyPerYear").value(2));

        assertThat(planTierServiceRepository.findById(
                new PlanTierService.PlanTierServiceId(planTierId, serviceId))).isPresent();
    }

    @Test
    void addServiceToPlan_duplicate_returns409Conflict() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        // The original row's frequency is untouched by the rejected duplicate.
        assertThat(planTierServiceRepository.findById(
                        new PlanTierService.PlanTierServiceId(planTierId, serviceId))
                .orElseThrow().getFrequencyPerYear()).isEqualTo(2);
    }

    @Test
    void addServiceToPlan_unknownPlanTier_returns404() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(post(PLAN_TIER_SERVICES, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addServiceToPlan_unknownService_returns404() throws Exception {
        mockMvc.perform(post(PLAN_TIER_SERVICES, completeTierId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 999999999, \"frequencyPerYear\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addServiceToPlan_invalidFrequency_returns400ValidationFailed() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(post(PLAN_TIER_SERVICES, completeTierId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void addServiceToPlan_asCustomer_returns403() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(post(PLAN_TIER_SERVICES, completeTierId())
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/admin/plan-tiers/{planTierId}/services/{serviceId} ─────────

    @Test
    void updatePlanTierServiceFrequency_asAdmin_updatesFrequency() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch(PLAN_TIER_SERVICE, planTierId, serviceId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyPerYear\": 6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequencyPerYear").value(6));

        assertThat(planTierServiceRepository.findById(
                        new PlanTierService.PlanTierServiceId(planTierId, serviceId))
                .orElseThrow().getFrequencyPerYear()).isEqualTo(6);
    }

    @Test
    void updatePlanTierServiceFrequency_notInComposition_returns404() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(PLAN_TIER_SERVICE, completeTierId(), serviceId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyPerYear\": 6}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePlanTierServiceFrequency_unknownPlanTier_returns404() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(patch(PLAN_TIER_SERVICE, 999_999_999L, serviceId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyPerYear\": 6}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePlanTierServiceFrequency_invalidFrequency_returns400ValidationFailed() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch(PLAN_TIER_SERVICE, planTierId, serviceId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyPerYear\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ── DELETE /api/admin/plan-tiers/{planTierId}/services/{serviceId} ────────

    @Test
    void removeServiceFromPlan_asAdmin_removesRow() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(PLAN_TIER_SERVICE, planTierId, serviceId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNoContent());

        assertThat(planTierServiceRepository.findById(
                new PlanTierService.PlanTierServiceId(planTierId, serviceId))).isEmpty();
    }

    @Test
    void removeServiceFromPlan_notInComposition_returns404() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);

        mockMvc.perform(delete(PLAN_TIER_SERVICE, completeTierId(), serviceId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeServiceFromPlan_unknownService_returns404() throws Exception {
        mockMvc.perform(delete(PLAN_TIER_SERVICE, completeTierId(), 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeServiceFromPlan_asCustomer_returns403() throws Exception {
        Long serviceId = createTestService(VALID_CREATE_BODY);
        Long planTierId = completeTierId();

        mockMvc.perform(post(PLAN_TIER_SERVICES, planTierId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": " + serviceId + ", \"frequencyPerYear\": 2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(PLAN_TIER_SERVICE, planTierId, serviceId)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }
}
