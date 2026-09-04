package com.homekept.subscription;

import com.homekept.catalog.CatalogService;
import com.homekept.common.Pagination;
import com.homekept.property.PropertyService;
import com.homekept.subscription.dto.AdminSubscriberDetail;
import com.homekept.subscription.dto.AdminSubscriberListItem;
import com.homekept.subscription.dto.AdminSubscriberPropertySummary;
import com.homekept.subscription.dto.SubscriptionActionResponse;
import com.homekept.subscription.dto.SubscriptionEventItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-facing service for the subscriber console: read-only detail/list views, direct
 * control of a subscriber's subscription (cancel / pause / resume), and the subscriber's
 * activity history.
 *
 * <p>Cross-domain calls go through service interfaces only:
 * <ul>
 *   <li>catalog → {@link CatalogService} (plan code + MRR cents lookup)</li>
 *   <li>property → {@link PropertyService} (property summary for the detail view)</li>
 * </ul>
 *
 * <p>The pause/resume/cancel mutations delegate their actual mechanics (state-machine
 * legality, Stripe calls, idempotency keys) to the package-private methods on
 * {@link SubscriptionSelfServeService} — see that class's javadoc for why. This service only
 * resolves the subscriber by id (admin's own 404, distinct from the self-serve by-user-id
 * lookup) and applies the same billing-presence guard before delegating.
 *
 * <p>MRR is in integer cents — never floats. No PII in logs.
 */
