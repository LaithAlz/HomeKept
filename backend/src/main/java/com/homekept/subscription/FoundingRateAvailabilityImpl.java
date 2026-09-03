package com.homekept.subscription;

import com.homekept.catalog.FoundingRateAvailability;
import org.springframework.stereotype.Component;

/**
 * Live implementation of {@link FoundingRateAvailability} that counts actual founding
 * subscribers in the database. This is the only bean of this type registered in the
 * context; it is injected wherever {@link FoundingRateAvailability} is needed (e.g.,
 * {@link com.homekept.catalog.CatalogService}).
 *
 * <p>The founding-rate cap is 15 subscribers globally. This method returns {@code false}
 * once that count is reached, causing {@code GET /api/catalog/plans} to reflect
 * {@code foundingRateAvailable: false} for tiers that have a founding price.
 */
@Component
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
