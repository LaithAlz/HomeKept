package com.homekept;

import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.FoundingRateAvailabilityImpl;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link FoundingRateAvailabilityImpl} — the live implementation
 * that counts {@code subscriber.founding_rate = true} rows in the DB.
 *
 * <p>Runs against a real Postgres via Testcontainers. Verifies:
 * <ul>
 *   <li>With 0 founding subscribers, {@code GET /api/catalog/plans} COMPLETE shows
 *       {@code foundingRateAvailable:true} (slots open).</li>
 *   <li>After inserting 15 founding subscribers, {@code foundingSlotsRemaining()} returns
 *       {@code false} and {@code GET /api/catalog/plans} COMPLETE shows
 *       {@code foundingRateAvailable:false}.</li>
 * </ul>
 */
class FoundingRateIntegrationTest extends AbstractIntegrationTest {

    private static final String PLANS_URL = "/api/catalog/plans";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired FoundingRateAvailabilityImpl foundingRateAvailability;

    // ── With 0 founding subscribers ───────────────────────────────────────────

    @Test
    void withNoFoundingSubscribers_foundingSlotsRemaining_returnsTrue() {
        assertThat(foundingRateAvailability.foundingSlotsRemaining()).isTrue();
    }

    @Test
    void withNoFoundingSubscribers_plansEndpoint_completeShowsFoundingRateAvailableTrue() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                .andExpect(jsonPath("$[1].foundingRateAvailable").value(true))
                // Price is always returned regardless of slot availability.
                .andExpect(jsonPath("$[1].foundingMonthlyPriceCents").value(12900));
    }

    // ── With 15 founding subscribers (cap exactly reached) ────────────────────

    @Test
    void withCapReached_foundingSlotsRemaining_returnsFalse() {
        insertFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP);

        assertThat(foundingRateAvailability.foundingSlotsRemaining()).isFalse();
    }

    @Test
    void withCapReached_plansEndpoint_completeShowsFoundingRateAvailableFalse() throws Exception {
        insertFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP);

        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                .andExpect(jsonPath("$[1].foundingRateAvailable").value(false))
                // Price stays 12900 — it's displayed as "what you would have paid".
                .andExpect(jsonPath("$[1].foundingMonthlyPriceCents").value(12900));
    }

    @Test
    void withCapReached_essentialRemainsFalse_itHasNoFoundingPrice() throws Exception {
        insertFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP);

        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ESSENTIAL"))
                // ESSENTIAL has no founding price at all — always false regardless of cap.
                .andExpect(jsonPath("$[0].foundingRateAvailable").value(false));
    }

    @Test
    void withBelowCap_14FoundingSubscribers_slotsRemainingIsTrue() {
        insertFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP - 1);

        assertThat(foundingRateAvailability.foundingSlotsRemaining()).isTrue();
    }

    @Test
    void withBelowCap_14FoundingSubscribers_plansEndpointStillShowsAvailableTrue() throws Exception {
        insertFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP - 1);

        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                .andExpect(jsonPath("$[1].foundingRateAvailable").value(true));
    }

    @Test
    void nonFoundingSubscribers_doNotCountTowardCap() {
        // Insert FOUNDING_CAP non-founding subscribers — they must not affect the cap.
        insertNonFoundingSubscribers(FoundingRateAvailabilityImpl.FOUNDING_CAP);

        assertThat(foundingRateAvailability.foundingSlotsRemaining()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Inserts {@code count} founding subscribers (founding_rate=true) directly via the
     * repository. Each subscriber needs a real user row and a real property row to
     * satisfy the NOT NULL FKs in the database.
     */
    private void insertFoundingSubscribers(long count) {
        for (int i = 0; i < count; i++) {
            long stub = System.nanoTime();

            User user = userRepository.save(new User(
                    "founding-stub-" + stub + "@test.local",
                    passwordEncoder.encode("password"),
                    "Founding", "Stub",
                    Role.CUSTOMER, UserStatus.PENDING_ACTIVATION));

            Property property = propertyRepository.save(new Property(
                    stub + " Founding St", null, "Mississauga", "L5L 1A1",
                    "L5L", null, null, PropertyType.DETACHED));

            Subscriber sub = new Subscriber(
                    user.getId(), property.getId(),
                    SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
            sub.setFoundingRate(true);
            sub = subscriberRepository.save(sub);
        }
    }

    /**
     * Inserts {@code count} non-founding subscribers (founding_rate=false). Used to
     * verify these rows are not counted toward the cap.
     */
    private void insertNonFoundingSubscribers(long count) {
        for (int i = 0; i < count; i++) {
            long stub = System.nanoTime();

            User user = userRepository.save(new User(
                    "non-founding-stub-" + stub + "@test.local",
                    passwordEncoder.encode("password"),
                    "NonFounding", "Stub",
                    Role.CUSTOMER, UserStatus.PENDING_ACTIVATION));

            Property property = propertyRepository.save(new Property(
                    stub + " Regular St", null, "Oakville", "L6J 1A1",
                    "L6J", null, null, PropertyType.TOWNHOUSE));

            Subscriber sub = new Subscriber(
                    user.getId(), property.getId(),
                    SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
            // foundingRate defaults to false — do not set it.
            sub = subscriberRepository.save(sub);
        }
    }
}
