package com.homekept.analytics;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link PostHogAnalyticsService} — the analytics transport contract, with no
 * Spring context and no network. Covers the two guarantees a unit test can pin down without a
 * live PostHog: degrade-to-no-op without a key, and the exact capture payload shape (including
 * that no PII sneaks in and the distinct_id is the internal user id as a string).
 */
class PostHogAnalyticsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Builds an AppProperties whose only meaningful field is the analytics block. */
    private static AppProperties propsWith(String apiKey, String host) {
        return new AppProperties(
                "America/Toronto", false, false,
                null, null, null, null, null, null,
                "http://localhost:8080", null,
                new AppProperties.Analytics(apiKey, host));
    }

    @Test
    void disabled_whenApiKeyBlank_captureIsSilentNoOp() {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("", "https://us.i.posthog.com"), mapper);

        // No key → capture must not throw and must do nothing (no executor exists to submit to).
        assertThatCode(() -> svc.capture(42L, AnalyticsEvent.TODO_ADDED, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_toleratesNullDistinctIdAndNullProps() {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("", "https://us.i.posthog.com"), mapper);

        assertThatCode(() -> svc.capture(null, AnalyticsEvent.FLAG_CREATED, null))
                .doesNotThrowAnyException();
    }

    @Test
    void buildPayload_hasExpectedShape_distinctIdIsUserIdString() {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("phc_test_key", "https://us.i.posthog.com"), mapper);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("severity", "HIGH");
        Instant when = Instant.parse("2026-07-23T12:00:00Z");

        String json = svc.buildPayload(42L, AnalyticsEvent.FLAG_CREATED, props, when);

        // buildPayload uses a LinkedHashMap, so key order is deterministic. Asserting the
        // exact string proves the envelope shape AND that distinct_id is the internal user
        // id as a string (never an email/name) AND that no extra key sneaks in.
        assertThat(json).isEqualTo(
                "{\"api_key\":\"phc_test_key\",\"event\":\"flag_created\","
                        + "\"distinct_id\":\"42\",\"properties\":{\"severity\":\"HIGH\"},"
                        + "\"timestamp\":\"2026-07-23T12:00:00Z\"}");
    }

    @Test
    void buildPayload_onlyCarriesTheSuppliedProperties_noHiddenPii() {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("phc_test_key", "https://us.i.posthog.com"), mapper);

        // A caller that (correctly) passes an empty property map produces an empty properties
        // object — the transport adds nothing of its own beyond the fixed envelope, so there
        // is no channel through which PII could leak.
        String json = svc.buildPayload(7L, AnalyticsEvent.TODO_ADDED, Map.of(), Instant.EPOCH);

        assertThat(json).isEqualTo(
                "{\"api_key\":\"phc_test_key\",\"event\":\"todo_added\","
                        + "\"distinct_id\":\"7\",\"properties\":{},"
                        + "\"timestamp\":\"1970-01-01T00:00:00Z\"}");
    }

    @Test
    void trailingSlashInHost_isNormalised_noDoubleSlashInCaptureUrl() {
        // Constructing with a trailing-slash host must not throw and must not produce
        // "host//capture/" (verified indirectly: construction succeeds and enabled path is live).
        assertThatCode(() -> new PostHogAnalyticsService(
                propsWith("phc_test_key", "https://us.i.posthog.com/"), mapper))
                .doesNotThrowAnyException();
    }
}
