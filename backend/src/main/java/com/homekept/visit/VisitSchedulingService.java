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
 * Schedules a subscriber's upcoming ROUTINE visits: the initial batch on activation, and a
 * rolling top-up thereafter as new months enter the lookahead window (#57).
 *
 * <h2>Scheduling window</h2>
 * <p>This service looks at the current month and schedules visits for the <em>next
 * {@value #LOOKAHEAD_MONTHS} months</em> (exclusive of the past). For each calendar month in
 * that window, if the subscriber's tier has a matching template, a {@link Visit} is created in
 * SCHEDULED status at noon Toronto time on the 15th of that month (a neutral mid-month
 * placeholder — admin confirms/adjusts the real date). If the 15th is in the past relative to
 * today, the visit is pushed to a week from now (so admin still gets a window to confirm
 * before it's overdue).
 *
 * <h2>Cumulative calendar</h2>
 * <p>The calendar is cumulative: a COMPLETE subscriber (the base tier) gets COMPLETE-only
 * templates; a PREMIER subscriber gets COMPLETE + PREMIER templates. The
 * {@link VisitTemplateRepository#findByMinTierIn} query handles this.
 *
 * <h2>Idempotency</h2>
 * <p>Idempotency is per-template <em>and per-occurrence</em>, not per-subscriber and not
 * unbounded: for each of the subscriber's tier-eligible templates that falls in the lookahead
 * window, a visit is created only if the subscriber doesn't already have one tied to that
 * template <em>for this same yearly occurrence</em> ({@link
 * VisitRepository#existsBySubscriberIdAndVisitTemplateIdAndTemplateOccurrenceYear}, any status,
 * keyed on {@link Visit#getTemplateOccurrenceYear()}). Templates whose occurrence already has a
 * visit are skipped; templates newly inside the window (because time has passed since the last
 * call) still get one. This keeps the Stripe webhook safe to retry (a replay finds every
 * in-window template's occurrence already has a visit and creates nothing new) while also
 * letting {@link VisitTopUpScheduler} call this method repeatedly, over months, to keep the
 * rolling window populated as it advances.
 *
 * <p><strong>This guard used to be window-scoped instead</strong> — a visit counted as
 * "already scheduled" if its {@code scheduledFor} fell inside {@code [today, windowEnd)} —
 * which only worked because a reschedule used to leave the original row in place and add a
 * replacement, so one row was always still sitting inside the window. That broke once
 * reschedule started updating the single visit row in place (V16): a visit rescheduled outside
 * the window, in either direction (pushed forward past the lookahead, or moved to a past date),
 * would make the old guard go false and this method would create a duplicate for the same
 * occurrence — the customer double-booked, and the admin list carrying exactly the extra row
 * in-place reschedule existed to remove. The guard is now keyed on {@code
 * templateOccurrenceYear} (V17 migration) instead of {@code scheduledFor}, a value a reschedule
 * MUST NEVER write (see {@link Visit#getTemplateOccurrenceYear()}'s javadoc), so moving a visit
 * cannot make its occurrence invisible to this check.
 *
 * <p>The per-occurrence (rather than unbounded "ever") scope still matters because
 * {@link VisitTemplate templates} recur <em>annually</em> — one row per (month, min tier) — so
 * an unbounded "has this subscriber ever had a visit for this template" check would permanently
 * cap every subscriber at one lifetime pass through their tier's calendar instead of a fresh
 * occurrence every year. Each occurrence has a distinct {@code templateOccurrenceYear}, so last
 * year's (possibly since-moved) visit for a template never shadows this year's occurrence, and
 * this year's never shadows next year's.
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
     * Schedules any of a subscriber's tier-eligible ROUTINE visits that fall in the rolling
     * lookahead window and don't already have a visit.
     *
     * <p>Called immediately after the subscriber transitions to ACTIVE (via {@link
     * VisitSchedulingListener}) and again daily thereafter, for every ACTIVE subscriber, by
     * {@link VisitTopUpScheduler} — both callers rely on the per-template idempotency
     * described above. The subscriber's {@code planTierId} must be set.
     *
     * @param subscriber the subscriber to schedule visits for (planTierId must be non-null)
     */
    @Transactional
    public void scheduleInitialVisits(Subscriber subscriber) {
        if (subscriber.getPlanTierId() == null) {
            log.warn("visit_scheduling_skipped subscriberId={} reason=no_plan_tier", subscriber.getId());
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
        int alreadyScheduled = 0;

        for (VisitTemplate template : templates) {
            // Find the next occurrence of this template's month in the window.
            LocalDate candidateDate = nextOccurrenceInWindow(template.getMonth(), today, windowEnd, renderZoneId);
            if (candidateDate == null) {
                continue; // month does not fall in the lookahead window
            }

            // The occurrence year is the Toronto-local calendar year of the candidate date
            // itself (which becomes scheduledFor's date component below) — matches exactly
            // how the V17 migration's backfill derives templateOccurrenceYear for existing
            // rows (EXTRACT(YEAR FROM scheduled_for AT TIME ZONE 'America/Toronto')).
            int occurrenceYear = candidateDate.getYear();

            // Per-template, per-occurrence idempotency guard (see class Javadoc
            // "Idempotency"): skip only this template if the subscriber already has a visit
            // tied to this exact yearly occurrence of it — other eligible templates newly in
            // the window still get scheduled, and next year's occurrence of this same
            // template (a different occurrenceYear) schedules again regardless of where this
            // year's visit currently sits.
            if (visitRepository.existsBySubscriberIdAndVisitTemplateIdAndTemplateOccurrenceYear(
                    subscriber.getId(), template.getId(), occurrenceYear)) {
                alreadyScheduled++;
                continue;
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
            // Set once, at creation, and never touched again — see the field's javadoc. This
            // is what lets the guard above survive the visit later being rescheduled anywhere
            // on the calendar.
            visit.setTemplateOccurrenceYear(occurrenceYear);
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

        log.info("visit_scheduling_complete subscriberId={} tier={} visits_created={} visits_already_scheduled={}",
                subscriber.getId(), subscriberTier, createdVisits.size(), alreadyScheduled);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the cumulative list of {@link PlanCode} values that a subscriber at
     * {@code tier} qualifies for. Cumulative means each tier inherits every tier below
     * it: ESSENTIAL gets its own 4 seasonal anchors, COMPLETE gets those 4 plus its own
     * 4 (8 a year), PREMIER gets all 12.
     *
     * <p>This mirrors {@code visit_template.min_tier} being a floor rather than an
     * exclusive assignment. The switch is deliberately exhaustive and unguarded by a
     * default: adding a tier to {@link PlanCode} must fail to compile here rather than
     * silently scheduling that tier zero visits.
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
     * confirm. A weekend placeholder moves to the following Monday. If the adjusted date
     * falls outside the window, returns null.
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
                LocalDate placeholder = candidate.isBefore(earliest) ? earliest : candidate;
                // Visits run on weekdays: a Saturday or Sunday placeholder moves to the Monday.
                switch (placeholder.getDayOfWeek()) {
                    case SATURDAY -> placeholder = placeholder.plusDays(2);
                    case SUNDAY -> placeholder = placeholder.plusDays(1);
                    default -> { }
                }
                return placeholder.isBefore(windowEnd) ? placeholder : null;
            }
        }
        return null;
    }
}
