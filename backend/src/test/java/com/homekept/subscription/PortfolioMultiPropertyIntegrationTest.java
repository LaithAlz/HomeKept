package com.homekept.subscription;

import com.homekept.FakeStripeServiceConfig;
import com.homekept.TestcontainersConfiguration;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.visit.Flag;
import com.homekept.visit.FlagRepository;
import com.homekept.visit.FlagSeverity;
import com.homekept.visit.TodoItem;
import com.homekept.visit.TodoItemRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the multi-property portfolio, Phase 1 (see
 * docs/portfolio-multi-property-proposal.md): a user owning several {@link Subscriber}
 * rows (one per property), {@code GET /api/app/properties}, and the optional
 * {@code propertyId} scoping on the existing per-property {@code /api/app/*} endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>A landlord with two properties sees both from {@code GET /api/app/properties}.</li>
 *   <li>Per-property GETs (visits, health-score, todos, subscription, account) scoped by
 *       {@code propertyId} return only that property's data.</li>
 *   <li>A {@code propertyId} belonging to another user → 404 (ownership, not 403).</li>
 *   <li>Omitted {@code propertyId} with exactly one property still works (backward
 *       compatible with the pre-portfolio single-property app).</li>
 *   <li>Omitted {@code propertyId} with several properties defaults to the earliest
 *       created ("primary").</li>
 *   <li>A mutating action endpoint ({@code POST .../pause}) also respects
 *       {@code propertyId} scoping.</li>
 * </ul>
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeStripeServiceConfig.class})
class PortfolioMultiPropertyIntegrationTest {

    private static final String LOGIN_URL       = "/api/auth/login";
    private static final String PROPERTIES_URL  = "/api/app/properties";
    private static final String VISITS_URL      = "/api/app/visits";
    private static final String VISIT_URL       = "/api/app/visits/{id}";
    private static final String HEALTH_URL      = "/api/app/health-score";
    private static final String TODOS_URL       = "/api/app/todos";
    private static final String TODO_URL        = "/api/app/todos/{id}";
    private static final String SUBSCRIPTION_URL = "/api/app/subscription";
    private static final String ACCOUNT_URL     = "/api/app/account";
    private static final String PAUSE_URL       = "/api/app/subscription/pause";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired TodoItemRepository todoItemRepository;
    @Autowired FlagRepository flagRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeServiceConfig.RecordingStripeService recordingStripe;

    private final List<Long> createdUserIds       = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();

    /** The landlord: one user owning two subscribers (propertyA created first, propertyB second). */
    private User landlordUser;
    private String landlordToken;
    private Property propertyA;
    private Subscriber subscriberA;
    private Property propertyB;
    private Subscriber subscriberB;

    /** A different user, used to prove cross-user propertyId ownership isolation. */
    private Property otherProperty;

    /** A single-property user, used to prove omitted-propertyId backward compatibility. */
    private User soloUser;
    private String soloToken;
    private Property soloProperty;
    private Subscriber soloSubscriber;

    @BeforeEach
    void seed() throws Exception {
        recordingStripe.reset();
        long nano = System.nanoTime();

        // ── Landlord: two properties ────────────────────────────────────────────
        landlordUser = userRepository.save(new User(
                "portfolio-landlord-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Landlord", "Owner",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(landlordUser.getId());

        propertyA = propertyRepository.save(new Property(
                nano + " First St", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(propertyA.getId());
        subscriberA = subscriberRepository.save(new Subscriber(
                landlordUser.getId(), propertyA.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriberA.getId());

        propertyB = propertyRepository.save(new Property(
                nano + " Second Ave", null, "Brampton", "L6P 1A1",
                "L6P", null, null, PropertyType.TOWNHOUSE));
        createdPropertyIds.add(propertyB.getId());
        subscriberB = subscriberRepository.save(new Subscriber(
                landlordUser.getId(), propertyB.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriberB.getId());

        landlordToken = loginAs(landlordUser.getEmail(), "Test1234!");

        // ── A different user (used for cross-user ownership tests) ─────────────
        User otherUser = userRepository.save(new User(
                "portfolio-other-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Other", "Owner",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(otherUser.getId());

        otherProperty = propertyRepository.save(new Property(
                nano + " Third Rd", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(otherProperty.getId());
        Subscriber otherSubscriber = subscriberRepository.save(new Subscriber(
                otherUser.getId(), otherProperty.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(otherSubscriber.getId());

        // ── A single-property user (used for backward-compatibility tests) ─────
        soloUser = userRepository.save(new User(
                "portfolio-solo-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Solo", "Owner",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(soloUser.getId());

        soloProperty = propertyRepository.save(new Property(
                nano + " Fourth Cres", null, "Oakville", "L6H 1A1",
                "L6H", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(soloProperty.getId());
        soloSubscriber = subscriberRepository.save(new Subscriber(
                soloUser.getId(), soloProperty.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(soloSubscriber.getId());

        soloToken = loginAs(soloUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM todo_item WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM flag WHERE subscriber_id = ?", subId);
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

    // ── GET /api/app/properties ──────────────────────────────────────────────

    @Test
    void listProperties_twoProperties_returnsBothOrderedOldestFirst() throws Exception {
        assignPlan(subscriberA, "ESSENTIAL");
        assignPlan(subscriberB, "COMPLETE");
        Instant scheduledFor = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        seedVisit(subscriberB, scheduledFor);
        seedTodo(subscriberB, "Fix gutter");
        seedTodo(subscriberB, "Check attic insulation");

        mockMvc.perform(get(PROPERTIES_URL).cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subscriberId").value(subscriberA.getId()))
                .andExpect(jsonPath("$[0].propertyId").value(propertyA.getId()))
                .andExpect(jsonPath("$[0].streetAddress").value(propertyA.getStreetAddress()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].planCode").value("ESSENTIAL"))
                .andExpect(jsonPath("$[0].healthScore").value(100))
                .andExpect(jsonPath("$[0].openItemsCount").value(0))
                .andExpect(jsonPath("$[1].subscriberId").value(subscriberB.getId()))
                .andExpect(jsonPath("$[1].propertyId").value(propertyB.getId()))
                .andExpect(jsonPath("$[1].planCode").value("COMPLETE"))
                .andExpect(jsonPath("$[1].openItemsCount").value(2))
                .andExpect(jsonPath("$[1].nextVisitDate").value(scheduledFor.toString()));
    }

    @Test
    void listProperties_userWithNoSubscriber_returnsEmptyArrayNot404() throws Exception {
        String token = createCustomerWithNoSubscriber();

        mockMvc.perform(get(PROPERTIES_URL).cookie(new Cookie("hk_access", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listProperties_anonymous_returns401() throws Exception {
        mockMvc.perform(get(PROPERTIES_URL)).andExpect(status().isUnauthorized());
    }

    // ── GET /api/app/visits?propertyId= ──────────────────────────────────────

    @Test
    void listVisits_scopedByPropertyId_returnsOnlyThatPropertysVisits() throws Exception {
        Visit visitA = seedVisit(subscriberA, Instant.now().plus(10, ChronoUnit.DAYS));
        Visit visitB = seedVisit(subscriberB, Instant.now().plus(20, ChronoUnit.DAYS));

        MvcResult resultA = mockMvc.perform(get(VISITS_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<Integer> idsA = com.jayway.jsonpath.JsonPath.read(
                resultA.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsA).containsExactly(visitA.getId().intValue());

        MvcResult resultB = mockMvc.perform(get(VISITS_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<Integer> idsB = com.jayway.jsonpath.JsonPath.read(
                resultB.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsB).containsExactly(visitB.getId().intValue());
    }

    @Test
    void getVisit_crossPropertyId_scopedAway404() throws Exception {
        Visit visitB = seedVisit(subscriberB, Instant.now().plus(10, ChronoUnit.DAYS));

        // visitB does not belong to subscriberA — scoping to propertyA must 404 it.
        mockMvc.perform(get(VISIT_URL, visitB.getId())
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isNotFound());

        // Scoping to its own property finds it.
        mockMvc.perform(get(VISIT_URL, visitB.getId())
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visitB.getId()));
    }

    // ── GET /api/app/health-score?propertyId= ────────────────────────────────

    @Test
    void getHealthScore_scopedByPropertyId_reflectsThatPropertysFlags() throws Exception {
        flagRepository.save(new Flag(subscriberB.getId(), null, "Observed issue", FlagSeverity.URGENT));

        mockMvc.perform(get(HEALTH_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100));

        mockMvc.perform(get(HEALTH_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80));
    }

    // ── GET/POST/DELETE /api/app/todos?propertyId= ───────────────────────────

    @Test
    void listTodos_scopedByPropertyId_returnsOnlyThatPropertysTodos() throws Exception {
        seedTodo(subscriberA, "A's todo");
        seedTodo(subscriberB, "B's todo");

        MvcResult resultA = mockMvc.perform(get(TODOS_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<String> bodiesA = com.jayway.jsonpath.JsonPath.read(
                resultA.getResponse().getContentAsString(), "$[*].body");
        assertThat(bodiesA).containsExactly("A's todo");
    }

    @Test
    void createTodo_scopedByPropertyId_createsForThatSubscriber() throws Exception {
        mockMvc.perform(post(TODOS_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Replace furnace filter\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriberId").value(subscriberB.getId()));
    }

    @Test
    void deleteTodo_crossPropertyId_returns404AndDoesNotDelete() throws Exception {
        TodoItem todo = seedTodo(subscriberA, "A's todo");

        mockMvc.perform(delete(TODO_URL, todo.getId())
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isNotFound());

        assertThat(todoItemRepository.findById(todo.getId())).isPresent();
    }

    // ── GET /api/app/subscription?propertyId= & /api/app/account?propertyId= ─

    @Test
    void getSubscription_scopedByPropertyId_returnsThatPropertysPlan() throws Exception {
        assignPlan(subscriberA, "ESSENTIAL");
        assignPlan(subscriberB, "PREMIER");

        mockMvc.perform(get(SUBSCRIPTION_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("ESSENTIAL"));

        mockMvc.perform(get(SUBSCRIPTION_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PREMIER"));
    }

    @Test
    void getAccount_scopedByPropertyId_returnsThatPropertysAddress() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetAddress").value(propertyA.getStreetAddress()))
                .andExpect(jsonPath("$.city").value("Mississauga"));

        mockMvc.perform(get(ACCOUNT_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetAddress").value(propertyB.getStreetAddress()))
                .andExpect(jsonPath("$.city").value("Brampton"));
    }

    // ── Ownership: propertyId belonging to another user → 404 ───────────────

    @Test
    void getSubscription_propertyIdBelongingToAnotherUser_returns404() throws Exception {
        mockMvc.perform(get(SUBSCRIPTION_URL)
                        .param("propertyId", otherProperty.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void createTodo_propertyIdBelongingToAnotherUser_returns404() throws Exception {
        mockMvc.perform(post(TODOS_URL)
                        .param("propertyId", otherProperty.getId().toString())
                        .cookie(landlordCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Should not be created\"}"))
                .andExpect(status().isNotFound());
    }

    // ── Omitted propertyId: defaulting rule ──────────────────────────────────

    @Test
    void getSubscription_omittedPropertyId_singleSubscriberUser_stillWorks() throws Exception {
        assignPlan(soloSubscriber, "COMPLETE");

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(new Cookie("hk_access", soloToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("COMPLETE"));
    }

    @Test
    void getSubscription_omittedPropertyId_multiPropertyUser_defaultsToEarliestCreated() throws Exception {
        assignPlan(subscriberA, "ESSENTIAL");
        assignPlan(subscriberB, "PREMIER");

        mockMvc.perform(get(SUBSCRIPTION_URL).cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("ESSENTIAL"));
    }

    @Test
    void getAccount_omittedPropertyId_multiPropertyUser_defaultsToEarliestCreated() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL).cookie(landlordCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetAddress").value(propertyA.getStreetAddress()));
    }

    // ── Write/action endpoint scoping: POST /api/app/subscription/pause ─────

    @Test
    void pause_scopedByPropertyId_actsOnThatSubscribersBilling() throws Exception {
        // subscriberA has no Stripe subscription id — pausing it is a 409 NO_BILLING_ACCOUNT.
        subscriberB.setStripeCustomerId("cus_test_portfolio_b");
        subscriberB.setStripeSubscriptionId("sub_test_portfolio_b");
        subscriberRepository.save(subscriberB);

        mockMvc.perform(post(PAUSE_URL)
                        .param("propertyId", propertyA.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_BILLING_ACCOUNT"));

        mockMvc.perform(post(PAUSE_URL)
                        .param("propertyId", propertyB.getId().toString())
                        .cookie(landlordCookie()))
                .andExpect(status().isOk());

        assertThat(recordingStripe.pausedSubscriptionIds).containsExactly("sub_test_portfolio_b");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assignPlan(Subscriber subscriber, String planCode) {
        Long planTierId = jdbc.queryForObject(
                "SELECT id FROM plan_tier WHERE code = ?", Long.class, planCode);
        subscriber.setPlanTierId(planTierId);
        subscriberRepository.save(subscriber);
    }

    private Visit seedVisit(Subscriber subscriber, Instant scheduledFor) {
        return visitRepository.save(new Visit(
                subscriber.getId(), subscriber.getPropertyId(), null,
                scheduledFor.truncatedTo(ChronoUnit.SECONDS), 120, VisitType.ROUTINE));
    }

    private TodoItem seedTodo(Subscriber subscriber, String body) {
        return todoItemRepository.save(new TodoItem(subscriber.getId(), body));
    }

    private Cookie landlordCookie() {
        return new Cookie("hk_access", landlordToken);
    }

    private String createCustomerWithNoSubscriber() throws Exception {
        long nano = System.nanoTime();
        String email = "portfolio-nosub-" + nano + "@test.local";
        User user = userRepository.save(new User(
                email, passwordEncoder.encode("Test1234!"),
                "No", "Subscriber",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(user.getId());
        return loginAs(email, "Test1234!");
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
