package com.homekept.visit;

import com.homekept.FakeStorageServiceConfig;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the technician photo upload flow.
 *
 * <p>Imports {@link FakeStorageServiceConfig} so that presign-upload returns a canned URL
 * without real R2 credentials. Tests the HTTP layer only — the photo row is persisted in
 * the real Testcontainers Postgres.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST .../photos/upload-url with image/jpeg → 200 with uploadUrl and storageKey.</li>
 *   <li>POST .../photos/upload-url with non-image contentType → 400.</li>
 *   <li>POST .../photos/upload-url for a visit not assigned to this tech → 404.</li>
 *   <li>POST .../photos with a valid storageKey under visits/{id}/ → 201 + visit_photo row.</li>
 *   <li>POST .../photos with a storageKey NOT under this visit's prefix → 400.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeStorageServiceConfig.class})
class TechPhotoIntegrationTest {

    private static final String LOGIN_URL       = "/api/auth/login";
    private static final String UPLOAD_URL_PATH = "/api/tech/visits/{id}/photos/upload-url";
    private static final String CONFIRM_URL     = "/api/tech/visits/{id}/photos";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
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
    private Visit visit;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        techUser = userRepository.save(new User(
                "photo-tech-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Photo", "Tech",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(techUser.getId());

        TechnicianProfile profile = techProfileRepository.save(
                new TechnicianProfile(techUser.getId(), "ACTIVE", null, 4500));
        createdTechProfileIds.add(profile.getId());

        User customerUser = userRepository.save(new User(
                "photo-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Photo", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());

        property = propertyRepository.save(new Property(
                nano + " Photo Ave", null, "Mississauga", "L5L 3C3",
                "L5L", null, null, PropertyType.SEMI));
        createdPropertyIds.add(property.getId());

        subscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        ZoneId toronto = ZoneId.of("America/Toronto");
        ZonedDateTime todayNoon = java.time.LocalDate.now(toronto).atTime(12, 0).atZone(toronto);
        visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                todayNoon.toInstant(), 120, VisitType.ROUTINE));
        visit.setTechnicianId(techUser.getId());
        visit = visitRepository.save(visit);

        techToken = loginAsUser(techUser.getEmail(), "Tech1234!");
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            // visit_photo cascades from visit (ON DELETE CASCADE) — deleting visit is enough.
            jdbc.update("DELETE FROM visit_service WHERE visit_id IN (SELECT id FROM visit WHERE subscriber_id = ?)", subId);
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

    // ── POST .../photos/upload-url ────────────────────────────────────────────

    @Test
    void uploadUrl_imageJpeg_returns200WithUploadUrlAndStorageKey() throws Exception {
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(FakeStorageServiceConfig.FAKE_UPLOAD_URL))
                .andExpect(jsonPath("$.storageKey").value(
                        org.hamcrest.Matchers.startsWith("visits/" + visit.getId() + "/")));
    }

    @Test
    void uploadUrl_nonImageContentType_returns400() throws Exception {
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void uploadUrl_visitNotAssignedToThisTech_returns404() throws Exception {
        // Create a second visit with no tech assigned.
        Visit unassigned = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                Instant.now().plusSeconds(3600), 120, VisitType.ROUTINE));
        // technicianId = null

        mockMvc.perform(post(UPLOAD_URL_PATH, unassigned.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isNotFound());
    }

    // ── POST .../photos (confirm) ─────────────────────────────────────────────

    @Test
    void confirmPhoto_validStorageKey_creates201AndVisitPhotoRow() throws Exception {
        // First get a valid storage key from the upload-url endpoint.
        MvcResult uploadResult = mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String storageKey = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.storageKey");

        String body = "{\"storageKey\":\"" + storageKey + "\",\"caption\":\"North wall crack\"}";

        MvcResult result = mockMvc.perform(post(CONFIRM_URL, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitId").value(visit.getId()))
                .andExpect(jsonPath("$.storageKey").value(storageKey))
                .andExpect(jsonPath("$.caption").value("North wall crack"))
                .andReturn();

        Long photoId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        VisitPhoto persisted = visitPhotoRepository.findById(photoId).orElseThrow();
        assertThat(persisted.getVisitId()).isEqualTo(visit.getId());
        assertThat(persisted.getStorageKey()).isEqualTo(storageKey);
        assertThat(persisted.getCaption()).isEqualTo("North wall crack");
    }

    @Test
    void confirmPhoto_storageKeyNotUnderThisVisitPrefix_returns400() throws Exception {
        // Attempt to attach a storage key from a different visit prefix.
        String wrongKey = "visits/99999/some-other-uuid";

        mockMvc.perform(post(CONFIRM_URL, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageKey\":\"" + wrongKey + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
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
