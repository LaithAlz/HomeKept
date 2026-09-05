package com.homekept.subscription;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests (no Spring context, no Testcontainers) for
 * {@link StripeServiceImpl#listInvoices} / {@link StripeServiceImpl#findDefaultPaymentMethod}
 * blank-key graceful degradation — the "Stripe key is blank" branch cannot be exercised
 * through {@code AbstractIntegrationTest}, whose test profile sets a non-blank (fake)
 * {@code app.stripe.secret-key} so other Stripe-adjacent tests don't accidentally construct
 * a {@code StripeConfig} that warns/fails. Constructing {@link StripeServiceImpl} directly
 * with a blank key lets us assert the graceful-degradation branch without ever reaching the
 * Stripe SDK (a real call with a blank key would otherwise attempt a live HTTP request).
 */
class StripeServiceImplTest {

    @Test
    void listInvoices_blankSecretKey_returnsEmptyList() {
        StripeServiceImpl service = new StripeServiceImpl(appPropertiesWithBlankStripeKey());

        List<StripeService.StripeInvoiceSummary> result = service.listInvoices("cus_123", 24);

        assertThat(result).isEmpty();
    }

    @Test
    void findDefaultPaymentMethod_blankSecretKey_returnsEmpty() {
        StripeServiceImpl service = new StripeServiceImpl(appPropertiesWithBlankStripeKey());

        Optional<StripeService.StripePaymentMethodSummary> result =
                service.findDefaultPaymentMethod("cus_123");

        assertThat(result).isEmpty();
    }

    /**
     * Builds an {@link AppProperties} with a blank {@code stripe.secretKey} and every other
     * field left {@code null} — safe because {@code listInvoices}/{@code findDefaultPaymentMethod}
     * only ever read {@code appProperties.stripe().secretKey()} before short-circuiting.
     */
    private AppProperties appPropertiesWithBlankStripeKey() {
        AppProperties.Stripe blankStripe = new AppProperties.Stripe("", "", "", "", "");
        return new AppProperties(
                "America/Toronto", false, false,
                null, null, null, null,
                blankStripe,
                null, null, null, null);
    }
}
