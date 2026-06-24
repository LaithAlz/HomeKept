package com.homekept;

import com.homekept.catalog.PlanTier;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level test configuration that registers a {@code @Primary} {@link StripeService}
 * stub for controller tests that must not call the live Stripe API.
 *
 * <p>Import this class with {@code @Import(FakeStripeServiceConfig.class)} only on tests
 * that exercise checkout / portal / subscription-lifecycle controller logic. The webhook
 * integration tests use the REAL {@link com.homekept.subscription.StripeServiceImpl} so
 * that {@code Webhook.constructEvent} actually verifies signatures.
 *
 * <p>The fake {@link RecordingStripeService} returns canned URLs and records the
 * subscription ids passed to pause/resume/cancel so lifecycle tests can assert that the
 * controller actually reached the Stripe seam (the real PAUSED/CANCELLED transition is
 * driven by webhooks and covered separately by the webhook integration tests). Inject the
 * concrete {@code RecordingStripeService} and call {@link RecordingStripeService#reset()}
 * in {@code @BeforeEach} to avoid cross-test bleed.
 *
 * <p>This is a top-level {@code @TestConfiguration} class (not nested) because nested
 * {@code @TestConfiguration} classes inside a {@code @SpringBootTest} class are unreliable
 * in Spring Boot 4.x — the bean registration can be silently skipped.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeStripeServiceConfig {

    public static final String FAKE_CHECKOUT_URL = "https://checkout.stripe.test/session";
    public static final String FAKE_PORTAL_URL   = "https://billing.stripe.test/portal";

    @Bean
    @Primary
    RecordingStripeService fakeStripeService() {
        return new RecordingStripeService();
    }

    /** Hand fake that records subscription-lifecycle calls for assertions. */
    public static class RecordingStripeService implements StripeService {

        public final List<String> pausedSubscriptionIds    = new ArrayList<>();
        public final List<String> resumedSubscriptionIds   = new ArrayList<>();
        public final List<String> cancelledSubscriptionIds = new ArrayList<>();

        /** Clears all recorded calls — call in @BeforeEach. */
        public void reset() {
            pausedSubscriptionIds.clear();
            resumedSubscriptionIds.clear();
            cancelledSubscriptionIds.clear();
        }

        @Override
        public String createCheckoutSession(Subscriber subscriber, PlanTier plan,
                                            BillingCycle cycle, boolean foundingRate,
                                            String idempotencyKey) {
            return FAKE_CHECKOUT_URL;
        }

        @Override
        public String createPortalSession(String stripeCustomerId) {
            return FAKE_PORTAL_URL;
        }

        @Override
        public void pauseSubscription(String stripeSubscriptionId, String idempotencyKey) {
            pausedSubscriptionIds.add(stripeSubscriptionId);
        }

        @Override
        public void resumeSubscription(String stripeSubscriptionId, String idempotencyKey) {
            resumedSubscriptionIds.add(stripeSubscriptionId);
        }

        @Override
        public void cancelSubscriptionAtPeriodEnd(String stripeSubscriptionId, String idempotencyKey) {
            cancelledSubscriptionIds.add(stripeSubscriptionId);
        }

        /**
         * NOT used by tests that import this config — those tests do not exercise the
         * webhook endpoint. For completeness we throw so any accidental call is loud.
         */
        @Override
        public Event constructWebhookEvent(String payload, String sigHeader)
                throws SignatureVerificationException {
            throw new UnsupportedOperationException(
                    "FakeStripeServiceConfig does not support constructWebhookEvent. "
                    + "Use the real StripeService in webhook integration tests.");
        }
    }
}
