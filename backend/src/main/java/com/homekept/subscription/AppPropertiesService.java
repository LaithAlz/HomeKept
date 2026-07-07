package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.property.Property;
import com.homekept.property.PropertyService;
import com.homekept.subscription.dto.AppPropertySummary;
import com.homekept.visit.HealthScoreService;
import com.homekept.visit.VisitQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Composes the authenticated user's property portfolio for {@code GET /api/app/properties}
 * (multi-property portfolio, Phase 1 — see docs/portfolio-multi-property-proposal.md).
 *
 * <p>One row per {@link Subscriber} the user owns — today, almost always exactly one; a
 * landlord who owns several properties (a CUSTOMER who happens to own more than one
 * subscriber — no separate "organization" concept yet) sees one row per property. This is
 * pure composition over existing domain services: no new data, no fabrication.
 *
 * <h2>Domain boundaries</h2>
 * <p>Cross-domain reads go through service interfaces only, never repositories or entities:
 * <ul>
 *   <li>catalog → {@link CatalogService#findPlanTierSummary}</li>
 *   <li>property → {@link PropertyService#findById}</li>
 *   <li>visit → {@link VisitQueryService#findNextScheduledVisitDate},
 *       {@link VisitQueryService#countOpenTodos}</li>
 *   <li>visit → {@link HealthScoreService#getScoreForSubscriber}</li>
 * </ul>
 */
@Service
public class AppPropertiesService {

    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final PropertyService propertyService;
    private final VisitQueryService visitQueryService;
    private final HealthScoreService healthScoreService;

    public AppPropertiesService(SubscriberQueryService subscriberQueryService,
                                CatalogService catalogService,
                                PropertyService propertyService,
                                VisitQueryService visitQueryService,
                                HealthScoreService healthScoreService) {
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.propertyService = propertyService;
        this.visitQueryService = visitQueryService;
        this.healthScoreService = healthScoreService;
    }

    /**
     * Returns one summary per property the authenticated user owns, ordered by subscriber
     * id ascending — oldest/"primary" property first, the same ordering
     * {@link SubscriberQueryService#resolveOwnedSubscriber} uses to pick the default
     * subscriber elsewhere when {@code propertyId} is omitted.
     *
     * <p>A user with no subscriber at all gets an empty list (not a 404) — this endpoint
     * lists the caller's own properties, so "none yet" is a valid state, not an ownership
     * failure.
     *
     * @param userId the authenticated user's id (JWT principal)
     * @return the portfolio; a single-element list for today's typical customer
     */
    @Transactional(readOnly = true)
    public List<AppPropertySummary> listProperties(Long userId) {
        return subscriberQueryService.findAllByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    private AppPropertySummary toSummary(Subscriber subscriber) {
        Property property = propertyService.findById(subscriber.getPropertyId());

        String planCode = null;
        String planDisplayName = null;
        var planSummary = catalogService.findPlanTierSummary(subscriber.getPlanTierId());
        if (planSummary.isPresent()) {
            planCode = planSummary.get().code();
            planDisplayName = planSummary.get().displayName();
        }

        int healthScore = healthScoreService.getScoreForSubscriber(subscriber.getId());
        Instant nextVisitDate = visitQueryService.findNextScheduledVisitDate(subscriber.getId())
                .orElse(null);
        long openItemsCount = visitQueryService.countOpenTodos(subscriber.getId());

        return new AppPropertySummary(
                subscriber.getPropertyId(),
                subscriber.getId(),
                property != null ? property.getStreetAddress() : null,
                property != null ? property.getCity() : null,
                subscriber.getStatus().name(),
                planCode,
                planDisplayName,
                healthScore,
                nextVisitDate,
                openItemsCount
        );
    }
}
