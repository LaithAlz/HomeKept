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

    /** One captured event. {@code props} is a defensive copy that tolerates null values. */
    public record Captured(Long distinctId, String event, Map<String, Object> props) {}

    public static final class RecordingAnalyticsService implements AnalyticsService {
        private final List<Captured> captured = new CopyOnWriteArrayList<>();

        @Override
        public void capture(Long distinctUserId, String event, Map<String, Object> properties) {
            // LinkedHashMap copy (not Map.copyOf) so a null property value (e.g. a null
            // visit_template id) does not blow up the recorder.
            Map<String, Object> copy = properties == null ? Map.of() : new LinkedHashMap<>(properties);
            captured.add(new Captured(distinctUserId, event, copy));
        }

        public List<Captured> events() {
            return List.copyOf(captured);
        }

        public void clear() {
            captured.clear();
        }
    }

    @Bean
    @Primary
    RecordingAnalyticsService recordingAnalyticsService() {
        return new RecordingAnalyticsService();
    }
}
