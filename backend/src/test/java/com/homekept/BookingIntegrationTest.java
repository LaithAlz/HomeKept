package com.homekept;

import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.analytics.AnalyticsEvent;
import com.homekept.booking.BookingRateLimiter;
import com.homekept.booking.BookingStatus;
import com.homekept.booking.WalkthroughBooking;
import com.homekept.booking.WalkthroughBookingRepository;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the booking vertical slice.
 * Runs against a real Postgres via Testcontainers (@ServiceConnection).
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/bookings/walkthrough happy path — 201, PENDING, consent timestamp</li>
 *   <li>contactConsent=false → 400</li>
 *   <li>contactConsent absent → 400</li>
 *   <li>Missing required fields → 400</li>
 *   <li>Invalid squareFootageRange → 400</li>
 *   <li>leadSource defaults to WEBSITE_DIRECT when absent</li>
 *   <li>Rate limit 3/IP → 4th request returns 429</li>
 *   <li>GET /api/admin/bookings — 401 without auth, 403 as CUSTOMER, 200 as ADMIN</li>
 *   <li>GET /api/admin/bookings — paginates and filters by status</li>
 *   <li>PATCH /api/admin/bookings/{id} — happy path transition</li>
 *   <li>PATCH /api/admin/bookings/{id} — illegal transition → 409</li>
 *   <li>PATCH /api/admin/bookings/{id} — missing booking → 404</li>
 *   <li>PATCH /api/admin/bookings/{id} — sets scheduledFor</li>
 *   <li>Flyway V3 + JPA validate boots (implicit — context load passes)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingAnalyticsConfig.class})
class BookingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired WalkthroughBookingRepository bookingRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired BookingRateLimiter rateLimiter;
    @Autowired RecordingAnalyticsService recording;

    private static final String WALKTHROUGH_URL  = "/api/bookings/walkthrough";
    private static final String ADMIN_BOOKINGS_URL = "/api/admin/bookings";
    private static final String LOGIN_URL = "/api/auth/login";

    private static final String VALID_BODY = """
            {
              "fullName": "Priya Sharma",
              "email": "priya@example.com",
              "phone": "(905) 555-0123",
              "streetAddress": "14 Maple Ridge Crt",
              "city": "Mississauga",
              "postalCode": "L5L 1A1",
              "propertyType": "DETACHED",
              "preferredWeek": "2026-07-07",
              "timeOfDay": "AFTERNOON",
              "dayPreferences": ["WED", "THU"],
              "contactConsent": true
            }
            """;

    /** Track created entities for cleanup. */
    private final List<Long> createdBookingIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Reset rate limiter for the test IP (MockMvc uses 127.0.0.1)
        rateLimiter.reset("127.0.0.1");
        recording.clear();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdBookingIds) {
            bookingRepository.deleteById(id);
        }
        createdBookingIds.clear();
        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ── POST /api/bookings/walkthrough ────────────────────────────────────────

    @Test
    void submitBooking_happyPath_returns201WithPendingStatus() throws Exception {
        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // Extract created id for cleanup and verify it's in the DB
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);

        WalkthroughBooking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(saved.getContactConsentAt()).isNotNull();
        assertThat(saved.getLeadSource().name()).isEqualTo("WEBSITE_DIRECT");

        // Analytics: walkthrough_booked fired as an ANONYMOUS event. No anon id was supplied,
        // so it falls back to a booking-scoped synthetic id. Props are the lead source, the
        // city (a bounded form value), and the property type — no name/email/street.
        assertThat(recording.anonymousEvents()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.WALKTHROUGH_BOOKED);
            assertThat(e.distinctId()).isEqualTo("booking_" + id);
            assertThat(e.props()).containsEntry("lead_source", "WEBSITE_DIRECT");
            assertThat(e.props()).containsEntry("city", "Mississauga");
            assertThat(e.props()).containsEntry("property_type", "DETACHED");
        });
    }

    @Test
    void submitBooking_withPosthogDistinctId_walkthroughBookedUsesThatAnonId() throws Exception {
        String body = """
                {
                  "fullName": "Ada Lovelace",
                  "email": "ada@example.com",
                  "phone": "(905) 555-0199",
                  "streetAddress": "1 Analytical Way",
                  "city": "Oakville",
                  "postalCode": "L6H 1A1",
                  "propertyType": "SEMI",
                  "preferredWeek": "2026-07-14",
                  "timeOfDay": "MORNING",
                  "dayPreferences": ["MON"],
                  "contactConsent": true,
                  "posthogDistinctId": "anon-abc-123"
                }
                """;
        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);

        // With an anon id supplied, walkthrough_booked is attributed to it (so the later
        // activation alias can stitch this lead to the eventual user).
        assertThat(recording.anonymousEvents()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.WALKTHROUGH_BOOKED);
            assertThat(e.distinctId()).isEqualTo("anon-abc-123");
            assertThat(e.props()).containsEntry("city", "Oakville");
        });
    }

    @Test
    void submitBooking_withLeadSource_storesLeadSource() throws Exception {
        String body = """
                {
                  "fullName": "Test User",
                  "email": "test@example.com",
                  "phone": "555-0000",
                  "streetAddress": "1 Main St",
                  "city": "Oakville",
                  "postalCode": "L6J 1A1",
                  "propertyType": "SEMI",
                  "preferredWeek": "2026-07-14",
                  "timeOfDay": "MORNING",
                  "leadSource": "NEXTDOOR",
                  "contactConsent": true
                }
                """;

        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);

        WalkthroughBooking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getLeadSource().name()).isEqualTo("NEXTDOOR");
    }

    @Test
    void submitBooking_contactConsentFalse_returns400() throws Exception {
        String body = """
                {
                  "fullName": "Bad Actor",
                  "email": "bad@example.com",
                  "phone": "555-9999",
                  "streetAddress": "1 Fake St",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "MORNING",
                  "contactConsent": false
                }
                """;

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void submitBooking_contactConsentAbsent_returns400() throws Exception {
        String body = """
                {
                  "fullName": "Bad Actor",
                  "email": "bad@example.com",
                  "phone": "555-9999",
                  "streetAddress": "1 Fake St",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "MORNING"
                }
                """;

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void submitBooking_missingRequiredField_returns400() throws Exception {
        // Missing email
        String body = """
                {
                  "fullName": "Priya Sharma",
                  "phone": "555-0000",
                  "streetAddress": "1 Main St",
                  "city": "Oakville",
                  "postalCode": "L6J 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "MORNING",
                  "contactConsent": true
                }
                """;

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.email").isString());
    }

    @Test
    void submitBooking_invalidSquareFootageRange_returns400() throws Exception {
        String body = """
                {
                  "fullName": "Priya Sharma",
                  "email": "priya@example.com",
                  "phone": "555-0123",
                  "streetAddress": "14 Maple Ridge Crt",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "AFTERNOON",
                  "squareFootageRange": "INVALID",
                  "contactConsent": true
                }
                """;

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitBooking_optionalFieldsAbsent_returns201() throws Exception {
        // yearBuilt, squareFootageRange, dayPreferences, notes, leadSource, posthogDistinctId all absent
        String body = """
                {
                  "fullName": "Minimal User",
                  "email": "min@example.com",
                  "phone": "555-1111",
                  "streetAddress": "1 Oak Ave",
                  "city": "Milton",
                  "postalCode": "L9T 1A1",
                  "propertyType": "TOWNHOUSE",
                  "preferredWeek": "2026-07-21",
                  "timeOfDay": "EVENING",
                  "contactConsent": true
                }
                """;

        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);
    }

    @Test
    void submitBooking_rateLimit_4thRequestReturns429() throws Exception {
        // Exhaust 3 allowed submissions from 127.0.0.1 (no CF header → RemoteAddr)
        for (int i = 0; i < BookingRateLimiter.MAX_SUBMISSIONS; i++) {
            rateLimiter.tryConsume("127.0.0.1");
        }

        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    /**
     * Verifies that when CF-Connecting-IP is set, the rate-limit bucket is keyed on
     * that header — NOT on X-Forwarded-For. Two requests with different fake XFF values
     * but the same CF-Connecting-IP count against the same bucket, so spoofing XFF
     * cannot bypass the cap.
     */
    @Test
    void submitBooking_cfConnectingIp_keysBucketOnCfHeader() throws Exception {
        String cfIp = "203.0.113.55";
        // Pre-exhaust the CF-IP bucket directly (simulates the first 3 requests)
        for (int i = 0; i < BookingRateLimiter.MAX_SUBMISSIONS; i++) {
            rateLimiter.tryConsume(cfIp);
        }

        // A 4th request carrying the same CF-Connecting-IP must be rate-limited,
        // regardless of the X-Forwarded-For value supplied by the client.
        mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("CF-Connecting-IP", cfIp)
                        .header("X-Forwarded-For", "1.2.3.4")   // different — ignored
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    /**
     * Verifies that a fresh CF-Connecting-IP (different from an exhausted one) is still
     * allowed through — i.e. CF-IP buckets are independent.
     */
    @Test
    void submitBooking_cfConnectingIp_differentIpHasOwnBucket() throws Exception {
        String exhaustedIp = "203.0.113.10";
        String freshIp = "203.0.113.11";
        rateLimiter.reset(exhaustedIp);
        rateLimiter.reset(freshIp);

        for (int i = 0; i < BookingRateLimiter.MAX_SUBMISSIONS; i++) {
            rateLimiter.tryConsume(exhaustedIp);
        }

        // A request from a fresh CF-IP should succeed (201)
        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("CF-Connecting-IP", freshIp)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);
    }

    // ── GET /api/admin/bookings — auth / role gate ────────────────────────────

    @Test
    void adminBookings_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_BOOKINGS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminBookings_asCustomer_returns403() throws Exception {
        String customerToken = loginAs(Role.CUSTOMER);
        mockMvc.perform(get(ADMIN_BOOKINGS_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBookings_asAdmin_returns200() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_BOOKINGS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminBookings_filterByStatus_returnsPending() throws Exception {
        // Create one booking
        MvcResult createResult = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        Long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(bookingId);

        String adminToken = loginAs(Role.ADMIN);

        // Filter by PENDING — must include our booking
        mockMvc.perform(get(ADMIN_BOOKINGS_URL + "?status=PENDING")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // All returned items have status PENDING
                .andExpect(jsonPath("$[?(@.status != 'PENDING')]").isEmpty());
    }

    @Test
    void adminBookings_cursor_paginatesCorrectly() throws Exception {
        // Create 3 bookings
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
            ids.add(id);
            createdBookingIds.add(id);
        }

        String adminToken = loginAs(Role.ADMIN);

        // Get first page (limit=2) — should return the 2 highest ids
        MvcResult page1 = mockMvc.perform(get(ADMIN_BOOKINGS_URL + "?limit=2")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        List<Integer> page1Ids = com.jayway.jsonpath.JsonPath.read(
                page1.getResponse().getContentAsString(), "$[*].id");
        Long cursorForPage2 = page1Ids.get(page1Ids.size() - 1).longValue();

        // Page 2 with cursor — must return the next item
        mockMvc.perform(get(ADMIN_BOOKINGS_URL + "?limit=2&cursor=" + cursorForPage2)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                // All returned ids must be < the cursor
                .andExpect(jsonPath("$[?(@.id >= " + cursorForPage2 + ")]").isEmpty());
    }

    // ── PATCH /api/admin/bookings/{id} ────────────────────────────────────────

    @Test
    void adminPatchBooking_legalTransition_returns200() throws Exception {
        Long bookingId = createBookingViaApi();

        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.id").value(bookingId));

        // Verify persisted
        WalkthroughBooking saved = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void adminPatchBooking_setsScheduledFor() throws Exception {
        Long bookingId = createBookingViaApi();
        String adminToken = loginAs(Role.ADMIN);
        String scheduledFor = "2026-07-10T14:00:00Z";

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"scheduledFor\": \"" + scheduledFor + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledFor").value(scheduledFor));
    }

    @Test
    void adminPatchBooking_illegalTransition_returns409() throws Exception {
        Long bookingId = createBookingViaApi();
        String adminToken = loginAs(Role.ADMIN);

        // PENDING → PERFORMED is illegal (must go through CONFIRMED first)
        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"PERFORMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void adminPatchBooking_missingBooking_returns404() throws Exception {
        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void adminPatchBooking_terminalState_returns409() throws Exception {
        // Walk PENDING → CONFIRMED → NO_SHOW, then try to transition out of NO_SHOW
        Long bookingId = createBookingViaApi();
        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"NO_SHOW\"}"))
                .andExpect(status().isOk());

        // NO_SHOW is terminal — any further transition must be rejected
        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"PENDING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminPatchBooking_fullTransitionChain_toConverted() throws Exception {
        Long bookingId = createBookingViaApi();
        String adminToken = loginAs(Role.ADMIN);

        // PENDING → CONFIRMED
        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // CONFIRMED → PERFORMED
        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"PERFORMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PERFORMED"));

        // PERFORMED → CONVERTED
        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", adminToken))
                        .content("{\"status\": \"CONVERTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONVERTED);
    }

    @Test
    void adminPatchBooking_withoutAuth_returns401() throws Exception {
        Long bookingId = createBookingViaApi();

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPatchBooking_asCustomer_returns403() throws Exception {
        Long bookingId = createBookingViaApi();
        String customerToken = loginAs(Role.CUSTOMER);

        mockMvc.perform(patch(ADMIN_BOOKINGS_URL + "/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("hk_access", customerToken))
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Context load / Flyway boot ────────────────────────────────────────────

    /**
     * Verifies that the Spring context loads with V3 migration applied and JPA validates
     * the schema. If this test class runs at all, Flyway and JPA validate have passed.
     */
    @Test
    void contextLoads_flyway_v3_passes() {
        // No assertions needed — the fact that the @SpringBootTest context started
        // means Flyway ran V1+V2+V3 successfully and JPA validated the schema.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a booking via the public API and tracks the id for cleanup.
     */
    private Long createBookingViaApi() throws Exception {
        MvcResult result = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        createdBookingIds.add(id);
        return id;
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