@Service
public class SubscriptionAdminService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionAdminService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EVENTS_LIMIT = 100;

    /** Event type whose payload carries a churn/cancellation reason to surface as {@code note}. */
    private static final String CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";

    private final SubscriberRepository subscriberRepository;
    private final CatalogService catalogService;
    private final PropertyService propertyService;
    private final SubscriptionSelfServeService selfServeService;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final ObjectMapper objectMapper;

    public SubscriptionAdminService(SubscriberRepository subscriberRepository,
                                    CatalogService catalogService,
                                    PropertyService propertyService,
                                    SubscriptionSelfServeService selfServeService,
                                    SubscriptionEventRepository subscriptionEventRepository,
                                    ObjectMapper objectMapper) {
        this.subscriberRepository = subscriberRepository;
        this.catalogService = catalogService;
        this.propertyService = propertyService;
        this.selfServeService = selfServeService;
        this.subscriptionEventRepository = subscriptionEventRepository;
        this.objectMapper = objectMapper;
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
        int pageSize = Pagination.resolveLimit(limit, DEFAULT_PAGE_SIZE, 100);
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

    // ── Subscription lifecycle mutations ────────────────────────────────────────

    /**
     * Cancels a subscriber's subscription, either at period end or immediately, and records
     * the reason as a {@code MANUAL} {@link SubscriptionEvent} (payload
     * {@code {"reason": ..., "by": "ADMIN", "byUserId": ..., "immediate": true|false}})
     * BEFORE calling Stripe. The event and the Stripe call share one transaction (see
     * {@link SubscriptionSelfServeService#cancelSubscriber}), which also rejects a duplicate
     * request (a cancellation already pending for this subscriber) with 409.
     *
     * <p>The CANCELLED status transition itself is applied later by the
     * {@code customer.subscription.deleted} webhook, not here — see that class's javadoc.
     *
     * @param id          subscriber id
     * @param reason      the required admin-supplied reason (churn data)
     * @param immediately {@code false} = cancel at period end; {@code true} = cancel now
     * @param byUserId    the authenticated admin's user id (JWT principal), for the audit trail
     * @return the current status and period end
     * @throws SubscriberNotFoundException      unknown subscriber id (404)
     * @throws NoBillingAccountException        no Stripe subscription yet (409)
     * @throws IllegalSubscriptionStateException the subscriber cannot transition to CANCELLED,
     *                                            or a cancellation is already pending (409)
     */
    @Transactional
    public SubscriptionActionResponse cancelSubscriber(Long id, String reason, boolean immediately, Long byUserId) {
        Subscriber subscriber = requireSubscriber(id);
        selfServeService.requireBilled(subscriber);

        SubscriptionActionResponse response = selfServeService.cancelSubscriber(
                subscriber, serializeAdminCancelPayload(reason, byUserId, immediately), immediately);

        log.info("subscription_admin_cancel_requested subscriberId={} immediately={}",
                subscriber.getId(), immediately);
        return response;
    }

    /**
     * Pauses a subscriber's billing (same semantics/Stripe call as the customer self-serve
     * pause — eligible only from ACTIVE).
     *
     * @param id subscriber id
     * @return the current status and period end
     * @throws SubscriberNotFoundException      unknown subscriber id (404)
     * @throws NoBillingAccountException        no Stripe subscription yet (409)
     * @throws IllegalSubscriptionStateException not eligible to pause (409)
     */
    @Transactional(readOnly = true)
    public SubscriptionActionResponse pauseSubscriber(Long id) {
        Subscriber subscriber = requireSubscriber(id);
        selfServeService.requireBilled(subscriber);

        SubscriptionActionResponse response = selfServeService.pauseSubscriber(subscriber);
        log.info("subscription_admin_pause_requested subscriberId={}", subscriber.getId());
        return response;
    }

    /**
     * Resumes a subscriber's billing (same semantics/Stripe call as the customer self-serve
     * resume — eligible only from PAUSED).
     *
     * @param id subscriber id
     * @return the current status and period end
     * @throws SubscriberNotFoundException      unknown subscriber id (404)
     * @throws NoBillingAccountException        no Stripe subscription yet (409)
     * @throws IllegalSubscriptionStateException not eligible to resume (409)
     */
    @Transactional(readOnly = true)
    public SubscriptionActionResponse resumeSubscriber(Long id) {
        Subscriber subscriber = requireSubscriber(id);
        selfServeService.requireBilled(subscriber);

        SubscriptionActionResponse response = selfServeService.resumeSubscriber(subscriber);
        log.info("subscription_admin_resume_requested subscriberId={}", subscriber.getId());
        return response;
    }

    /**
     * Returns a subscriber's activity history (newest first, capped at {@value #EVENTS_LIMIT}
     * rows): Stripe webhook deliveries and manual (admin/self-serve) actions.
     *
     * @param id subscriber id
     * @return the subscriber's events, newest first
     * @throws SubscriberNotFoundException unknown subscriber id (404)
     */
    @Transactional(readOnly = true)
    public List<SubscriptionEventItem> listSubscriberEvents(Long id) {
        requireSubscriber(id);

        return subscriptionEventRepository
                .findBySubscriberIdOrderByCreatedAtDesc(id, PageRequest.of(0, EVENTS_LIMIT))
                .stream()
                .map(this::toEventItem)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves a subscriber by id for the admin mutation/events endpoints.
     *
     * @throws SubscriberNotFoundException if no subscriber row exists for this id (404)
     */
    private Subscriber requireSubscriber(Long id) {
        return subscriberRepository.findById(id)
                .orElseThrow(() -> new SubscriberNotFoundException("No subscriber row found for id=" + id));
    }

    /**
     * Serializes the admin cancel payload: {@code {"reason": ..., "by": "ADMIN", "byUserId":
     * ..., "immediate": true|false}}. Field order uses a {@link LinkedHashMap} purely for
     * readable JSON when the payload is inspected directly; JSON object key order carries no
     * semantic meaning here.
     */
    private String serializeAdminCancelPayload(String reason, Long byUserId, boolean immediate) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", reason);
        fields.put("by", "ADMIN");
        fields.put("byUserId", byUserId);
        fields.put("immediate", immediate);
        return objectMapper.writeValueAsString(fields);
    }

    /**
     * Maps a {@link SubscriptionEvent} row to its admin-console DTO. {@code note}, {@code by},
     * and {@code immediate} are extracted from the JSONB payload for
     * {@code CANCELLATION_REQUESTED} events only; every other event type reports all three as
     * {@code null}. Self-serve cancellations never carry an {@code immediate} key (always
     * at-period-end), so that field stays {@code null} for those rows too. The payload is
     * parsed, never logged (no PII in logs).
     */
    private SubscriptionEventItem toEventItem(SubscriptionEvent event) {
        String note = null;
        String by = null;
        Boolean immediate = null;
        if (CANCELLATION_REQUESTED.equals(event.getEventType()) && event.getPayload() != null) {
            try {
                JsonNode payload = objectMapper.readTree(event.getPayload());
                note = payload.path("reason").asString(null);
                by = payload.path("by").asString(null);
                JsonNode immediateNode = payload.path("immediate");
                immediate = immediateNode.isBoolean() ? immediateNode.asBoolean() : null;
            } catch (JacksonException e) {
                log.warn("subscription_event_payload_unparseable subscriberId={} eventId={}",
                        event.getSubscriberId(), event.getId());
            }
        }
        return new SubscriptionEventItem(
                event.getId(),
                event.getEventType(),
                event.getSource().name(),
                event.getCreatedAt(),
                note,
                by,
                immediate);
    }

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
                    property.getId(),
                    property.getStreetAddress(),
                    property.getCity(),
                    property.getPostalCode(),
                    property.getPropertyType() != null ? property.getPropertyType().name() : null,
                    property.hasAccessNotes(),
                    property.getHvacFilterSizes(),
                    property.getSmokeCODetectorModels(),
                    property.getHumidifierModel(),
                    property.getWaterHeaterAgeYears(),
                    property.getWaterHeaterFlushEligible()
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
                propertySummary
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
}
