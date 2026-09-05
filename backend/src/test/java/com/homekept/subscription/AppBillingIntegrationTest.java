package com.homekept.subscription;

import com.homekept.AbstractIntegrationTest;
import com.homekept.FakeStripeServiceConfig;
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
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SubscriptionController}'s billing read endpoints:
 * {@code GET /api/app/billing/invoices} and {@code GET /api/app/billing/payment-method}.
 *
 * <p>Imports {@link FakeStripeServiceConfig} so no live Stripe API calls are made.
 *
 * <p>Covers:
 * <ul>
 *   <li>A subscriber with no Stripe customer id gets an empty list / {@code null} — never
 *       an error, and the fake Stripe service is never even called.</li>
 *   <li>A subscriber with a Stripe customer id gets the mapped Stripe data.</li>
 *   <li>No subscriber row → 404; non-CUSTOMER role → 403.</li>
 * </ul>
 *
 * <p>Runs against a real Postgres via Testcontainers.
 */
@Import(FakeStripeServiceConfig.class)
class AppBillingIntegrationTest extends AbstractIntegrationTest {

    private static final String INVOICES_URL = "/api/app/billing/invoices";
    private static final String PAYMENT_METHOD_URL = "/api/app/billing/payment-method";

    @Autowired SubscriberRepository subscriberRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired FakeStripeServiceConfig.RecordingStripeService recordingStripe;

    private User customerUser;
    private Subscriber customerSubscriber;
    private String customerToken;

    @BeforeEach
    void seedCustomer() throws Exception {
        recordingStripe.reset();
        long nano = System.nanoTime();

        customerUser = userRepository.save(new User(
                "app-billing-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Priya", "Sharma",
                Role.CUSTOMER, UserStatus.ACTIVE));

        Property property = propertyRepository.save(new Property(
                nano + " Maple Ridge Crt", "Unit 4", "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));

        customerSubscriber = new Subscriber(
                customerUser.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY);
        customerSubscriber = subscriberRepository.save(customerSubscriber);

        customerToken = loginAs(customerUser.getEmail(), "Test1234!");
    }

    // ── GET /api/app/billing/invoices ────────────────────────────────────────

    @Test
    void listInvoices_noStripeCustomerId_returnsEmptyList_andNeverCallsStripe() throws Exception {
        // customerSubscriber has no stripeCustomerId set (pre-checkout).
        mockMvc.perform(get(INVOICES_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        assertThat(recordingStripe.listInvoicesCustomerIds).isEmpty();
    }

    @Test
    void listInvoices_withStripeCustomerId_returnsMappedInvoices() throws Exception {
        customerSubscriber.setStripeCustomerId("cus_billing_test_1");
        subscriberRepository.save(customerSubscriber);

        Instant createdAt = Instant.parse("2026-06-01T12:00:00Z");
        recordingStripe.invoicesToReturn = List.of(new StripeService.StripeInvoiceSummary(
                "in_123", "HK-0001", createdAt, 16900, "cad", "paid",
                "https://invoice.stripe.test/in_123", "https://invoice.stripe.test/in_123.pdf"));

        mockMvc.perform(get(INVOICES_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("in_123"))
                .andExpect(jsonPath("$[0].number").value("HK-0001"))
                .andExpect(jsonPath("$[0].amountPaidCents").value(16900))
                .andExpect(jsonPath("$[0].currency").value("cad"))
                .andExpect(jsonPath("$[0].status").value("paid"))
                .andExpect(jsonPath("$[0].hostedInvoiceUrl").value("https://invoice.stripe.test/in_123"))
                .andExpect(jsonPath("$[0].invoicePdf").value("https://invoice.stripe.test/in_123.pdf"));

        assertThat(recordingStripe.listInvoicesCustomerIds).containsExactly("cus_billing_test_1");
    }

    @Test
    void listInvoices_noSubscriberRow_returns404() throws Exception {
        String token = createCustomerWithNoSubscriber();

        mockMvc.perform(get(INVOICES_URL).cookie(new Cookie("hk_access", token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void listInvoices_anonymous_returns401() throws Exception {
        mockMvc.perform(get(INVOICES_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listInvoices_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(get(INVOICES_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/app/billing/payment-method ──────────────────────────────────

    @Test
    void getDefaultPaymentMethod_noStripeCustomerId_returnsNull_andNeverCallsStripe() throws Exception {
        mockMvc.perform(get(PAYMENT_METHOD_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        assertThat(recordingStripe.findPaymentMethodCustomerIds).isEmpty();
    }

    @Test
    void getDefaultPaymentMethod_noCardOnFile_returnsNull() throws Exception {
        customerSubscriber.setStripeCustomerId("cus_billing_test_2");
        subscriberRepository.save(customerSubscriber);
        // recordingStripe.paymentMethodToReturn defaults to Optional.empty().

        mockMvc.perform(get(PAYMENT_METHOD_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));

        assertThat(recordingStripe.findPaymentMethodCustomerIds).containsExactly("cus_billing_test_2");
    }

    @Test
    void getDefaultPaymentMethod_withCardOnFile_returnsCardSummary() throws Exception {
        customerSubscriber.setStripeCustomerId("cus_billing_test_3");
        subscriberRepository.save(customerSubscriber);
        recordingStripe.paymentMethodToReturn = Optional.of(
                new StripeService.StripePaymentMethodSummary("visa", "4242", 4, 2029));

        mockMvc.perform(get(PAYMENT_METHOD_URL).cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("visa"))
                .andExpect(jsonPath("$.last4").value("4242"))
                .andExpect(jsonPath("$.expMonth").value(4))
                .andExpect(jsonPath("$.expYear").value(2029));
    }

    @Test
    void getDefaultPaymentMethod_noSubscriberRow_returns404() throws Exception {
        String token = createCustomerWithNoSubscriber();

        mockMvc.perform(get(PAYMENT_METHOD_URL).cookie(new Cookie("hk_access", token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getDefaultPaymentMethod_anonymous_returns401() throws Exception {
        mockMvc.perform(get(PAYMENT_METHOD_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDefaultPaymentMethod_asAdmin_returns403() throws Exception {
        String adminToken = loginAsNewAdmin();

        mockMvc.perform(get(PAYMENT_METHOD_URL).cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createCustomerWithNoSubscriber() throws Exception {
        long nano = System.nanoTime();
        String email = "app-billing-nosub-" + nano + "@test.local";
        userRepository.save(new User(
                email, passwordEncoder.encode("Test1234!"),
                "No", "Subscriber", Role.CUSTOMER, UserStatus.ACTIVE));
        return loginAs(email, "Test1234!");
    }

    private String loginAsNewAdmin() throws Exception {
        long nano = System.nanoTime();
        String email = "app-billing-admin-" + nano + "@test.local";
        userRepository.save(new User(
                email, passwordEncoder.encode("Test1234!"),
                "Admin", "Test", Role.ADMIN, UserStatus.ACTIVE));
        return loginAs(email, "Test1234!");
    }

    private Cookie authCookie() {
        return new Cookie("hk_access", customerToken);
    }
}
