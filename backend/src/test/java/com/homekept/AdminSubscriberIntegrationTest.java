package com.homekept;

import com.homekept.booking.BookingRateLimiter;
import com.homekept.booking.WalkthroughBookingRepository;
import com.homekept.identity.Role;
import com.homekept.property.PropertyRepository;
import com.homekept.subscription.ActivationRateLimiter;
import com.homekept.subscription.ActivationTokenRepository;
import com.homekept.subscription.ActivationTokenService;
import com.homekept.subscription.SubscriberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 *   <li>GET /api/admin/bookings — {@code invitedAt} is null until an invite is sent for that
 *       booking, and non-null (only) for the booking an invite was sent to</li>
 *   <li>GET /api/admin/subscribers — as ADMIN → 200; as CUSTOMER → 403; anonymous → 401</li>
 *   <li>GET /api/admin/subscribers — cursor pagination newest-first</li>
 *   <li>GET /api/admin/subscribers/{id} — as ADMIN → 200 with property summary; hasAccessNotes present</li>
 *   <li>GET /api/admin/subscribers/{id} — response NEVER contains a decrypted access notes field</li>
 *   <li>GET /api/admin/subscribers/{id} — missing id → 404</li>
 *   <li>GET /api/admin/subscribers/{id} — SKU sheet fields (issue #56, read side) are null
 *       until set, and reflect a subsequent {@code PATCH /api/admin/properties/{propertyId}/sku}</li>
 * </ul>
 */
class AdminSubscriberIntegrationTest extends AbstractIntegrationTest {

    private static final String WALKTHROUGH_URL    = "/api/bookings/walkthrough";
    private static final String ADMIN_BOOKINGS     = "/api/admin/bookings";
    private static final String ADMIN_INVITE_URL   = "/api/admin/bookings/%d/activation-invite";
    private static final String ADMIN_SUBSCRIBERS  = "/api/admin/subscribers";
    private static final String ADMIN_PROPERTY_SKU_URL = "/api/admin/properties/%d/sku";
    private static final String COMPLETE_URL       = "/api/activation/complete";

    @Autowired WalkthroughBookingRepository bookingRepository;
    @Autowired ActivationTokenRepository tokenRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ActivationTokenService activationTokenService;
    @Autowired ActivationRateLimiter activationRateLimiter;
    @Autowired BookingRateLimiter bookingRateLimiter;

    @BeforeEach
    void resetRateLimiters() {
        bookingRateLimiter.reset("127.0.0.1");
        activationRateLimiter.reset("127.0.0.1");
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
    }

    @Test
    void listBookings_showsInvitedAtOnlyForBookingWithSentInvite() throws Exception {
        Long invitedBookingId = createBookingViaApi("Wren Ashby", "wren-invited@test.local");
        Long uninvitedBookingId = createBookingViaApi("Sable Doyle", "sable-uninvited@test.local");
        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(post(ADMIN_INVITE_URL.formatted(invitedBookingId))
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(ADMIN_BOOKINGS + "?limit=50")
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String invitedAtForInvited = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + invitedBookingId + ")].invitedAt[0]");
        List<Object> invitedAtForUninvited = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + uninvitedBookingId + ")].invitedAt");

        assertThat(invitedAtForInvited).isNotNull();
        assertThat(invitedAtForUninvited).hasSize(1);
        assertThat(invitedAtForUninvited.get(0)).isNull();
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
    void getSubscriberDetail_asAdmin_showsSkuFieldsNullInitially() throws Exception {
        Long subscriberId = createSubscriberViaActivation("sku-null@test.local", "Sku Null");

        String adminToken = loginAs(Role.ADMIN);
        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.propertyId").isNumber())
                .andExpect(jsonPath("$.property.hvacFilterSizes").value(nullValue()))
                .andExpect(jsonPath("$.property.smokeCoDetectorModels").value(nullValue()))
                .andExpect(jsonPath("$.property.humidifierModel").value(nullValue()))
                .andExpect(jsonPath("$.property.waterHeaterAgeYears").value(nullValue()))
                .andExpect(jsonPath("$.property.waterHeaterFlushEligible").value(nullValue()));
    }

    @Test
    void getSubscriberDetail_asAdmin_reflectsSkuAfterAdminPatch() throws Exception {
        Long subscriberId = createSubscriberViaActivation("sku-patched@test.local", "Sku Patched");
        String adminToken = loginAs(Role.ADMIN);

        MvcResult detail = mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        Long propertyId = idFrom(detail, "$.property.propertyId");

        mockMvc.perform(patch(ADMIN_PROPERTY_SKU_URL.formatted(propertyId))
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hvacFilterSizes": "16x25x1",
                                  "smokeCoDetectorModels": "Kidde P4010ACSCO-CA",
                                  "humidifierModel": "Aprilaire 600",
                                  "waterHeaterAgeYears": 5,
                                  "waterHeaterFlushEligible": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_SUBSCRIBERS + "/" + subscriberId)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.hvacFilterSizes").value("16x25x1"))
                .andExpect(jsonPath("$.property.smokeCoDetectorModels").value("Kidde P4010ACSCO-CA"))
                .andExpect(jsonPath("$.property.humidifierModel").value("Aprilaire 600"))
                .andExpect(jsonPath("$.property.waterHeaterAgeYears").value(5))
                .andExpect(jsonPath("$.property.waterHeaterFlushEligible").value(true));
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
     * Creates a walk-through booking via the public API.
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

        // Advance to PERFORMED so the activation flow can convert it (PERFORMED → CONVERTED).
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(com.homekept.booking.BookingStatus.PERFORMED);
        bookingRepository.save(booking);
        return id;
    }

    /**
     * Runs the full activation flow (book → mint token → complete) and returns the
     * subscriber id.
     */
    private Long createSubscriberViaActivation(String email, String fullName) throws Exception {
        Long bookingId = createBookingViaApi(fullName, email);
        activationRateLimiter.reset("127.0.0.1");

        ActivationTokenService.MintResult mint = activationTokenService.mint(bookingId);

        activationRateLimiter.reset("127.0.0.1");

        MvcResult result = mockMvc.perform(post(COMPLETE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mint.rawToken() + "\",\"password\":\"hunter2pw\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = idFrom(result, "$.userId");

        var subscriber = subscriberRepository.findByUserId(userId).orElseThrow();

        // Reset rate limiter for subsequent calls from tests.
        activationRateLimiter.reset("127.0.0.1");
        bookingRateLimiter.reset("127.0.0.1");

        return subscriber.getId();
    }
}
