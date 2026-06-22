package com.homekept.subscription;

import com.homekept.catalog.FoundingRateAvailability;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Live implementation of {@link FoundingRateAvailability} that counts actual founding
 * subscribers in the database.
 *
 * <p>This {@code @Primary} bean overrides {@link com.homekept.catalog.DefaultFoundingRateAvailability}
 * (the placeholder in the catalog domain) once the subscription domain exists.
 * Both beans are registered in the context; {@code @Primary} ensures this one wins
 * when {@link FoundingRateAvailability} is injected anywhere (e.g., {@link com.homekept.catalog.CatalogService}).
 *
 * <p>Note: {@code @ConditionalOnMissingBean} is deliberately NOT used — it is unreliable
 * on component-scanned {@code @Component} classes. The {@code @Primary} pattern is the
 * correct approach here per the issue spec.
 *
 * <p>The founding-rate cap is 15 subscribers globally. This method returns {@code false}
 * once that count is reached, causing {@code GET /api/catalog/plans} to reflect
 * {@code foundingRateAvailable: false} for tiers that have a founding price.
 */
@Component
@Primary
public class FoundingRateAvailabilityImpl implements FoundingRateAvailability {

    /** Maximum number of founding-rate subscribers allowed. */
    public static final long FOUNDING_CAP = 15L;

    private final SubscriberRepository subscriberRepository;

    public FoundingRateAvailabilityImpl(SubscriberRepository subscriberRepository) {
        this.subscriberRepository = subscriberRepository;
    }

    /**
     * Returns {@code true} if fewer than 15 subscribers have {@code founding_rate = true}.
     * Cheap query — single COUNT on an indexed boolean column.
     */
    @Override
    public boolean foundingSlotsRemaining() {
        return subscriberRepository.countByFoundingRateTrue() < FOUNDING_CAP;
    }
}
