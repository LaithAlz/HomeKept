package com.homekept.catalog;

import org.springframework.stereotype.Component;

/**
 * Temporary stand-in for the real founding-rate availability check.
 *
 * <p><strong>TODO (issue #55 — subscription slice):</strong> Replace this bean with a real
 * implementation that counts {@code subscriber.founding_rate = true} rows and returns
 * {@code false} once that count reaches 15. Until the subscription domain exists,
 * 0 subscribers ⇒ 0 founding subscribers ⇒ slots are available ⇒ {@code true}.
 *
 * <p>This is a plain {@code @Component} (a single bean of this type at MVP). The subscription
 * slice supersedes it by registering its own {@link FoundingRateAvailability} marked
 * {@code @Primary} (or by deleting this class). Note: {@code @ConditionalOnMissingBean} is
 * deliberately NOT used here — it is unreliable on component-scanned beans and would leave
 * the type unsatisfied at context load.
 *
 * <p>The founding-rate cap (15 subscribers) is enforced by counting
 * {@code subscriber.founding_rate = true} rows, per api-contract.md lines 113-116.
 */
@Component
public class DefaultFoundingRateAvailability implements FoundingRateAvailability {

    @Override
    public boolean foundingSlotsRemaining() {
        // Founding rate is open while fewer than 15 founding subscribers exist.
        // The subscription slice (issue #55) replaces this with a real
        // subscriber.founding_rate = true count < 15. Until that domain exists,
        // 0 subscribers ⇒ available.
        return true;
    }
}
