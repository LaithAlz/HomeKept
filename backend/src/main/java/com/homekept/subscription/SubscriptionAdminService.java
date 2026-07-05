package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.property.PropertyService;
import com.homekept.subscription.dto.AdminSubscriberDetail;
import com.homekept.subscription.dto.AdminSubscriberListItem;
import com.homekept.subscription.dto.AdminSubscriberPropertySummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only service for the admin subscriber console.
 *
 * <p>Cross-domain calls go through service interfaces only:
 * <ul>
 *   <li>catalog → {@link CatalogService} (plan code + MRR cents lookup)</li>
 *   <li>property → {@link PropertyService} (property summary for the detail view)</li>
 * </ul>
 *
 * <p>MRR is in integer cents — never floats. No PII in logs.
 */
@Service
public class SubscriptionAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SubscriberRepository subscriberRepository;
    private final CatalogService catalogService;
    private final PropertyService propertyService;

    public SubscriptionAdminService(SubscriberRepository subscriberRepository,
                                    CatalogService catalogService,
                                    PropertyService propertyService) {
        this.subscriberRepository = subscriberRepository;
        this.catalogService = catalogService;
        this.propertyService = propertyService;
    }

    /**
     * Returns a cursor-paginated list of subscribers for the admin console.
     * Ordered by id descending (newest first).
     *
     * @param cursor optional id cursor (exclusive upper bound)
     * @param limit  optional page size (default 20, max 100)
     */
    @Transactional(readOnly = true)
    public List<AdminSubscriberListItem> listSubscribers(Long cursor, Integer limit) {
        int pageSize = resolveLimit(limit);
        PageRequest pageable = PageRequest.of(0, pageSize);

        List<Subscriber> subscribers = (cursor != null)
                ? subscriberRepository.findByIdLessThanOrderByIdDesc(cursor, pageable)
                : subscriberRepository.findAllByOrderByIdDesc(pageable);

        return subscribers.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    /**
     * Returns the full admin detail for a single subscriber.
     * Returns {@code null} if not found — caller is responsible for the 404.
     *
     * @param id subscriber id
     * @return detail DTO or {@code null}
     */
    @Transactional(readOnly = true)
    public AdminSubscriberDetail getSubscriberDetail(Long id) {
        Subscriber subscriber = subscriberRepository.findById(id).orElse(null);
        if (subscriber == null) {
            return null;
        }
        return toDetail(subscriber);
    }

    /**
     * Computes the subscription-domain slice of the admin dashboard aggregate
     * ({@code GET /api/admin/dashboard}):
     * <ul>
     *   <li>{@code activeSubscribers} — count of subscribers with status ACTIVE.</li>
     *   <li>{@code mrrCents} — sum of {@link #computeMrrCents} across ACTIVE subscribers
     *       only (PAUSED/PAYMENT_ISSUE/CANCELLED/PENDING_ACTIVATION are excluded — they
     *       are not currently-paying recurring revenue).</li>
     *   <li>{@code foundingRateSlotsRemaining} — {@link FoundingRateAvailabilityImpl#FOUNDING_CAP}
     *       minus the count of founding-rate subscribers (never negative).</li>
     * </ul>
     *
     * @return the subscription metrics slice
     */
    @Transactional(readOnly = true)
    public SubscriptionMetrics getDashboardMetrics() {
        List<Subscriber> activeSubscribers = subscriberRepository.findByStatus(SubscriberStatus.ACTIVE);

        int mrrCents = activeSubscribers.stream()
                .mapToInt(s -> {
                    Integer cents = computeMrrCents(s);
                    return cents != null ? cents : 0;
                })
                .sum();

        long foundingRateSlotsRemaining = Math.max(0,
                FoundingRateAvailabilityImpl.FOUNDING_CAP - subscriberRepository.countByFoundingRateTrue());

        return new SubscriptionMetrics(activeSubscribers.size(), mrrCents, foundingRateSlotsRemaining);
    }

    /**
     * Subscription-domain slice of the admin dashboard aggregate. See
     * {@link #getDashboardMetrics()} for how each field is computed.
     */
    public record SubscriptionMetrics(long activeSubscribers, int mrrCents, long foundingRateSlotsRemaining) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AdminSubscriberListItem toListItem(Subscriber s) {
        String planCode = catalogService.getPlanCode(s.getPlanTierId());
        Integer mrrCents = computeMrrCents(s);
        return new AdminSubscriberListItem(
                s.getId(),
                s.getStatus().name(),
                planCode,
                mrrCents,
                s.isFoundingRate()
        );
    }

    private AdminSubscriberDetail toDetail(Subscriber s) {
        String planCode = catalogService.getPlanCode(s.getPlanTierId());
        Integer mrrCents = computeMrrCents(s);

        AdminSubscriberPropertySummary propertySummary = null;
        var property = propertyService.findById(s.getPropertyId());
        if (property != null) {
            propertySummary = new AdminSubscriberPropertySummary(
                    property.getStreetAddress(),
                    property.getCity(),
                    property.getPostalCode(),
                    property.getPropertyType() != null ? property.getPropertyType().name() : null,
                    property.hasAccessNotes()
            );
        }

        return new AdminSubscriberDetail(
                s.getId(),
                s.getUserId(),
                s.getStatus().name(),
                planCode,
                mrrCents,
                s.isFoundingRate(),
                s.getBillingCycle().name(),
                s.getStripeCustomerId(),
                s.getStripeSubscriptionId(),
                s.getCurrentPeriodStart(),
                s.getCurrentPeriodEnd(),
                s.getStartedAt(),
                s.getPausedAt(),
                s.getCancelledAt(),
                propertySummary,
                List.of()
        );
    }

    /**
     * Computes MRR in integer cents for the given subscriber.
     * Returns {@code null} when no plan tier has been assigned yet (pre-checkout).
     * Founding-rate subscribers use the founding monthly price when set.
     * All other cases use the regular monthly price.
     */
    private Integer computeMrrCents(Subscriber s) {
        if (s.getPlanTierId() == null) {
            return null;
        }
        if (s.isFoundingRate()) {
            Integer foundingPrice = catalogService.getFoundingMonthlyPriceCents(s.getPlanTierId());
            if (foundingPrice != null) {
                return foundingPrice;
            }
        }
        return catalogService.getMonthlyPriceCents(s.getPlanTierId());
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 100);
    }
}
