package com.homekept.visit;

import com.homekept.AbstractIntegrationTest;
import com.homekept.FakeStorageServiceConfig;
import com.homekept.identity.Role;
import com.homekept.identity.User;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
 *   <li>POST .../photos/upload-url with contentLength over the 25 MB cap → 400.</li>
 *   <li>POST .../photos/upload-url with a missing / zero contentLength → 400.</li>
 *   <li>POST .../photos/upload-url for a visit not assigned to this tech → 404.</li>
 *   <li>POST .../photos with a valid storageKey under visits/{id}/ → 201 + visit_photo row.</li>
 *   <li>POST .../photos with a storageKey NOT under this visit's prefix → 400.</li>
 * </ul>
 */
@Import(FakeStorageServiceConfig.class)
class TechPhotoIntegrationTest extends AbstractIntegrationTest {

    private static final String UPLOAD_URL_PATH = "/api/tech/visits/{id}/photos/upload-url";
    private static final String CONFIRM_URL     = "/api/tech/visits/{id}/photos";

    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
    @Autowired TechnicianProfileRepository techProfileRepository;

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

        techProfileRepository.save(
                new TechnicianProfile(techUser.getId(), "ACTIVE", null, 4500));

        User customerUser = userRepository.save(new User(
                "photo-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Photo", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        property = propertyRepository.save(new Property(
                nano + " Photo Ave", null, "Mississauga", "L5L 3C3",
                "L5L", null, null, PropertyType.SEMI));

        subscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        ZoneId toronto = ZoneId.of("America/Toronto");
        ZonedDateTime todayNoon = java.time.LocalDate.now(toronto).atTime(12, 0).atZone(toronto);
        visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                todayNoon.toInstant(), 120, VisitType.ROUTINE));
        visit.setTechnicianId(techUser.getId());
        visit = visitRepository.save(visit);

        techToken = loginAs(techUser.getEmail(), "Tech1234!");
    }

    // ── POST .../photos/upload-url ────────────────────────────────────────────

    @Test
    void uploadUrl_imageJpeg_returns200WithUploadUrlAndStorageKey() throws Exception {
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1048576}"))
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
                        .content("{\"contentType\":\"application/pdf\",\"contentLength\":1048576}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void uploadUrl_contentLengthOverCap_returns400() throws Exception {
        // 25 MB cap is 26_214_400 bytes; one byte over must be rejected before R2 is touched.
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":26214401}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadUrl_missingContentLength_returns400() throws Exception {
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadUrl_zeroContentLength_returns400() throws Exception {
        mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":0}"))
                .andExpect(status().isBadRequest());
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
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1048576}"))
                .andExpect(status().isNotFound());
    }

    // ── POST .../photos (confirm) ─────────────────────────────────────────────

    @Test
    void confirmPhoto_validStorageKey_creates201AndVisitPhotoRow() throws Exception {
        // First get a valid storage key from the upload-url endpoint.
        MvcResult uploadResult = mockMvc.perform(post(UPLOAD_URL_PATH, visit.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1048576}"))
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

        Long photoId = idFrom(result);

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

}
