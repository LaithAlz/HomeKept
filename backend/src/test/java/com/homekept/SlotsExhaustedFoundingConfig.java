package com.homekept;

import com.homekept.catalog.FoundingRateAvailability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that supersedes {@code DefaultFoundingRateAvailability} with a
 * cap-reached stub ({@code foundingSlotsRemaining() == false}), marked {@code @Primary}
 * so it wins over the plain {@code @Component} default — the same way the subscription
 * slice will register its real bean.
 *
 * <p>Deliberately a TOP-LEVEL class (not nested in the test) so it is NOT auto-detected
 * by the enclosing {@code @SpringBootTest} and only applies where it is explicitly
 * {@code @Import}ed. A static nested {@code @TestConfiguration} would leak into the outer
 * context and force every COMPLETE founding-rate assertion to false.
 */
@TestConfiguration
public class SlotsExhaustedFoundingConfig {

    @Bean
    @Primary
    FoundingRateAvailability slotsExhaustedAvailability() {
        return () -> false;
    }
}
