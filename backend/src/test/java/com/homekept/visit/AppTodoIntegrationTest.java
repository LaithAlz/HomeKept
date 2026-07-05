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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AppTodoController} — the customer-facing "your list"
 * endpoints ({@code GET/POST /api/app/todos}, {@code DELETE /api/app/todos/{id}}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authenticated CUSTOMER can list only their own todo items, newest first.</li>
 *   <li>Authenticated CUSTOMER can create a new OPEN todo item.</li>
 *   <li>Blank body → 400 (validation).</li>
 *   <li>Authenticated CUSTOMER can delete their own item.</li>
 *   <li>Deleting another subscriber's item → 404 (ownership, not 403).</li>
 *   <li>Deleting a non-existent id → 404.</li>
 *   <li>Anonymous → 401 on every endpoint.</li>
 *   <li>ADMIN on CUSTOMER endpoints → 403 (wrong role).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppTodoIntegrationTest {

    private static final String LIST_CREATE_URL = "/api/app/todos";
    private static final String DELETE_URL      = "/api/app/todos/{id}";
    private static final String LOGIN_URL       = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired TodoItemRepository todoItemRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds       = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();

    /** The CUSTOMER under test. */
    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerToken;

    /** A second CUSTOMER used for ownership isolation tests. */
    private Subscriber otherSubscriber;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        // Primary customer.
        customerUser = userRepository.save(new User(
                "app-todo-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "App", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        Property customerProp = propertyRepository.save(new Property(
                nano + " Todo Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(customerProp.getId());

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), customerProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(customerSubscriber.getId());

        // Other customer (different subscriber) — used to prove ownership isolation.
        User otherUser = userRepository.save(new User(
                "app-todo-other-" + nano + "@test.local",
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
        for (Long subId : createdSubscriberIds) {
            jdbc.update("DELETE FROM todo_item WHERE subscriber_id = ?", subId);
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

    // ── GET /api/app/todos ────────────────────────────────────────────────────

    @Test
    void listTodos_authenticatedCustomer_returnsOwnItemsOnly() throws Exception {
        seedTodo(customerSubscriber, "Check smoke detector battery");
        seedTodo(customerSubscriber, "Fix squeaky hinge");
        seedTodo(otherSubscriber, "Someone else's item");

        MvcResult result = mockMvc.perform(get(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> bodies = com.jayway.jsonpath.JsonPath.read(body, "$[*].body");
        assertThat(bodies).containsExactlyInAnyOrder(
                "Check smoke detector battery", "Fix squeaky hinge");
    }

    @Test
    void listTodos_newestFirst() throws Exception {
        TodoItem older = seedTodo(customerSubscriber, "Older item");
        TodoItem newer = seedTodo(customerSubscriber, "Newer item");

        mockMvc.perform(get(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()));
    }

    @Test
    void listTodos_anonymous_returns401() throws Exception {
        mockMvc.perform(get(LIST_CREATE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTodos_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();
        mockMvc.perform(get(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/app/todos ───────────────────────────────────────────────────

    @Test
    void createTodo_validBody_returns201WithOpenStatus() throws Exception {
        mockMvc.perform(post(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Replace furnace filter\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Replace furnace filter"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.subscriberId").value(customerSubscriber.getId()))
                .andExpect(jsonPath("$.visitId").value(nullValue()));

        List<TodoItem> items = todoItemRepository.findBySubscriberIdOrderByCreatedAtDesc(
                customerSubscriber.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getBody()).isEqualTo("Replace furnace filter");
        assertThat(items.get(0).getStatus()).isEqualTo(TodoItemStatus.OPEN);
        assertThat(items.get(0).getVisitId()).isNull();
    }

    @Test
    void createTodo_blankBody_returns400() throws Exception {
        mockMvc.perform(post(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(todoItemRepository.findBySubscriberIdOrderByCreatedAtDesc(
                customerSubscriber.getId())).isEmpty();
    }

    @Test
    void createTodo_missingBody_returns400() throws Exception {
        mockMvc.perform(post(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTodo_anonymous_returns401() throws Exception {
        mockMvc.perform(post(LIST_CREATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Replace furnace filter\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTodo_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();
        mockMvc.perform(post(LIST_CREATE_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Replace furnace filter\"}"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/app/todos/{id} ────────────────────────────────────────────

    @Test
    void deleteTodo_ownItem_returns204AndRemovesRow() throws Exception {
        TodoItem todo = seedTodo(customerSubscriber, "Fix squeaky hinge");

        mockMvc.perform(delete(DELETE_URL, todo.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNoContent());

        assertThat(todoItemRepository.findById(todo.getId())).isEmpty();
    }

    @Test
    void deleteTodo_otherSubscribersItem_returns404() throws Exception {
        TodoItem othersTodo = seedTodo(otherSubscriber, "Someone else's item");

        mockMvc.perform(delete(DELETE_URL, othersTodo.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());

        // Must NOT have been deleted.
        assertThat(todoItemRepository.findById(othersTodo.getId())).isPresent();
    }

    @Test
    void deleteTodo_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete(DELETE_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTodo_anonymous_returns401() throws Exception {
        TodoItem todo = seedTodo(customerSubscriber, "Fix squeaky hinge");

        mockMvc.perform(delete(DELETE_URL, todo.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteTodo_asAdmin_returns403() throws Exception {
        TodoItem todo = seedTodo(customerSubscriber, "Fix squeaky hinge");
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(delete(DELETE_URL, todo.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TodoItem seedTodo(Subscriber subscriber, String body) {
        return todoItemRepository.save(new TodoItem(subscriber.getId(), body));
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
        String email = "app-todo-admin-" + nano + "@test.local";
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
