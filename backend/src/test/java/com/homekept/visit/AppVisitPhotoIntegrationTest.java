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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/app/visits/{id}} photo mapping with R2 faked
 * (via {@link FakeStorageServiceConfig}), complementing {@link AppVisitIntegrationTest}
 * which covers the R2-unconfigured (empty {@code photos[]}) case.
 *
 * <p>Covers:
 * <ul>
 *   <li>A visit with photo rows returns a {@code photos[]} array shaped
 *       {@code { url, caption, takenAt }}, ordered oldest-first by id.</li>
 *   <li>Another subscriber's visit id → 404 (ownership; ownership is checked before any
 *       photo is loaded, so photos never leak across subscribers).</li>
 * </ul>
 */
@Import(FakeStorageServiceConfig.class)
class AppVisitPhotoIntegrationTest extends AbstractIntegrationTest {

    private static final String DETAIL_URL = "/api/app/visits/{id}";

    @Autowired VisitRepository visitRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;

    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerToken;

    private Subscriber otherSubscriber;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        customerUser = userRepository.save(new User(
                "app-visit-photo-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Photo", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property customerProp = propertyRepository.save(new Property(
                nano + " Photo Customer Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), customerProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        User otherUser = userRepository.save(new User(
                "app-visit-photo-other-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Other", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property otherProp = propertyRepository.save(new Property(
                nano + " Photo Other Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        otherSubscriber = subscriberRepository.save(new Subscriber(
                otherUser.getId(), otherProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @Test
    void getVisit_withPhotoRows_returnsPhotosArrayInShapeAndOrder() throws Exception {
        Visit visit = visitRepository.save(new Visit(
                customerSubscriber.getId(), customerSubscriber.getPropertyId(), null,
                Instant.now().plus(30, ChronoUnit.DAYS), 120, VisitType.ROUTINE));

        Instant takenAt1 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant takenAt2 = Instant.now().minus(1, ChronoUnit.DAYS);
        VisitPhoto first = visitPhotoRepository.save(new VisitPhoto(
                visit.getId(), "visits/" + visit.getId() + "/uuid-1", "North wall crack", takenAt1));
        VisitPhoto second = visitPhotoRepository.save(new VisitPhoto(
                visit.getId(), "visits/" + visit.getId() + "/uuid-2", null, takenAt2));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos").isArray())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].url").value(FakeStorageServiceConfig.FAKE_DOWNLOAD_URL))
                .andExpect(jsonPath("$.photos[0].caption").value("North wall crack"))
                .andExpect(jsonPath("$.photos[0].takenAt").exists())
                .andExpect(jsonPath("$.photos[1].url").value(FakeStorageServiceConfig.FAKE_DOWNLOAD_URL))
                .andExpect(jsonPath("$.photos[1].caption").doesNotExist());

        // Sanity: rows were persisted with distinct storage keys under this visit's prefix.
        org.assertj.core.api.Assertions.assertThat(first.getStorageKey())
                .isNotEqualTo(second.getStorageKey());
    }

    @Test
    void getVisit_otherSubscribersVisitWithPhotos_returns404() throws Exception {
        Visit otherVisit = visitRepository.save(new Visit(
                otherSubscriber.getId(), otherSubscriber.getPropertyId(), null,
                Instant.now().plus(30, ChronoUnit.DAYS), 120, VisitType.ROUTINE));
        visitPhotoRepository.save(new VisitPhoto(
                otherVisit.getId(), "visits/" + otherVisit.getId() + "/uuid", "Not yours", Instant.now()));

        mockMvc.perform(get(DETAIL_URL, otherVisit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isNotFound());
    }

}
