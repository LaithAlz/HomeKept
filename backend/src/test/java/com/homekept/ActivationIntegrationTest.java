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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the activation flow.
 * Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/activation/validate — valid token returns 200 {valid:true, bookingId, firstName}</li>
 *   <li>POST /api/activation/validate — garbage token returns {valid:false, reason:"INVALID"}</li>
 *   <li>POST /api/activation/complete — happy path: 201 + auth cookies; DB assertions</li>
 *   <li>POST /api/activation/complete — re-use consumed token → 400 INVALID_TOKEN</li>
 *   <li>POST /api/activation/complete — password too short → 400 INVALID_REQUEST</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActivationIntegrationTest {

    private static final String WALKTHROUGH_URL  = "/api/bookings/walkthrough";
    private static final String VALIDATE_URL     = "/api/activation/validate";
    private static final String COMPLETE_URL     = "/api/activation/complete";
    private static final String LOGIN_URL        = "/api/auth/login";

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
        // Break the circular FKs first: a booking references its subscriber
        // (converted_to_subscriber_id) and its token (activation_token_id), while the token
        // references the booking (booking_id). Null the booking's outbound refs so the rows
        // can then be deleted in dependency order without a constraint violation.
        for (Long id : createdBookingIds) {
            bookingRepository.findById(id).ifPresent(b -> {
                b.setConvertedToSubscriberId(null);
                b.setActivationTokenId(null);
                bookingRepository.save(b);
            });
        }

        // tokens (token.booking_id → booking, still present, OK) → subscribers
        // (property.subscriber_id is ON DELETE SET NULL) → bookings (now unreferenced)
        // → properties (subscriber gone) → users (subscriber gone).
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

    // ── POST /api/activation/validate ────────────────────────────────────────

    @Test
    void validate_validToken_returns200WithValidTrueAndFirstName() throws Exception {
        Long bookingId = createBookingViaApi("Priya Sharma", "priya-validate@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                // firstName is the first word of fullName
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void validate_garbageToken_returns200WithValidFalseAndInvalidReason() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"this.is.garbage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID"));
    }

    @Test
    void validate_consumedToken_returns200WithValidFalseAndUsedReason() throws Exception {
        Long bookingId = createBookingViaApi("Jane Doe", "jane-consumed@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        // Complete activation to consume the token; track created rows.
        MvcResult completeResult = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        trackActivationCreatedRows(completeResult, bookingId);

        // Re-validate the now-consumed token.
        mockMvc.perform(post(VALIDATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("USED"));
    }

    // ── POST /api/activation/complete — happy path ────────────────────────────

    @Test
    void complete_happyPath_returns201WithUserIdAndCheckout_andSetsAuthCookies() throws Exception {
        Long bookingId = createBookingViaApi("Maria Costa", "maria-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.next").value("CHECKOUT"))
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"))
                .andReturn();

        trackActivationCreatedRows(result, bookingId);
    }

    @Test
    void complete_happyPath_persistsUserWithCustomerRoleAndPendingActivationStatus() throws Exception {
        Long bookingId = createBookingViaApi("Chidi Okeke", "chidi-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = trackActivationCreatedRows(result, bookingId);

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.getEmail()).isEqualTo("chidi-complete@test.local");
    }

    @Test
    void complete_happyPath_persistsPropertyLinkedToSubscriber() throws Exception {
        Long bookingId = createBookingViaApi("Tomás García", "tomas-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = trackActivationCreatedRows(result, bookingId);

        // The subscriber exists with PENDING_ACTIVATION status.
        var subscriber = subscriberRepository.findByUserId(userId).orElseThrow();
        assertThat(subscriber.getStatus()).isEqualTo(SubscriberStatus.PENDING_ACTIVATION);

        // The property exists and its subscriber_id is set.
        var property = propertyRepository.findById(subscriber.getPropertyId()).orElseThrow();
        assertThat(property.getSubscriberId()).isEqualTo(subscriber.getId());
        assertThat(property.getStreetAddress()).isEqualTo("14 Maple Ridge Crt");
    }

    @Test
    void complete_happyPath_setsBookingConvertedToSubscriberId() throws Exception {
        Long bookingId = createBookingViaApi("Sam Lee", "sam-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = trackActivationCreatedRows(result, bookingId);

        var subscriber = subscriberRepository.findByUserId(userId).orElseThrow();
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getConvertedToSubscriberId()).isEqualTo(subscriber.getId());
        // Conversion must transition the booking to CONVERTED via the state machine.
        assertThat(booking.getStatus()).isEqualTo(com.homekept.booking.BookingStatus.CONVERTED);
    }

    @Test
    void complete_happyPath_tokenIsConsumedAfterCompletion() throws Exception {
        Long bookingId = createBookingViaApi("Ann White", "ann-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        trackActivationCreatedRows(result, bookingId);

        var token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isNotNull();
    }

    // ── POST /api/activation/complete — error cases ───────────────────────────

    @Test
    void complete_consumedToken_returns400InvalidToken() throws Exception {
        Long bookingId = createBookingViaApi("Bob Brown", "bob-reuse@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        // First completion — valid.
        MvcResult first = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        trackActivationCreatedRows(first, bookingId);

        // Reset rate limiter so the second call isn't rate-limited.
        activationRateLimiter.reset("127.0.0.1");

        // Second completion with the same token — must be rejected.
        mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void complete_passwordTooShort_returns400InvalidRequest() throws Exception {
        Long bookingId = createBookingViaApi("Clara Day", "clara-short@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);
        createdTokenIds.add(mint.tokenId());

        mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void complete_garbageToken_returns400() throws Exception {
        mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage.token\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a walk-through booking via the public API using the standard valid body
     * with the supplied name and email substituted in. Tracks the created booking id.
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

        // The real flow performs the walk-through before the activation invite is sent, so the
        // booking is PERFORMED when /activation/complete converts it (PERFORMED → CONVERTED).
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(com.homekept.booking.BookingStatus.PERFORMED);
        bookingRepository.save(booking);
        return id;
    }

    /**
     * Reads the userId from a successful complete response, locates the corresponding
     * subscriber and property, tracks them for cleanup, and returns the userId.
     *
     * <p>The token row is tracked separately (already done by the caller before the request).
     * The booking row is tracked when created via {@link #createBookingViaApi}.
     */
    private Long trackActivationCreatedRows(MvcResult completeResult, Long bookingId)
            throws java.io.UnsupportedEncodingException {
        Long userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                completeResult.getResponse().getContentAsString(), "$.userId")).longValue();
        createdUserIds.add(userId);

        // Find the subscriber and property for cleanup.
        subscriberRepository.findByUserId(userId).ifPresent(sub -> {
            createdSubscriberIds.add(sub.getId());
            if (sub.getPropertyId() != null) {
                createdPropertyIds.add(sub.getPropertyId());
            }
        });

        return userId;
    }
}
