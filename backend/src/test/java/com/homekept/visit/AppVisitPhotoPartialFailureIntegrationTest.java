package com.homekept.visit;

import com.homekept.AbstractIntegrationTest;
import com.homekept.FlakyStorageServiceConfig;
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
 * Integration test for {@code GET /api/app/visits/{id}} when one photo's signing throws a
 * non-{@code StorageUnavailableException} error (via {@link FlakyStorageServiceConfig}).
 *
 * <p>Asserts the per-photo failure degrades to that one photo being omitted — the rest of
 * the visit detail (including the other, successfully-signed photo) still returns 200,
 * rather than the whole request 500ing.
 */
@Import(FlakyStorageServiceConfig.class)
class AppVisitPhotoPartialFailureIntegrationTest extends AbstractIntegrationTest {

    private static final String DETAIL_URL = "/api/app/visits/{id}";

    @Autowired VisitRepository visitRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;

    private Subscriber customerSubscriber;
    private String customerToken;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        User customerUser = userRepository.save(new User(
                "app-visit-photo-flaky-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Flaky", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Flaky Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @Test
    void getVisit_onePhotoSigningThrowsNonStorageUnavailableError_skipsThatPhotoAndReturns200() throws Exception {
        Visit visit = visitRepository.save(new Visit(
                customerSubscriber.getId(), customerSubscriber.getPropertyId(), null,
                Instant.now().plus(30, ChronoUnit.DAYS), 120, VisitType.ROUTINE));

        // Good photo: signs fine under FlakyStorageServiceConfig.
        visitPhotoRepository.save(new VisitPhoto(
                visit.getId(), "visits/" + visit.getId() + "/good-uuid", "Good photo", Instant.now()));
        // Bad photo: its storage key triggers a simulated RuntimeException (not
        // StorageUnavailableException) from presignDownload.
        visitPhotoRepository.save(new VisitPhoto(
                visit.getId(), "visits/" + visit.getId() + "/" + FlakyStorageServiceConfig.FAILING_KEY_MARKER,
                "Bad photo", Instant.now()));

        mockMvc.perform(get(DETAIL_URL, visit.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visit.getId()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.photos").isArray())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].caption").value("Good photo"))
                .andExpect(jsonPath("$.photos[0].url").value(FlakyStorageServiceConfig.FAKE_DOWNLOAD_URL));
    }

}
