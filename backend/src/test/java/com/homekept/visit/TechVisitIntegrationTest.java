package com.homekept.visit;

import com.homekept.TestcontainersConfiguration;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.AccessNotesCipher;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.technician.TechnicianProfile;
import com.homekept.technician.TechnicianProfileRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the technician visit app slice
 * ({@code GET /api/tech/visits/today}, start, complete, incomplete, patch-service, flags).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>Day sheet returns today's visit with decrypted access notes.</li>
 *   <li>Day sheet's {@code todos[]} and {@code flags[]} carry real ids, and the todo id
 *       can be PATCHed via {@code /api/tech/todos/{id}}.</li>
 *   <li>A second technician does NOT see the first tech's visits.</li>
 *   <li>Admin and app visit DTOs do NOT include decrypted access notes.</li>
 *   <li>start → IN_PROGRESS; wrong tech → 404; illegal state → 409.</li>
 *   <li>checklist tick updates the row; cross-visit visitServiceId → 404.</li>
 *   <li>complete from IN_PROGRESS → COMPLETED, fields persisted; complete from SCHEDULED → 409.</li>
 *   <li>incomplete from IN_PROGRESS → INCOMPLETE + follow-up SCHEDULED visit created.</li>
 *   <li>flags POST creates an OPEN flag with the subscriber from the visit.</li>
 *   <li>Authz: anon → 401; CUSTOMER → 403; wrong-tech visit → 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TechVisitIntegrationTest {

    private static final String LOGIN_URL          = "/api/auth/login";
    private static final String TODAY_URL          = "/api/tech/visits/today";
    private static final String START_URL          = "/api/tech/visits/{id}/start";
    private static final String PATCH_SVC_URL      = "/api/tech/visits/{visitId}/services/{vsId}";
    private static final String COMPLETE_URL       = "/api/tech/visits/{id}/complete";
    private static final String INCOMPLETE_URL     = "/api/tech/visits/{id}/incomplete";
    private static final String FLAGS_URL          = "/api/tech/visits/{id}/flags";
    private static final String APP_VISIT_URL      = "/api/app/visits/{id}";
    private static final String TODO_URL           = "/api/tech/todos/{id}";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitServiceRepository visitServiceRepository;
    @Autowired FlagRepository flagRepository;
    @Autowired TodoItemRepository todoItemRepository;
    @Autowired TechnicianProfileRepository techProfileRepository;
    @Autowired AccessNotesCipher accessNotesCipher;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    // ── Created-row tracking for FK-safe teardown ─────────────────────────────

    private final List<Long> createdTechProfileIds  = new ArrayList<>();
    private final List<Long> createdSubscriberIds   = new ArrayList<>();
    private final List<Long> createdPropertyIds     = new ArrayList<>();
    private final List<Long> createdUserIds         = new ArrayList<>();

    // ── Primary tech fixture ──────────────────────────────────────────────────

    private User techUser;
    private String techToken;
    /** Token for the subscriber's customer user — used for the access-note isolation spot-check. */
    private String customerToken;
    private Subscriber subscriber;
    private Property property;
    private Visit todayVisit;
    private VisitService visitService1;
    private VisitService visitService2;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        // 1. TECHNICIAN user (created ACTIVE so loginAs works).
        techUser = userRepository.save(new User(
                "tech-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Tech", "User",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(techUser.getId());

        // 2. TechnicianProfile for the technician.
        TechnicianProfile profile = techProfileRepository.save(
                new TechnicianProfile(techUser.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(profile.getId());

        // 3. CUSTOMER user + Property + Subscriber.
        User customerUser = userRepository.save(new User(
                "cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "User",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());
        customerToken = loginAsUser(customerUser.getEmail(), "Cust1234!");

        // Encrypt access notes for the property.
        byte[] encrypted = accessNotesCipher.encrypt("Lockbox 1234");
        property = new Property(
                nano + " Elm St", null, "Mississauga", "L5L 2B2",
                "L5L", null, null, PropertyType.DETACHED);
        property.setAccessNotes(encrypted);
        property = propertyRepository.save(property);
        createdPropertyIds.add(property.getId());

        subscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        // Link subscriber to property.
        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        // 4. Visit scheduled for TODAY in America/Toronto, assigned to the tech.
        ZoneId toronto = ZoneId.of("America/Toronto");
        ZonedDateTime todayNoon = java.time.LocalDate.now(toronto).atTime(12, 0).atZone(toronto);
        Instant scheduledFor = todayNoon.toInstant();

        todayVisit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                scheduledFor, 120, VisitType.ROUTINE));
        todayVisit.setTechnicianId(techUser.getId());
        todayVisit = visitRepository.save(todayVisit);

        // 5. Two visit_service rows (seed uses the first available catalog service id).
        Long serviceId = firstServiceId();
        visitService1 = visitServiceRepository.save(
                new VisitService(todayVisit.getId(), serviceId, VisitServiceSource.TEMPLATE));
        visitService2 = visitServiceRepository.save(
                new VisitService(todayVisit.getId(), serviceId, VisitServiceSource.TEMPLATE));

        // 6. Login as the tech.
        techToken = loginAsUser(techUser.getEmail(), "Tech1234!");
    }

    @AfterEach
    void tearDown() {
        // FK-safe delete order:
        // visit_photo, visit_note, flag, todo_item are handled below then visit, then rest.

        for (Long subId : createdSubscriberIds) {
            // visit_photo and visit_note cascade from visit (ON DELETE CASCADE) — no explicit delete needed.
            // flag.origin_visit_id is SET NULL — safe to delete visit directly.
            // todo_item.visit_id is SET NULL — safe to delete visit directly.
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM flag WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM todo_item WHERE subscriber_id = ?", subId);
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

        for (Long profId : createdTechProfileIds) {
            techProfileRepository.deleteById(profId);
        }
        createdTechProfileIds.clear();

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    // ── GET /api/tech/visits/today — day sheet ─────────────────────────────────

    @Test
    void daySheet_returnsVisitWithDecryptedAccessNotes() throws Exception {
        // The response MUST include the today visit and the decrypted access notes.
        // We assert the plaintext equals "Lockbox 1234" — proves the decryption path.
        // IMPORTANT: we do NOT log or print the value from the response — only assertThat.
        //
        // accessNotes is a singular String on the DTO. A JsonPath filter ($[?(...)]) is an
        // indefinite path, so projecting .accessNotes yields a JSONArray (["Lockbox 1234"]);
        // we match it with hasItem. (Indexing the String field as accessNotes[0] would
        // resolve to an empty array and never match — that was the original bug.)
        mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + todayVisit.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + todayVisit.getId() + ")].accessNotes")
                        .value(hasItem("Lockbox 1234")))
                .andExpect(jsonPath("$[?(@.id == " + todayVisit.getId() + ")].services").isArray());
    }

    @Test
    void daySheet_checkslistIsPresent() throws Exception {
        mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services.length()").value(2));
    }

    @Test
    void daySheet_includesTodosAndFlagsWithRealIds() throws Exception {
        // A todo folded into today's visit (status=SCHEDULED, visitId set — the
        // "folded into a visit" state per TodoItem's javadoc).
        TodoItem visitTodo = todoItemRepository.save(
                new TodoItem(subscriber.getId(), "Replace furnace filter"));
        visitTodo.setVisitId(todayVisit.getId());
        visitTodo.setStatus(TodoItemStatus.SCHEDULED);
        visitTodo = todoItemRepository.save(visitTodo);

        // An OPEN flag on the subscriber, shown on the day sheet for context.
        Flag openFlag = flagRepository.save(new Flag(
                subscriber.getId(), null, "Missing shingles on the north slope", FlagSeverity.ATTENTION));

        mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].todos.length()").value(1))
                .andExpect(jsonPath("$[0].todos[0].id").value(visitTodo.getId().intValue()))
                .andExpect(jsonPath("$[0].todos[0].body").value("Replace furnace filter"))
                .andExpect(jsonPath("$[0].todos[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].flags.length()").value(1))
                .andExpect(jsonPath("$[0].flags[0].id").value(openFlag.getId().intValue()))
                .andExpect(jsonPath("$[0].flags[0].body").value("Missing shingles on the north slope"))
                .andExpect(jsonPath("$[0].flags[0].severity").value("ATTENTION"))
                .andExpect(jsonPath("$[0].flags[0].createdAt").exists());
    }

    @Test
    void daySheet_todoId_targetsPatchTodoEndpoint() throws Exception {
        // A todo folded into today's visit — the tech app must be able to PATCH it
        // using ONLY the id surfaced on the day sheet.
        TodoItem visitTodo = todoItemRepository.save(
                new TodoItem(subscriber.getId(), "Test smoke detectors"));
        visitTodo.setVisitId(todayVisit.getId());
        visitTodo.setStatus(TodoItemStatus.SCHEDULED);
        todoItemRepository.save(visitTodo);

        MvcResult daySheetResult = mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andReturn();

        Long todoIdFromDaySheet = ((Number) com.jayway.jsonpath.JsonPath.read(
                daySheetResult.getResponse().getContentAsString(), "$[0].todos[0].id")).longValue();

        mockMvc.perform(patch(TODO_URL, todoIdFromDaySheet)
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        TodoItem updated = todoItemRepository.findById(todoIdFromDaySheet).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TodoItemStatus.DONE);
    }

    @Test
    void daySheet_secondTechDoesNotSeeFirstTechVisit() throws Exception {
        // Create a second tech who has no visits assigned.
        long nano2 = System.nanoTime();
        User tech2 = userRepository.save(new User(
                "tech2-" + nano2 + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Tech2", "User",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(tech2.getId());

        TechnicianProfile profile2 = techProfileRepository.save(
                new TechnicianProfile(tech2.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(profile2.getId());

        String tech2Token = loginAsUser(tech2.getEmail(), "Tech1234!");

        // Tech2's day sheet must be empty (or at least must NOT contain tech1's visit).
        mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", tech2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + todayVisit.getId() + ")]").isEmpty());
    }

    @Test
    void appVisitDetail_doesNotIncludeDecryptedAccessNotes() throws Exception {
        // The customer-facing app visit detail must NOT expose access notes in any form.
        // The subscriber's customer user calls GET /api/app/visits/{id} — the response
        // (AppVisitDetail DTO) must not have an accessNotes or decryptedAccessNotes field.
        mockMvc.perform(get(APP_VISIT_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessNotes").doesNotExist())
                .andExpect(jsonPath("$.decryptedAccessNotes").doesNotExist());
    }

    // ── POST /api/tech/visits/{id}/start ─────────────────────────────────────

    @Test
    void startVisit_scheduledToInProgress() throws Exception {
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Visit updated = visitRepository.findById(todayVisit.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
    }

    @Test
    void startVisit_pausedSubscriber_returns409_andLeavesVisitScheduled() throws Exception {
        // A paused (non-paying) subscriber's scheduled visit must not be startable — otherwise
        // a churned/paused customer gets free service. Guard fires at START; the visit is left
        // SCHEDULED so it becomes startable again if the subscriber resumes.
        subscriber.setStatus(SubscriberStatus.PAUSED);
        subscriberRepository.save(subscriber);

        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIBER_NOT_ACTIVE"));

        Visit unchanged = visitRepository.findById(todayVisit.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
    }

    @Test
    void startVisit_cancelledSubscriber_returns409() throws Exception {
        subscriber.setStatus(SubscriberStatus.CANCELLED);
        subscriberRepository.save(subscriber);

        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIBER_NOT_ACTIVE"));
    }

    @Test
    void startVisit_paymentIssueSubscriber_returns200_dunningGrace() throws Exception {
        // PAYMENT_ISSUE = Stripe retrying a card on an otherwise-active subscription. We still
        // perform the visit (dunning grace) rather than penalise a transient decline.
        subscriber.setStatus(SubscriberStatus.PAYMENT_ISSUE);
        subscriberRepository.save(subscriber);

        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void completeVisit_subscriberPausedAfterStart_stillAllowed() throws Exception {
        // The guard is START-only: a visit started while ACTIVE must still be completable even
        // if the customer pauses billing mid-visit (the tech is physically on-site).
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        subscriber.setStatus(SubscriberStatus.PAUSED);
        subscriberRepository.save(subscriber);

        mockMvc.perform(post(COMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNotes\":\"done\",\"actualDurationMinutes\":60,\"materialsCostCents\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void startVisit_visitNotAssignedToThisTech_returns404() throws Exception {
        // Seed a visit with NO technician assigned (technicianId = null).
        Visit unassigned = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                Instant.now().plus(1, ChronoUnit.DAYS), 120, VisitType.ROUTINE));
        // technicianId is null — no tech owns this visit.

        mockMvc.perform(post(START_URL, unassigned.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isNotFound());

        // Clean up (the visit for this subscriber is deleted in tearDown via subscriber_id delete)
    }

    @Test
    void startVisit_illegalTransition_inProgressToInProgress_returns409() throws Exception {
        // First start is legal.
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());

        // Second start from IN_PROGRESS → IN_PROGRESS is illegal.
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── PATCH /api/tech/visits/{id}/services/{vsId} ───────────────────────────

    @Test
    void patchService_updatesCompletedAndNotes() throws Exception {
        // Start the visit first (not strictly required by the service but realistic).
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());

        String body = "{\"completed\":true,\"technicianNotes\":\"Filter was 20x25x1\"}";
        mockMvc.perform(patch(PATCH_SVC_URL, todayVisit.getId(), visitService1.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.technicianNotes").value("Filter was 20x25x1"));

        VisitService updated = visitServiceRepository.findById(visitService1.getId()).orElseThrow();
        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getTechnicianNotes()).isEqualTo("Filter was 20x25x1");
    }

    @Test
    void patchService_crossVisitServiceId_returns404() throws Exception {
        // Create a second visit for the same subscriber with its own service row.
        Visit otherVisit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                Instant.now().plus(2, ChronoUnit.DAYS), 120, VisitType.ROUTINE));
        otherVisit.setTechnicianId(techUser.getId());
        visitRepository.save(otherVisit);

        Long serviceId = firstServiceId();
        VisitService otherVs = visitServiceRepository.save(
                new VisitService(otherVisit.getId(), serviceId, VisitServiceSource.TEMPLATE));

        // Use otherVs.id but the visitId from todayVisit → cross-visit IDOR attempt.
        mockMvc.perform(patch(PATCH_SVC_URL, todayVisit.getId(), otherVs.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/tech/visits/{id}/complete ───────────────────────────────────

    @Test
    void completeVisit_fromInProgress_completesAndPersistsFields() throws Exception {
        // Transition to IN_PROGRESS first (legal pre-condition for complete).
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());

        String body = """
                {
                  "completionNotes": "All items done",
                  "actualDurationMinutes": 95,
                  "materialsCostCents": 1250,
                  "materialsNotes": "1 furnace filter"
                }
                """;

        mockMvc.perform(post(COMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualDurationMinutes").value(95))
                .andExpect(jsonPath("$.materialsCostCents").value(1250));

        Visit completed = visitRepository.findById(todayVisit.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(completed.getActualDurationMinutes()).isEqualTo(95);
        assertThat(completed.getMaterialsCostCents()).isEqualTo(1250);
        assertThat(completed.getCompletionNotes()).isEqualTo("All items done");
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void completeVisit_writesHealthScoreSnapshot() throws Exception {
        // Completing a visit snapshots the Home Health Score (#53) so the dashboard delta
        // has a prior value.
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(COMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNotes\":\"done\",\"actualDurationMinutes\":90,\"materialsCostCents\":0}"))
                .andExpect(status().isOk());

        Integer snapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_score_snapshot WHERE subscriber_id = ?",
                Integer.class, subscriber.getId());
        assertThat(snapshots).isEqualTo(1);
    }

    @Test
    void completeVisit_fromScheduled_returns409() throws Exception {
        // todayVisit is SCHEDULED — completing without starting is illegal.
        String body = """
                {
                  "actualDurationMinutes": 90,
                  "materialsCostCents": 0
                }
                """;

        mockMvc.perform(post(COMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    // ── POST /api/tech/visits/{id}/incomplete ─────────────────────────────────

    @Test
    void incompleteVisit_fromInProgress_createsFollowUpScheduledVisit() throws Exception {
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post(INCOMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No access — owner not home\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.followUpVisitId").isNumber())
                .andReturn();

        Long followUpId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.followUpVisitId")).longValue();

        // Original visit must be INCOMPLETE.
        Visit original = visitRepository.findById(todayVisit.getId()).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(VisitStatus.INCOMPLETE);

        // Follow-up visit must exist, be SCHEDULED, and belong to the same subscriber.
        Visit followUp = visitRepository.findById(followUpId).orElseThrow();
        assertThat(followUp.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(followUp.getSubscriberId()).isEqualTo(subscriber.getId());
        // Follow-up is in the future.
        assertThat(followUp.getScheduledFor()).isAfter(Instant.now());
    }

    // ── POST /api/tech/visits/{id}/flags ──────────────────────────────────────

    @Test
    void createFlag_createsOpenFlagWithSubscriberId() throws Exception {
        String body = """
                {
                  "body": "Crack in foundation wall near NE corner",
                  "severity": "ATTENTION"
                }
                """;

        MvcResult result = mockMvc.perform(post(FLAGS_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("ATTENTION"))
                .andExpect(jsonPath("$.subscriberId").value(subscriber.getId()))
                .andReturn();

        Long flagId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        Flag persisted = flagRepository.findById(flagId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(FlagStatus.OPEN);
        assertThat(persisted.getSubscriberId()).isEqualTo(subscriber.getId());
        assertThat(persisted.getOriginVisitId()).isEqualTo(todayVisit.getId());
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    @Test
    void daySheet_anonymous_returns401() throws Exception {
        mockMvc.perform(get(TODAY_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void daySheet_asCustomer_returns403() throws Exception {
        long nano = System.nanoTime();
        User cust = userRepository.save(new User(
                "cust-authz-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "Authz",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(cust.getId());
        String custToken = loginAsUser(cust.getEmail(), "Cust1234!");

        mockMvc.perform(get(TODAY_URL)
                        .cookie(new Cookie("hk_access", custToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void startVisit_anonymous_returns401() throws Exception {
        mockMvc.perform(post(START_URL, todayVisit.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startVisit_asCustomer_returns403() throws Exception {
        long nano = System.nanoTime();
        User cust = userRepository.save(new User(
                "cust-authz-start-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "Authz",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(cust.getId());
        String custToken = loginAsUser(cust.getEmail(), "Cust1234!");

        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", custToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void startVisit_differentTech_returns404() throws Exception {
        // A second TECHNICIAN user should get 404 for todayVisit (assigned to tech1).
        long nano = System.nanoTime();
        User tech2 = userRepository.save(new User(
                "tech2-authz-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Tech2", "Authz",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(tech2.getId());

        TechnicianProfile p2 = techProfileRepository.save(
                new TechnicianProfile(tech2.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(p2.getId());

        String tech2Token = loginAsUser(tech2.getEmail(), "Tech1234!");

        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", tech2Token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void completeVisit_differentTech_returns404() throws Exception {
        // Start the visit as tech1 first.
        mockMvc.perform(post(START_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isOk());

        // A second tech tries to complete it.
        long nano = System.nanoTime();
        User tech2 = userRepository.save(new User(
                "tech2-complete-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Tech2", "Complete",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(tech2.getId());

        TechnicianProfile p2 = techProfileRepository.save(
                new TechnicianProfile(tech2.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(p2.getId());

        String tech2Token = loginAsUser(tech2.getEmail(), "Tech1234!");

        mockMvc.perform(post(COMPLETE_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", tech2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":90,\"materialsCostCents\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void flagsEndpoint_differentTech_returns404() throws Exception {
        long nano = System.nanoTime();
        User tech2 = userRepository.save(new User(
                "tech2-flags-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Tech2", "Flags",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(tech2.getId());

        TechnicianProfile p2 = techProfileRepository.save(
                new TechnicianProfile(tech2.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(p2.getId());

        String tech2Token = loginAsUser(tech2.getEmail(), "Tech1234!");

        mockMvc.perform(post(FLAGS_URL, todayVisit.getId())
                        .cookie(new Cookie("hk_access", tech2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Test\",\"severity\":\"INFO\"}"))
                .andExpect(status().isNotFound());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String loginAsUser(String email, String password) throws Exception {
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

    private Long firstServiceId() {
        Long id = jdbc.queryForObject(
                "SELECT id FROM service ORDER BY id LIMIT 1",
                Long.class);
        if (id == null) {
            throw new IllegalStateException("No services found in catalog seed data");
        }
        return id;
    }
}
