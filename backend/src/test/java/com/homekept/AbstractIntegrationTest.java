package com.homekept;

import com.homekept.identity.AdminSeeder;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for HTTP-driven integration tests. Boots a real Spring context against a real
 * Postgres via Testcontainers, and gives every subclass a clean database before each test:
 * every mutable table is truncated (not rolled back — see below) and the admin user is
 * re-seeded the way the app does on startup.
 *
 * <p>Deliberately NOT {@code @Transactional}: several service methods (e.g. visit scheduling)
 * register {@code AFTER_COMMIT} hooks that only fire on a real commit. A test wrapped in a
 * rolled-back transaction would silently make those hooks never run. Truncation-based cleanup
 * only — see {@link #truncateAllMutableTables()}.
 *
 * <p>Test doubles imported here ({@link RecordingAnalyticsConfig}, {@link FakeEmailSenderConfig})
 * are safe for every test: neither changes behaviour that any test asserts on directly (the real
 * analytics/email transports already no-op in the test profile). {@link FakeStripeServiceConfig}
 * and the storage fakes ({@link FakeStorageServiceConfig}, {@link FlakyStorageServiceConfig}) stay
 * opt-in per test class — at least one test (photo endpoints) asserts on the REAL
 * {@code R2StorageService}'s unconfigured-503 behaviour, so those cannot be swapped in globally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingAnalyticsConfig.class, FakeEmailSenderConfig.class})
public abstract class AbstractIntegrationTest {

    /**
     * {@code Instant.now()} at the precision Postgres will actually give back.
     *
     * <p>{@code TIMESTAMPTZ} stores microseconds. {@code Instant.now()} resolves to
     * nanoseconds on Linux but only microseconds on macOS, so a test that persists
     * {@code Instant.now()} and then asserts the round-trip equals it passes on a developer's
     * Mac and fails in CI with a diff of trailing digits
     * ({@code ...57.009405092Z} vs {@code ...57.009405Z}). That is a platform artifact, not a
     * behaviour difference, and it wastes a CI cycle every time someone rediscovers it.
     *
     * <p>Use this instead of {@code Instant.now()} anywhere the value is written to the
     * database and later compared. Truncating is also closer to what the test means: the
     * question is whether the right instant was stored, not whether the JVM clock's spare
     * nanoseconds survived a column that has nowhere to put them.
     */
    protected static java.time.Instant dbNow() {
        return java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    /**
     * Every table Flyway V1..V10 creates, EXCLUDING the seeded catalog tables (plan_tier,
     * service, plan_tier_service, visit_template, visit_template_service — never mutated by
     * app code, only read) and Flyway's own schema-history table. Keep this in sync with
     * {@code src/main/resources/db/migration/*.sql}.
     */
    private static final String MUTABLE_TABLES = String.join(", ",
            "users", "refresh_tokens", "password_reset_tokens",
            "walkthrough_booking", "walkthrough_booking_day_preference",
            "property", "subscriber", "subscription_event", "activation_token",
            "technician_profile", "visit_photo", "visit_note", "flag", "todo_item",
            "visit", "visit_service", "visit_event",
            "reschedule_request", "reschedule_request_slot",
            "health_score_snapshot",
            "notification_log");

    protected static final String LOGIN_URL = "/api/auth/login";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected AdminSeeder adminSeeder;

    @BeforeEach
    void truncateAllMutableTables() {
        jdbcTemplate.execute("TRUNCATE TABLE " + MUTABLE_TABLES + " RESTART IDENTITY CASCADE");
        // The app seeds the admin user once on startup via AdminSeeder (an ApplicationRunner);
        // truncating users wipes it, so every test needs it re-seeded to match production.
        adminSeeder.run(null);
    }

    /**
     * Creates a fresh user with the given role and status ACTIVE, logs in via the real
     * {@code POST /api/auth/login} endpoint, and returns the {@code hk_access} cookie value.
     */
    protected String loginAs(Role role) throws Exception {
        String email = "test-" + role.name().toLowerCase() + "-" + System.nanoTime() + "@test.local";
        String password = "Test1234!";
        userRepository.save(new User(email, passwordEncoder.encode(password), "Test", "User", role, UserStatus.ACTIVE));
        return loginAs(email, password);
    }

    /** Logs in with the given credentials and returns the {@code hk_access} cookie value. */
    protected String loginAs(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return extractCookieValue(loginResult.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }

    /**
     * Extracts a cookie value from Set-Cookie header strings.
     * Each header looks like: {@code name=value; Path=/; HttpOnly; ...}
     */
    protected static String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }

    /** Extracts the {@code $.id} field of an MvcResult's JSON body as a {@link Long}. */
    protected static Long idFrom(MvcResult result) {
        return idFrom(result, "$.id");
    }

    /** Extracts the given JsonPath field of an MvcResult's JSON body as a {@link Long}. */
    protected static Long idFrom(MvcResult result, String jsonPath) {
        try {
            return ((Number) com.jayway.jsonpath.JsonPath.read(
                    result.getResponse().getContentAsString(), jsonPath)).longValue();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
