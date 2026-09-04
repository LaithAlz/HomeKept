package com.homekept;

import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.analytics.AnalyticsEvent;
import com.homekept.booking.BookingRateLimiter;
import com.homekept.booking.WalkthroughBookingRepository;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import com.homekept.property.PropertyRepository;
import com.homekept.subscription.ActivationRateLimiter;
import com.homekept.subscription.ActivationTokenRepository;
import com.homekept.subscription.ActivationTokenService;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
class ActivationIntegrationTest extends AbstractIntegrationTest {

    private static final String WALKTHROUGH_URL  = "/api/bookings/walkthrough";
    private static final String VALIDATE_URL     = "/api/activation/validate";
    private static final String COMPLETE_URL     = "/api/activation/complete";

    @Autowired WalkthroughBookingRepository bookingRepository;
    @Autowired ActivationTokenRepository tokenRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ActivationTokenService activationTokenService;
    @Autowired ActivationRateLimiter activationRateLimiter;
    @Autowired BookingRateLimiter bookingRateLimiter;
    @Autowired RecordingAnalyticsService recording;

    @BeforeEach
    void resetRateLimiters() {
        bookingRateLimiter.reset("127.0.0.1");
        activationRateLimiter.reset("127.0.0.1");
        recording.clear();
    }

    // ── POST /api/activation/validate ────────────────────────────────────────

    @Test
    void validate_validToken_returns200WithValidTrueAndFirstName() throws Exception {
        Long bookingId = createBookingViaApi("Priya Sharma", "priya-validate@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

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

        // Complete activation to consume the token; track created rows.
        MvcResult completeResult = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();


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

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.next").value("CHECKOUT"))
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"))
                .andReturn();

    }

    @Test
    void complete_emitsActivationCompletedAndAliasesTheAnonymousLead() throws Exception {
        // The booking carries the wizard's anonymous distinct id (walkthrough_booked was
        // captured against it). Activation must emit activation_completed for the new user AND
        // alias the anonymous id into that user, so the acquisition funnel stitches.
        String body = """
                {
                  "fullName": "Grace Hopper",
                  "email": "grace-funnel@test.local",
                  "phone": "(905) 555-0123",
                  "streetAddress": "14 Maple Ridge Crt",
                  "city": "Mississauga",
                  "postalCode": "L5L 1A1",
                  "propertyType": "DETACHED",
                  "preferredWeek": "2026-07-07",
                  "timeOfDay": "AFTERNOON",
                  "contactConsent": true,
                  "posthogDistinctId": "anon-funnel-9"
                }
                """;
        MvcResult bookingResult = mockMvc.perform(post(WALKTHROUGH_URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Long bookingId = idFrom(bookingResult);
        // Real flow: the walk-through is performed before the invite is sent.
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(com.homekept.booking.BookingStatus.PERFORMED);
        bookingRepository.save(booking);

        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated()).andReturn();
        Long userId = idFrom(result, "$.userId");

        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.ACTIVATION_COMPLETED);
            assertThat(e.distinctId()).isEqualTo(userId);
            assertThat(e.props()).containsKey("days_since_walkthrough");
        });
        assertThat(recording.aliases()).anySatisfy(a -> {
            assertThat(a.anonymousDistinctId()).isEqualTo("anon-funnel-9");
            assertThat(a.userId()).isEqualTo(userId);
        });
    }

    @Test
    void complete_happyPath_persistsUserWithCustomerRoleAndActiveStatus() throws Exception {
        // The User row only exists once a password has been set, so it must be created
        // ACTIVE (able to authenticate) rather than PENDING_ACTIVATION — regression test
        // for the login-lockout bug: a User stuck in PENDING_ACTIVATION can never log in
        // again once its activation-issued cookies expire (see AuthService.login).
        Long bookingId = createBookingViaApi("Chidi Okeke", "chidi-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = idFrom(result, "$.userId");

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("chidi-complete@test.local");
    }

    @Test
    void complete_thenLogInWithThatPassword_returns200() throws Exception {
        // The actual regression test: a customer who activates must be able to log back in
        // with the same password once their activation-issued session lapses. Before the
        // fix, ActivationService.complete created the User as PENDING_ACTIVATION, which
        // AuthService.login unconditionally rejects — this call would have returned 401
        // "Invalid email or password" even with the correct credentials.
        Long bookingId = createBookingViaApi("Login Regression", "login-regression@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login-regression@test.local\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("hk_access"))
                .andExpect(cookie().exists("hk_refresh"));
    }

    @Test
    void complete_happyPath_persistsPropertyLinkedToSubscriber() throws Exception {
        Long bookingId = createBookingViaApi("Tomás García", "tomas-complete@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = idFrom(result, "$.userId");

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

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = idFrom(result, "$.userId");

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

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();


        var token = tokenRepository.findById(mint.tokenId()).orElseThrow();
        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isNotNull();
    }

    // ── POST /api/activation/complete — error cases ───────────────────────────

    @Test
    void complete_consumedToken_returns400InvalidToken() throws Exception {
        Long bookingId = createBookingViaApi("Bob Brown", "bob-reuse@test.local");
        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        // First completion — valid.
        MvcResult first = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();


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
     * with the supplied name and email substituted in.
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

        Long id = idFrom(result);

        // The real flow performs the walk-through before the activation invite is sent, so the
        // booking is PERFORMED when /activation/complete converts it (PERFORMED → CONVERTED).
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(com.homekept.booking.BookingStatus.PERFORMED);
        bookingRepository.save(booking);
        return id;
    }
}
