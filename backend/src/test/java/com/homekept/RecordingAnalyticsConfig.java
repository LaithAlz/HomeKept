package com.homekept;

import com.homekept.analytics.AnalyticsService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test configuration that swaps in a recording {@link AnalyticsService} so a test can assert
 * that a service method emitted the expected event with the expected (PII-free) properties.
 *
 * <p>The recorder captures synchronously in {@code capture} — it deliberately does NOT
 * reproduce {@link com.homekept.analytics.PostHogAnalyticsService}'s after-commit / async
 * transport (that is the transport's own concern, unit-tested separately). This bean exists
 * purely to prove the <em>wiring</em>: that the right event, distinct id, and props reach
 * the seam.
 *
 * <p>Top-level (not nested) for the same Spring-Boot-4.x reason as
 * {@link FakeStorageServiceConfig}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingAnalyticsConfig {

    /** A user-attributed event. {@code props} is a defensive copy that tolerates null values. */
    public record Captured(Long distinctId, String event, Map<String, Object> props) {}

    /** An anonymous (pre-signup) event, keyed by a String distinct id. */
    public record AnonymousCaptured(String distinctId, String event, Map<String, Object> props) {}

    /** An anonymous→user identity merge. */
    public record Aliased(String anonymousDistinctId, Long userId) {}

    public static final class RecordingAnalyticsService implements AnalyticsService {
        private final List<Captured> captured = new CopyOnWriteArrayList<>();
        private final List<AnonymousCaptured> anonymousCaptured = new CopyOnWriteArrayList<>();
        private final List<Aliased> aliases = new CopyOnWriteArrayList<>();

        @Override
        public void capture(Long distinctUserId, String event, Map<String, Object> properties) {
            // LinkedHashMap copy (not Map.copyOf) so a null property value (e.g. a null
            // visit_template id) does not blow up the recorder.
            Map<String, Object> copy = properties == null ? Map.of() : new LinkedHashMap<>(properties);
            captured.add(new Captured(distinctUserId, event, copy));
        }

        @Override
        public void captureAnonymous(String distinctId, String event, Map<String, Object> properties) {
            Map<String, Object> copy = properties == null ? Map.of() : new LinkedHashMap<>(properties);
            anonymousCaptured.add(new AnonymousCaptured(distinctId, event, copy));
        }

        @Override
        public void alias(String anonymousDistinctId, Long userId) {
            aliases.add(new Aliased(anonymousDistinctId, userId));
        }

        public List<Captured> events() {
            return List.copyOf(captured);
        }

        public List<AnonymousCaptured> anonymousEvents() {
            return List.copyOf(anonymousCaptured);
        }

        public List<Aliased> aliases() {
            return List.copyOf(aliases);
        }

        public void clear() {
            captured.clear();
            anonymousCaptured.clear();
            aliases.clear();
        }
    }

    @Bean
    @Primary
    RecordingAnalyticsService recordingAnalyticsService() {
        return new RecordingAnalyticsService();
    }
}
