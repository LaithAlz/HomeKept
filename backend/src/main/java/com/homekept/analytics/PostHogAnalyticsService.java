package com.homekept.analytics;

import com.homekept.config.AppProperties;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PostHog implementation of {@link AnalyticsService} (arch doc §5.7).
 *
 * <h2>Graceful degradation</h2>
 * <p>If {@code POSTHOG_API_KEY} ({@code app.analytics.api-key}) is blank, the service is
 * disabled: every {@link #capture} is a no-op and no executor or network client is created.
 * Dev, test, and CI run without a key. Same pattern as R2 / SendGrid.
 *
 * <h2>Best-effort, non-blocking, commit-gated</h2>
 * <ul>
 *   <li><b>Non-blocking:</b> the PostHog HTTP call runs on a small bounded daemon pool, so a
 *       slow or unreachable PostHog never adds latency to a checkout, webhook, or visit
 *       completion. Under a burst the bounded queue overflows to a discard policy — analytics
 *       is dropped, never allowed to block a request thread or exhaust memory.</li>
 *   <li><b>Commit-gated:</b> when a transaction is active the event is buffered and dispatched
 *       in {@code afterCommit}, so a rolled-back business change emits no event. Outside a
 *       transaction it dispatches immediately.</li>
 *   <li><b>Swallows failures:</b> a send failure (timeout, non-2xx, serialization) is logged
 *       at WARN and never propagates. Analytics can never break the thing it measures.</li>
 * </ul>
 *
 * <h2>No PII</h2>
 * <p>This class does not inspect property values — the no-PII rule is enforced at the call
 * sites (see {@link AnalyticsEvent}). The {@code distinct_id} is the internal user id as a
 * string, never an email or name.
 */
@Service
public class PostHogAnalyticsService implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PostHogAnalyticsService.class);

    /** Per-request timeout for the capture POST — short, because it must never hold a worker long. */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final boolean enabled;
    private final String apiKey;
    private final String captureUrl;
    private final ObjectMapper objectMapper;

    /** Null when disabled. Bounded, daemon-threaded, discards under saturation. */
    private final ThreadPoolExecutor executor;
    /** Null when disabled. */
    private final HttpClient httpClient;

    public PostHogAnalyticsService(AppProperties appProperties, ObjectMapper objectMapper) {
        AppProperties.Analytics config = appProperties.analytics();
        this.apiKey = config.apiKey();
        this.objectMapper = objectMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();

        if (!enabled) {
            this.captureUrl = null;
            this.executor = null;
            this.httpClient = null;
            log.info("PostHogAnalyticsService: POSTHOG_API_KEY is blank — analytics capture is a no-op.");
            return;
        }

        // Trim a trailing slash so we don't produce "host//capture/".
        String host = config.host().endsWith("/")
                ? config.host().substring(0, config.host().length() - 1)
                : config.host();
        this.captureUrl = host + "/capture/";

        AtomicLong threadCount = new AtomicLong();
        this.executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread t = new Thread(runnable, "analytics-" + threadCount.incrementAndGet());
                    t.setDaemon(true); // never hold up JVM shutdown for a best-effort send
                    return t;
                },
                // Best-effort: if the queue is full, drop the event rather than block the caller.
                new ThreadPoolExecutor.DiscardPolicy());
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        log.info("PostHogAnalyticsService: enabled, captureUrl={}", captureUrl);
    }

    @Override
    public void capture(Long distinctUserId, String event, Map<String, Object> properties) {
        if (distinctUserId == null) {
            // No identity to attribute to — skip rather than send distinct_id="null".
            log.debug("analytics_skip_no_distinct_id event={}", event);
            return;
        }
        captureInternal(String.valueOf(distinctUserId), event, properties);
    }

    @Override
    public void captureAnonymous(String distinctId, String event, Map<String, Object> properties) {
        if (distinctId == null || distinctId.isBlank()) {
            log.debug("analytics_skip_blank_distinct_id event={}", event);
            return;
        }
        captureInternal(distinctId, event, properties);
    }

    @Override
    public void alias(String anonymousDistinctId, Long userId) {
        if (userId == null || anonymousDistinctId == null || anonymousDistinctId.isBlank()) {
            log.debug("analytics_skip_alias userIdPresent={} anonBlank={}",
                    userId != null, anonymousDistinctId == null || anonymousDistinctId.isBlank());
            return;
        }
        // PostHog server-side person merge: an $identify carrying $anon_distinct_id folds the
        // pre-signup anonymous person into the identified user. (Verify this is still the
        // recommended merge event for the PostHog version in use when the key is wired.)
        captureInternal(String.valueOf(userId), "$identify",
                Map.of("$anon_distinct_id", anonymousDistinctId));
    }

    /**
     * Shared pipeline for all three public methods: no-op when disabled, snapshot the time,
     * defensively copy props, and dispatch after commit (or immediately outside a transaction).
     */
    private void captureInternal(String distinctId, String event, Map<String, Object> properties) {
        if (!enabled) {
            log.debug("analytics_noop event={}", event);
            return;
        }
        // Snapshot the event time now (not when the async send runs) so a queued event keeps
        // its real timestamp. properties is copied defensively so a caller mutating its map
        // after this call cannot affect the payload.
        Instant occurredAt = Instant.now();
        Map<String, Object> propsCopy = (properties == null || properties.isEmpty())
                ? Map.of()
                : new LinkedHashMap<>(properties);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Emit only if the surrounding business transaction commits.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(distinctId, event, propsCopy, occurredAt);
                }
            });
        } else {
            dispatch(distinctId, event, propsCopy, occurredAt);
        }
    }

    private void dispatch(String distinctId, String event, Map<String, Object> props, Instant occurredAt) {
        try {
            executor.execute(() -> send(distinctId, event, props, occurredAt));
        } catch (RejectedExecutionException e) {
            // DiscardPolicy shouldn't reach here, but guard so a full pool never bubbles up.
            log.debug("analytics_dispatch_rejected event={}", event);
        }
    }

    private void send(String distinctId, String event, Map<String, Object> props, Instant occurredAt) {
        try {
            String body = buildPayload(distinctId, event, props, occurredAt);
            HttpRequest request = HttpRequest.newBuilder(URI.create(captureUrl))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.warn("analytics_capture_non2xx event={} status={}", event, response.statusCode());
            } else {
                log.debug("analytics_captured event={}", event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("analytics_capture_interrupted event={}", event);
        } catch (Exception e) {
            // Best-effort: never let an analytics failure surface. Log the type/message only
            // (the api key lives in the body, which is never logged).
            log.warn("analytics_capture_failed event={}: {}", event, e.toString());
        }
    }

    /**
     * Serializes the PostHog {@code /capture/} payload. Package-private for unit testing —
     * asserts the shape and that no PII leaks in. {@code distinctId} is already a string
     * (an internal user id or an anonymous id); the transport never sees a raw entity.
     */
    String buildPayload(String distinctId, String event, Map<String, Object> props, Instant occurredAt) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("api_key", apiKey);
            payload.put("event", event);
            payload.put("distinct_id", distinctId);
            payload.put("properties", props == null ? Map.of() : props);
            payload.put("timestamp", occurredAt.toString());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // Should never happen for plain maps of primitives; degrade to an empty object.
            log.warn("analytics_payload_serialization_failed event={}: {}", event, e.toString());
            return "{}";
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
