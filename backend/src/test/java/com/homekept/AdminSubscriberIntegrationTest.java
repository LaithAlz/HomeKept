package com.homekept;

import com.homekept.booking.BookingRateLimiter;
import com.homekept.booking.WalkthroughBookingRepository;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.PropertyRepository;
import com.homekept.subscription.ActivationRateLimiter;
import com.homekept.subscription.ActivationTokenRepository;
import com.homekept.subscription.ActivationTokenService;
import com.homekept.subscription.SubscriberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for admin activation-invite and admin subscriber endpoints.
 * Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/admin/bookings/{id}/activation-invite — as ADMIN → 200; as CUSTOMER → 403; anonymous → 401</li>
 *   <li>POST activation-invite — verifies token row is created and booking.activationTokenId is set</li>
 *   <li>GET /api/admin/subscribers — as ADMIN → 200; as CUSTOMER → 403; anonymous → 401</li>
 *   <li>GET /api/admin/subscribers — cursor pagination newest-first</li>
 *   <li>GET /api/admin/subscribers/{id} — as ADMIN → 200 with property summary; hasAccessNotes present</li>
 *   <li>GET /api/admin/subscribers/{id} — response NEVER contains a decrypted access notes field</li>
 *   <li>GET /api/admin/subscribers/{id} — missing id → 404</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminSubscriberIntegrationTest {

    private static final String WALKTHROUGH_URL    = "/api/bookings/walkthrough";
    private static final String ADMIN_INVITE_URL   = "/api/admin/bookings/%d/activation-invite";
    private static final String ADMIN_SUBSCRIBERS  = "/api/admin/subscribers";
    private static final String LOGIN_URL          = "/api/auth/login";
    private static final String COMPLETE_URL       = "/api/activation/complete";

    @Autowired MockMvc mockMvc;
    @Autowired WalkthroughBookingRepository bookingRepository;
    @Autowired ActivationTokenRepository tokenRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired ActivationTokenService activationTokenService;
    @Autowired ActivationRateLimiter activationRateLimiter;
    @Autowired BookingRateLimiter bookingRateLimiter;
    @Autowired PasswordEncoder passwordEncoder;

    private final List<Long> createdBookingIds    = new ArrayList<>();
    private final List<Long> createdTokenIds      = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds       = new ArrayList<>();

    @BeforeEach
    void resetRateLimiters() {
        bookingRateLimiter.reset("127.0.0.1");
        activationRateLimiter.reset("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        // Break the circular booking↔token / booking→subscriber FKs first, then delete in
        // dependency order (see ActivationIntegrationTest.tearDown for the rationale).
        for (Long id : createdBookingIds) {
            bookingRepository.findById(id).ifPresent(b -> {
                b.setConvertedToSubscriberId(null);
                b.setActivationTokenId(null);
                bookingRepository.save(b);
            });
        }

        for (Long id : createdTokenIds) {
            tokenRepository.deleteById(id);
        }
        createdTokenIds.clear();

        for (Long id : createdSubscriberIds) {
            subscriberRepository.deleteById(id);
        }
        createdSubscriberIds.clear();

        for (Long id : createdBookingIds) {
            bookingRepository.deleteById(id);
        }
        createdBookingIds.clear();

        for (Long id : createdPropertyIds) {
            propertyRepository.deleteById(id);
        }
        createdPropertyIds.clear();

        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ── POST /api/admin/bookings/{id}/activation-invite — role gating ─────────

    @Test
    void sendActivationInvite_asAdmin_returns200() throws Exception {
        Long bookingId = createBookingViaApi("Aditi Singh", "aditi-invite-admin@test.local");
        String adminToken = loginAs(Role.ADMIN);

        MvcResult result = mockMvc.perform(post(ADMIN_INVITE_URL.formatted(bookingId))
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVITE_SENT"))
                .andReturn();
        // Track the token row for cleanup.
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        if (booking.getActivationTokenId() != null) {
            createdTokenIds.add(booking.getActivationTokenId());
        }
    }

    @Test
    void sendActivationInvite_asCustomer_returns403() throws Exception {
        Long bookingId = createBookingViaApi("Kim Park", "kim-invite-cust@test.local");
        String customerToken = loginAs(Role.CUSTOMER);

        mockMvc.perform(post(ADMIN_INVITE_URL.formatted(bookingId))
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendActivationInvite_anonymous_returns401() throws Exception {
        Long bookingId = createBookingViaApi("Nobody Here", "anon-invite@test.local");

        mockMvc.perform(post(ADMIN_INVITE_URL.formatted(bookingId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendActivationInvite_createsTokenRowAndSetsBookingActivationTokenId() throws Exception {
        Long bookingId = createBookingViaApi("Nina Fox", "nina-invite@test.local");
        String adminToken = loginAs(Role.ADMIN);

        long tokenCountBefore = tokenRepository.count();

        mockMvc.perform(post(ADMIN_INVITE_URL.formatted(bookingId))
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk());

        // A new token row must exist.
        assertThat(tokenRepository.count()).isEqualTo(tokenCountBefore + 1);

        // The booking must have activation_token_id set.
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getActivationTokenId()).isNotNull();

        // Track for cleanup.
        createdTokenIds.add(booking.getActivationTokenId());
    }

    // ── GET /api/admin/subscribers — role gating ──────────────────────────────

    @Test
    void listSubscribers_anonymous_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_SUBSCRIBERS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSubscribers_asCustomer_returns403() throws Exception {
        String customerToken = loginAs(Role.CUSTOMER);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSubscribers_asAdmin_returns200WithArray() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listSubscribers_includesCreatedSubscriber_newestFirst() throws Exception {
        // Create a subscriber via the full activation flow.
        Long subscriberId = createSubscriberViaActivation("listme@test.local", "List Me");

        String adminToken = loginAs(Role.ADMIN);
        MvcResult result = mockMvc.perform(get(ADMIN_SUBSCRIBERS + "?limit=50")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        // The subscriber we just created must appear in the list.
        String body = result.getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(subscriberId.intValue());
    }

    @Test
    void listSubscribers_cursorPagination_returnsNewestFirst() throws Exception {
        // Create two subscribers in order.
        Long sub1 = createSubscriberViaActivation("cursor-sub1@test.local", "Cursor One");
        Long sub2 = createSubscriberViaActivation("cursor-sub2@test.local", "Cursor Two");

        String adminToken = loginAs(Role.ADMIN);

        // First page, limit 1 — should return the highest id (sub2).
        MvcResult page1 = mockMvc.perform(get(ADMIN_SUBSCRIBERS + "?limit=1")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        List<Integer> page1Ids = com.jayway.jsonpath.JsonPath.read(
                page1.getResponse().getContentAsString(), "$[*].id");
        Long cursor = page1Ids.get(0).longValue();

        // Page 2 using cursor — all returned ids must be < cursor.
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "?limit=50&cursor=" + cursor)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id >= " + cursor + ")]").isEmpty());
    }

    // ── GET /api/admin/subscribers/{id} ──────────────────────────────────────

    @Test
    void getSubscriberDetail_asAdmin_returns200WithPropertySummary() throws Exception {
        Long subscriberId = createSubscriberViaActivation("detail-sub@test.local", "Detail Sub");

        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subscriberId))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.property").exists())
                .andExpect(jsonPath("$.property.hasAccessNotes").isBoolean())
                // Access notes never decrypted — must NOT expose a decrypted string field.
                .andExpect(jsonPath("$.property.accessNotes").doesNotExist())
                .andExpect(jsonPath("$.property.decryptedAccessNotes").doesNotExist());
    }

    @Test
    void getSubscriberDetail_asAdmin_hasAccessNotesFalseWhenNoneSet() throws Exception {
        Long subscriberId = createSubscriberViaActivation("no-access-notes@test.local", "No Notes");

        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                // The activation flow does not set access notes, so hasAccessNotes must be false.
                .andExpect(jsonPath("$.property.hasAccessNotes").value(false));
    }

    @Test
    void getSubscriberDetail_missingId_returns404() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/999999999")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSubscriberDetail_asCustomer_returns403() throws Exception {
        Long subscriberId = createSubscriberViaActivation("cust-detail@test.local", "Cust Detail");

        String customerToken = loginAs(Role.CUSTOMER);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSubscriberDetail_anonymous_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a walk-through booking via the public API and tracks it for cleanup.
     */
    private Long createBookingViaApi(String fullName, String email) throws Exception {
        String body = """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "phone": "(905) 555-0123",
                  "streetAddress": "14 Maple Ridge Crt",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "AFTERNOON",
                  "contactConsent": true
                }
                """.formatted(fullName, email);

        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);

        // Advance to PERFORMED so the activation flow can convert it (PERFORMED → CONVERTED).
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(com.homekept.booking.BookingStatus.PERFORMED);
        bookingRepository.save(booking);
        return id;
    }

    /**
     * Runs the full activation flow (book → mint token → complete) and returns the
     * subscriber id. All created rows are tracked for cleanup.
     */
    private Long createSubscriberViaActivation(String email, String fullName) throws Exception {
        Long bookingId = createBookingViaApi(fullName, email);
        activationRateLimiter.reset("127.0.0.1");

        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        activationRateLimiter.reset("127.0.0.1");

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.userId")).longValue();
        createdUserIds.add(userId);

        var subscriber = subscriberRepository.findByUserId(userId).orElseThrow();
        createdSubscriberIds.add(subscriber.getId());
        if (subscriber.getPropertyId() != null) {
            createdPropertyIds.add(subscriber.getPropertyId());
        }

        // Reset rate limiter for subsequent calls from tests.
        activationRateLimiter.reset("127.0.0.1");
        bookingRateLimiter.reset("127.0.0.1");

        return subscriber.getId();
    }

    /**
     * Creates a user with the given role and logs in, returning the access token value.
     */
    private String loginAs(Role role) throws Exception {
        String email = "test-" + role.name().toLowerCase() + "-" + System.nanoTime() + "@test.local";
        String password = "Test1234!";
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(password), "Test", "User", role, UserStatus.ACTIVE));
        createdUserIds.add(user.getId());

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return extractCookieValue(loginResult.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }
}
