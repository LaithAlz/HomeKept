package com.homekept.visit;

import com.homekept.catalog.CatalogService;
import com.homekept.catalog.PlanCode;
import com.homekept.subscription.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Schedules the initial set of upcoming ROUTINE visits for a newly-activated subscriber.
 *
 * <h2>Scheduling window</h2>
 * <p>On activation, this service looks at the current month and schedules visits for
 * the <em>next {@value #LOOKAHEAD_MONTHS} months</em> (exclusive of the past). For each
 * calendar month in that window, if the subscriber's tier has a matching template, a
 * {@link Visit} is created in SCHEDULED status at noon Toronto time on the 15th of
 * that month (a neutral mid-month placeholder — admin confirms/adjusts the real date).
 * If the 15th is in the past relative to today, the visit is pushed to a week from now
 * (so admin still gets a window to confirm before it's overdue).
 *
 * <h2>Cumulative calendar</h2>
 * <p>The calendar is cumulative: an ESSENTIAL subscriber gets ESSENTIAL-only templates;
 * a COMPLETE subscriber gets ESSENTIAL + COMPLETE templates; a PREMIER subscriber gets
 * all three. The {@link VisitTemplateRepository#findByMinTierIn} query handles this.
 *
 * <h2>Idempotency</h2>
 * <p>If the subscriber already has any SCHEDULED or IN_PROGRESS visits, this method
 * returns immediately without creating duplicates. This keeps the Stripe webhook safe to
 * retry and prevents double-scheduling on webhook replay.
 *
 * <h2>Standing items only</h2>
 * <p>Each created visit gets only the template's standing-item services (those linked via
 * {@link VisitTemplateService} rows, source = TEMPLATE). Picks, todos, and flagged items
 * fold in later — their slices are not built yet.
 *
 * <h2>Domain boundary</h2>
 * <p>This service is in {@code com.homekept.visit}. It depends on
 * {@link CatalogService} (catalog domain) to resolve the subscriber's plan tier code —
 * it never reaches into the catalog repository or entities directly.
 * It receives a {@link Subscriber} loaded by the caller (e.g. {@code VisitSchedulingListener})
 * via {@link com.homekept.subscription.SubscriberQueryService} — never via the subscription
 * repository directly.
 */
@Service
public class VisitSchedulingService {

    private static final Logger log = LoggerFactory.getLogger(VisitSchedulingService.class);

    /** Number of months ahead to schedule. Covers the near-term calendar. */
    static final int LOOKAHEAD_MONTHS = 4;

    /** Default visit duration in minutes (admin adjusts as needed). */
    static final int DEFAULT_DURATION_MINUTES = 120;

    /** Placeholder day-of-month for auto-scheduled visits (mid-month). */
    private static final int PLACEHOLDER_DAY = 15;

    /** Minimum days from now before we push a placeholder forward instead of using the 15th. */
    private static final int MIN_DAYS_AHEAD = 7;

    private final VisitRepository visitRepository;
    private final VisitTemplateRepository visitTemplateRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final CatalogService catalogService;
    private final ZoneId renderZoneId;

    public VisitSchedulingService(VisitRepository visitRepository,
                                  VisitTemplateRepository visitTemplateRepository,
                                  VisitServiceRepository visitServiceRepository,
                                  CatalogService catalogService,
                                  ZoneId renderZoneId) {
        this.visitRepository = visitRepository;
        this.visitTemplateRepository = visitTemplateRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.catalogService = catalogService;
        this.renderZoneId = renderZoneId;
    }

    /**
     * Creates the initial ROUTINE visits for a newly-activated subscriber.
     *
     * <p>Should be called immediately after the subscriber transitions to ACTIVE
     * (in the Stripe webhook handler). The subscriber's {@code planTierId} must be set.
     *
     * <p>Idempotent: returns without creating any visits if the subscriber already has
     * SCHEDULED or IN_PROGRESS visits.
     *
     * @param subscriber the newly-activated subscriber (planTierId must be non-null)
     */
    @Transactional
    public void scheduleInitialVisits(Subscriber subscriber) {
        if (subscriber.getPlanTierId() == null) {
            log.warn("visit_scheduling_skipped subscriberId={} reason=no_plan_tier", subscriber.getId());
            return;
        }

        // Idempotency guard: skip if visits already exist (e.g. webhook replay).
        boolean alreadyScheduled = visitRepository.existsBySubscriberIdAndStatusIn(
                subscriber.getId(),
                List.of(VisitStatus.SCHEDULED, VisitStatus.IN_PROGRESS));
        if (alreadyScheduled) {
            log.info("visit_scheduling_skipped subscriberId={} reason=visits_already_exist", subscriber.getId());
            return;
        }

        // Resolve the plan tier code via CatalogService (never the catalog repository).
        String planCodeStr = catalogService.getPlanCode(subscriber.getPlanTierId());
        if (planCodeStr == null) {
            log.warn("visit_scheduling_skipped subscriberId={} reason=unknown_plan_tier planTierId={}",
                    subscriber.getId(), subscriber.getPlanTierId());
            return;
        }
        PlanCode subscriberTier = PlanCode.valueOf(planCodeStr);

        // Build the cumulative list of tier codes this subscriber qualifies for.
        List<PlanCode> eligibleTiers = eligibleTiersFor(subscriberTier);

        // Load the matching templates (all months, sorted by month asc).
        List<VisitTemplate> templates = visitTemplateRepository.findByMinTierIn(eligibleTiers);
        if (templates.isEmpty()) {
            log.warn("visit_scheduling_skipped subscriberId={} reason=no_templates_found tier={}",
                    subscriber.getId(), subscriberTier);
            return;
        }

        // Determine which months fall in the lookahead window.
        LocalDate today = LocalDate.now(renderZoneId);
        LocalDate windowEnd = today.plusMonths(LOOKAHEAD_MONTHS);

        List<Visit> createdVisits = new ArrayList<>();

        for (VisitTemplate template : templates) {
            // Find the next occurrence of this template's month in the window.
            LocalDate candidateDate = nextOccurrenceInWindow(template.getMonth(), today, windowEnd, renderZoneId);
            if (candidateDate == null) {
                continue; // month does not fall in the lookahead window
            }

            Instant scheduledFor = candidateDate.atTime(12, 0).atZone(renderZoneId).toInstant();

            Visit visit = new Visit(
                    subscriber.getId(),
                    subscriber.getPropertyId(),
                    template.getId(),
                    scheduledFor,
                    DEFAULT_DURATION_MINUTES,
                    VisitType.ROUTINE
            );
            Visit savedVisit = visitRepository.save(visit);

            // Attach the template's standing-item services as checklist rows.
            List<VisitTemplateService> templateServices = template.getServices();
            for (VisitTemplateService tSvc : templateServices) {
                VisitService vs = new VisitService(
                        savedVisit.getId(),
                        tSvc.getService().getId(),
                        VisitServiceSource.TEMPLATE
                );
                visitServiceRepository.save(vs);
            }

            createdVisits.add(savedVisit);
        }

        log.info("visit_scheduling_complete subscriberId={} tier={} visits_created={}",
                subscriber.getId(), subscriberTier, createdVisits.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the cumulative list of {@link PlanCode} values that a subscriber at
     * {@code tier} qualifies for. Cumulative means: PREMIER gets all three tiers;
     * COMPLETE gets ESSENTIAL + COMPLETE; ESSENTIAL gets only ESSENTIAL.
     */
    static List<PlanCode> eligibleTiersFor(PlanCode tier) {
        return switch (tier) {
            case ESSENTIAL -> List.of(PlanCode.ESSENTIAL);
            case COMPLETE  -> List.of(PlanCode.ESSENTIAL, PlanCode.COMPLETE);
            case PREMIER   -> List.of(PlanCode.ESSENTIAL, PlanCode.COMPLETE, PlanCode.PREMIER);
        };
    }

    /**
     * Returns the placeholder date for the given month within the lookahead window,
     * or {@code null} if the month does not occur in the window.
     *
     * <p>The placeholder is the 15th of the month at noon Toronto time. If that date
     * is within {@value #MIN_DAYS_AHEAD} days of today (or in the past), the placeholder
     * is pushed to {@value #MIN_DAYS_AHEAD} days from now so admin still has time to
     * confirm. If the adjusted date falls outside the window, returns null.
     *
     * @param month      calendar month (1-12)
     * @param today      current date in Toronto timezone
     * @param windowEnd  exclusive upper bound of the scheduling window
     * @param toronto    Toronto timezone (injected to avoid hardcoding)
     * @return the placeholder date, or null if outside the window
     */
    static LocalDate nextOccurrenceInWindow(int month, LocalDate today, LocalDate windowEnd, ZoneId toronto) {
        // Try the current year first, then next year.
        for (int yearOffset = 0; yearOffset <= 1; yearOffset++) {
            LocalDate candidate = LocalDate.of(today.getYear() + yearOffset, month, PLACEHOLDER_DAY);
            if (candidate.isBefore(windowEnd) && !candidate.isBefore(today)) {
                // Ensure admin has MIN_DAYS_AHEAD to act.
                LocalDate earliest = today.plusDays(MIN_DAYS_AHEAD);
                return candidate.isBefore(earliest) ? earliest : candidate;
            }
        }
        return null;
    }
}
