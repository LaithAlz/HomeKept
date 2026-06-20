package com.homekept.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Provides the render time zone as a Spring bean.
 *
 * <p>All timestamps are stored UTC (Instant / TIMESTAMPTZ). This bean provides the
 * display zone for rendering timestamps to users. Callers inject {@link ZoneId}
 * from this bean — never hardcode {@code "America/Toronto"} in business logic.
 * When we expand to a second metro, this is the only config that changes.
 */
@Configuration
public class TimeZoneConfig {

    private final AppProperties appProperties;

    public TimeZoneConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * The zone to use when rendering timestamps to users (display only — storage is UTC).
     */
    @Bean
    public ZoneId renderZoneId() {
        return ZoneId.of(appProperties.timezone());
    }
}
