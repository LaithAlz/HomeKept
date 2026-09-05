package com.homekept.subscription;

import com.homekept.AbstractIntegrationTest;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SubscriptionController#updateAccount}:
 * {@code PATCH /api/app/account}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Updating one field leaves the others unchanged.</li>
 *   <li>A blank name and an over-length phone are rejected with 400.</li>
 *   <li>Email and the service address are never mutated by this endpoint.</li>
 *   <li>A customer can only ever touch their own record (there is no target-user field).</li>
 *   <li>No subscriber row → 404; anonymous → 401.</li>
 * </ul>
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
class AppAccountUpdateIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNT_URL = "/api/app/account";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;

    private User customerUser;
    private String customerToken;

    @BeforeEach
    void seedCustomer() throws Exception {
        long nano = System.nanoTime();

        customerUser = userRepository.save(new User(
                "app-account-update-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Priya", "Sharma",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Maple Ridge Crt", "Unit 4", "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        Subscriber subscriber = new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        subscriberRepository.save(subscriber);

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    @Test
    void updateAccount_firstNameOnly_updatesOnlyThatField() throws Exception {
        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Priyanka\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priyanka"))
                .andExpect(jsonPath("$.lastName").value("Sharma"))
                .andExpect(jsonPath("$.email").value(customerUser.getEmail()));

        User reloaded = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Priyanka");
        assertThat(reloaded.getLastName()).isEqualTo("Sharma");
    }

    @Test
    void updateAccount_phoneOnly_updatesOnlyThatField_andLeavesNamesUnchanged() throws Exception {
        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9055550123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.lastName").value("Sharma"));

        User reloaded = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(reloaded.getPhone()).isEqualTo("9055550123");
        assertThat(reloaded.getFirstName()).isEqualTo("Priya");
    }

    @Test
    void updateAccount_neverChangesEmailOrServiceAddress() throws Exception {
        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Priyanka\"}"))
                .andExpect(status().isOk());

        // AppAccountUpdateRequest has no email/address fields at all — GET must still show
        // the original values after a PATCH that only touched firstName.
        mockMvc.perform(get(ACCOUNT_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customerUser.getEmail()))
                .andExpect(jsonPath("$.streetAddress").exists())
                .andExpect(jsonPath("$.city").value("Mississauga"));
    }

    @Test
    void updateAccount_blankFirstName_returns400_andDoesNotPersist() throws Exception {
        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"   \"}"))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Priya");
    }

    @Test
    void updateAccount_overLengthPhone_returns400_andDoesNotPersist() throws Exception {
        String tooLong = "1".repeat(31); // column + validation cap is 30

        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(reloaded.getPhone()).isNull();
    }

    @Test
    void updateAccount_cannotTouchAnotherUsersRecord() throws Exception {
        long nano = System.nanoTime();
        User otherUser = userRepository.save(new User(
                "app-account-other-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Other", "Person",
                Role.CUSTOMER, UserStatus.ACTIVE));
        Property otherProperty = propertyRepository.save(new Property(
                nano + " Other Ave", null, "Brampton", "L6Y 0A1",
                "L6Y", null, null, PropertyType.DETACHED));
        subscriberRepository.save(new Subscriber(
                otherUser.getId(), otherProperty.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));

        // Only the authenticated caller's own record can ever be targeted — the request body
        // has no user/subscriber id field at all.
        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(authCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hijacked\"}"))
                .andExpect(status().isOk());

        User otherReloaded = userRepository.findById(otherUser.getId()).orElseThrow();
        assertThat(otherReloaded.getFirstName()).isEqualTo("Other");

        User callerReloaded = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(callerReloaded.getFirstName()).isEqualTo("Hijacked");
    }

    @Test
    void updateAccount_noSubscriberRow_returns404() throws Exception {
        long nano = System.nanoTime();
        String email = "app-account-nosub-" + nano + "@test.local";
        userRepository.save(new User(
                email, passwordEncoder.encode("Test1234!"),
                "No", "Subscriber", Role.CUSTOMER, UserStatus.ACTIVE));
        String token = loginAs(email, "Test1234!");

        mockMvc.perform(patch(ACCOUNT_URL)
                        .cookie(new Cookie("hk_access", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anything\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void updateAccount_anonymous_returns401() throws Exception {
        mockMvc.perform(patch(ACCOUNT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    private Cookie authCookie() {
        return new Cookie("hk_access", customerToken);
    }
}
