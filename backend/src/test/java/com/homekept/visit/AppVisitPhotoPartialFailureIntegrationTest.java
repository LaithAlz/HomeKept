package com.homekept.visit;

import com.homekept.FlakyStorageServiceConfig;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FlakyStorageServiceConfig.class})
class AppVisitPhotoPartialFailureIntegrationTest {

    private static final String DETAIL_URL = "/api/app/visits/{id}";
    private static final String LOGIN_URL  = "/api/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitPhotoRepository visitPhotoRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds       = new ArrayList<>();
    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();

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
        createdUserIds.add(customerUser.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Flaky Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(customerSubscriber.getId());

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @AfterEach
    void tearDown() {
        // visit_photo cascades from visit (ON DELETE CASCADE) — deleting visit is enough.
        for (Long subId : createdSubscriberIds) {
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

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
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

    private String loginAs(String email, String password) throws Exception {
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
