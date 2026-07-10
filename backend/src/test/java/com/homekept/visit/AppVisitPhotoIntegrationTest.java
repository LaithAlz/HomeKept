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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeStorageServiceConfig.class})
class AppVisitPhotoIntegrationTest {

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
        createdUserIds.add(customerUser.getId());

        Property customerProp = propertyRepository.save(new Property(
                nano + " Photo Customer Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(customerProp.getId());

        customerSubscriber = subscriberRepository.save(new Subscriber(
                customerUser.getId(), customerProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(customerSubscriber.getId());

        User otherUser = userRepository.save(new User(
                "app-visit-photo-other-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Other", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(otherUser.getId());

        Property otherProp = propertyRepository.save(new Property(
                nano + " Photo Other Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(otherProp.getId());

        otherSubscriber = subscriberRepository.save(new Subscriber(
                otherUser.getId(), otherProp.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(otherSubscriber.getId());

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
