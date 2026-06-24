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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the technician todo patch endpoint
 * ({@code PATCH /api/tech/todos/{id}}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>PATCH {status:DONE} → status updated to DONE, no decline note.</li>
 *   <li>PATCH {status:DECLINED} without a note → 400 (validation).</li>
 *   <li>PATCH {status:DECLINED} with a note → DECLINED status + declineNote persisted.</li>
 *   <li>Todo whose subscriber has NO visit assigned to this tech → 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TechTodoIntegrationTest {

    private static final String LOGIN_URL    = "/api/auth/login";
    private static final String TODO_URL     = "/api/tech/todos/{id}";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired TodoItemRepository todoItemRepository;
    @Autowired TechnicianProfileRepository techProfileRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdTechProfileIds = new ArrayList<>();
    private final List<Long> createdSubscriberIds  = new ArrayList<>();
    private final List<Long> createdPropertyIds    = new ArrayList<>();
    private final List<Long> createdUserIds        = new ArrayList<>();

    private User techUser;
    private String techToken;
    private Subscriber subscriber;
    private Property property;
    private Visit todayVisit;
    private TodoItem todo;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        techUser = userRepository.save(new User(
                "todo-tech-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Todo", "Tech",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(techUser.getId());

        TechnicianProfile profile = techProfileRepository.save(
                new TechnicianProfile(techUser.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(profile.getId());

        User customerUser = userRepository.save(new User(
                "todo-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Todo", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        property = propertyRepository.save(new Property(
                nano + " Todo St", null, "Mississauga", "L5L 4D4",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        subscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        // Create a visit scheduled for today in America/Toronto, assigned to the tech.
        ZoneId toronto = ZoneId.of("America/Toronto");
        ZonedDateTime todayNoon = java.time.LocalDate.now(toronto).atTime(12, 0).atZone(toronto);
        todayVisit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                todayNoon.toInstant(), 120, VisitType.ROUTINE));
        todayVisit.setTechnicianId(techUser.getId());
        todayVisit = visitRepository.save(todayVisit);

        // Create an OPEN todo for this subscriber.
        todo = todoItemRepository.save(new TodoItem(subscriber.getId(), "Check CO detector in basement"));

        techToken = loginAsUser(techUser.getEmail(), "Tech1234!");
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
            jdbc.update("DELETE FROM todo_item WHERE subscriber_id = ?", subId);
            jdbc.update("DELETE FROM flag WHERE subscriber_id = ?", subId);
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

    // ── PATCH /api/tech/todos/{id} — DONE ────────────────────────────────────

    @Test
    void patchTodo_statusDone_updatesStatus() throws Exception {
        mockMvc.perform(patch(TODO_URL, todo.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.declineNote").value(nullValue()));

        TodoItem updated = todoItemRepository.findById(todo.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TodoItemStatus.DONE);
        assertThat(updated.getDeclineNote()).isNull();
    }

    // ── PATCH /api/tech/todos/{id} — DECLINED without note → 400 ─────────────

    @Test
    void patchTodo_declinedWithoutNote_returns400() throws Exception {
        mockMvc.perform(patch(TODO_URL, todo.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isBadRequest());

        // Status must NOT have changed.
        TodoItem unchanged = todoItemRepository.findById(todo.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TodoItemStatus.OPEN);
    }

    // ── PATCH /api/tech/todos/{id} — DECLINED with note ──────────────────────

    @Test
    void patchTodo_declinedWithNote_updatesStatusAndDeclineNote() throws Exception {
        mockMvc.perform(patch(TODO_URL, todo.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECLINED\",\"note\":\"Requires licensed electrician\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineNote").value("Requires licensed electrician"));

        TodoItem updated = todoItemRepository.findById(todo.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TodoItemStatus.DECLINED);
        assertThat(updated.getDeclineNote()).isEqualTo("Requires licensed electrician");
    }

    // ── PATCH /api/tech/todos/{id} — subscriber has no active visit for this tech → 404 ──

    @Test
    void patchTodo_subscriberHasNoVisitAssignedToThisTech_returns404() throws Exception {
        // Create a second subscriber whose todo has no visit assigned to our tech.
        long nano2 = System.nanoTime();
        User custUser2 = userRepository.save(new User(
                "todo-cust2-" + nano2 + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Todo", "Customer2",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(custUser2.getId());

        Property prop2 = propertyRepository.save(new Property(
                nano2 + " Other St", null, "Mississauga", "L5L 5E5",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(prop2.getId());

        Subscriber sub2 = subscriberRepository.save(new Subscriber(
                custUser2.getId(), prop2.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(sub2.getId());

        // A todo for sub2 — NO visit assigned to our tech for sub2.
        TodoItem unownedTodo = todoItemRepository.save(
                new TodoItem(sub2.getId(), "Check attic insulation"));

        mockMvc.perform(patch(TODO_URL, unownedTodo.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
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
}
