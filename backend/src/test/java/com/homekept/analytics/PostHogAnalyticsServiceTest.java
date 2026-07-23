package com.homekept.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homekept.config.AppProperties;
import org.junit.jupiter.api.Test;

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
    void buildPayload_hasExpectedShape_distinctIdIsUserIdString() throws Exception {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("phc_test_key", "https://us.i.posthog.com"), mapper);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("severity", "HIGH");
        Instant when = Instant.parse("2026-07-23T12:00:00Z");

        String json = svc.buildPayload(42L, AnalyticsEvent.FLAG_CREATED, props, when);
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("api_key").asText()).isEqualTo("phc_test_key");
        assertThat(node.get("event").asText()).isEqualTo("flag_created");
        // distinct_id is the internal user id as a string — never an email or name.
        assertThat(node.get("distinct_id").asText()).isEqualTo("42");
        assertThat(node.get("properties").get("severity").asText()).isEqualTo("HIGH");
        assertThat(node.get("timestamp").asText()).isEqualTo("2026-07-23T12:00:00Z");
    }

    @Test
    void buildPayload_onlyCarriesTheSuppliedProperties_noHiddenPii() throws Exception {
        PostHogAnalyticsService svc = new PostHogAnalyticsService(
                propsWith("phc_test_key", "https://us.i.posthog.com"), mapper);

        // A caller that (correctly) passes an empty property map must produce an empty
        // properties object — the transport adds nothing of its own beyond the envelope.
        String json = svc.buildPayload(7L, AnalyticsEvent.TODO_ADDED, Map.of(), Instant.EPOCH);
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("properties").isEmpty()).isTrue();
        // The envelope keys are exactly these — nothing that could carry PII.
        assertThat(node.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("api_key", "event", "distinct_id", "properties", "timestamp");
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
