package com.homekept.notification;

import com.homekept.booking.BookingNotifier;
import com.homekept.booking.BookingService;
import com.homekept.booking.BookingService.BookingReminderTarget;
import com.homekept.visit.VisitQueryService;
import com.homekept.visit.VisitQueryService.VisitReminderTarget;
import com.homekept.visit.VisitReminderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends the two 24h-before reminder emails (#89): a CONFIRMED walk-through booking reminder
 * and a SCHEDULED visit reminder.
 *
 * <h2>Cadence</h2>
 * <p>Runs hourly ({@link #sendDueReminders}). Per arch doc §5.3 MVP guidance: plain
 * {@code @Scheduled}, no durable job queue yet (Post-MVP: Jobrunr).
 *
 * <h2>Window</h2>
 * <p>Each run looks for targets whose {@code scheduledFor} falls in
 * {@code [now, now + 24h]}. A target enters this window the moment it is 24h away and stays
 * in it on every subsequent hourly tick until it happens — so as long as the app runs at
 * least once during that 24h span, the reminder fires. This is deliberately a full 24h band
 * rather than a tight "23-25h before" one: overlap across ticks is safe (the dedupe ledger
 * sends each target at most once), and a wider window tolerates scheduler downtime instead of
 * silently missing a reminder that fell in a narrow gap.
 *
 * <h2>Dedupe</h2>
 * <p>{@link NotificationLogService#recordIfFirst} is called before any send attempt and is
 * the gate: a target is claimed (recorded) exactly once, whether or not a valid recipient is
 * later found for it — this guarantees at most one attempt per target, ever, matching the
 * V10 migration's unique constraint. A target with no attempt recorded (e.g. the process
 * crashed between the query and this call) is simply picked up again on the next tick.
 *
 * <h2>Domain boundaries</h2>
 * <p>Reads cross-domain via {@link BookingService} and {@link VisitQueryService} — never
 * their repositories or entities, only the narrow {@code *ReminderTarget} projections.
 * Sending is delegated back to each domain's own notifier ({@link BookingNotifier},
 * {@link VisitReminderNotifier}), which already knows how to reach {@link EmailSender}.
 */
@Service
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    /** How far ahead of "now" to look for due reminders. See class Javadoc "Window". */
    static final Duration REMINDER_WINDOW = Duration.ofHours(24);

    private final BookingService bookingService;
    private final BookingNotifier bookingNotifier;
    private final VisitQueryService visitQueryService;
    private final VisitReminderNotifier visitReminderNotifier;
    private final NotificationLogService notificationLogService;

    public ReminderScheduler(BookingService bookingService,
                             BookingNotifier bookingNotifier,
                             VisitQueryService visitQueryService,
                             VisitReminderNotifier visitReminderNotifier,
                             NotificationLogService notificationLogService) {
        this.bookingService = bookingService;
        this.bookingNotifier = bookingNotifier;
        this.visitQueryService = visitQueryService;
        this.visitReminderNotifier = visitReminderNotifier;
        this.notificationLogService = notificationLogService;
    }

    /**
     * Runs hourly (starting 1 minute after app startup). Sends the walk-through reminder
     * pass then the visit reminder pass. Each target is handled independently — a missing
     * recipient (or any other per-target issue) never blocks the rest of the run.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void sendDueReminders() {
        remindWalkthroughBookings();
        remindVisits();
    }

    /** Package-private so tests can trigger just this pass without waiting on the cron tick. */
    void remindWalkthroughBookings() {
        Instant now = Instant.now();
        List<BookingReminderTarget> due = bookingService.findConfirmedInWindow(now, now.plus(REMINDER_WINDOW));
        for (BookingReminderTarget target : due) {
            boolean first = notificationLogService.recordIfFirst(
                    NotificationType.WALKTHROUGH_REMINDER_24H,
                    NotificationTargetType.WALKTHROUGH_BOOKING,
                    target.bookingId());
            if (!first) {
                continue;
            }
            if (target.email() == null || target.email().isBlank()) {
                log.debug("walkthrough_reminder_skipped_no_email bookingId={}", target.bookingId());
                continue;
            }
            bookingNotifier.sendBookingReminder(target.bookingId(), target.email(), target.fullName(),
                    target.streetAddress(), target.city(), target.scheduledFor());
        }
    }

    /** Package-private so tests can trigger just this pass without waiting on the cron tick. */
    void remindVisits() {
        Instant now = Instant.now();
        List<VisitReminderTarget> due = visitQueryService.findScheduledInWindow(now, now.plus(REMINDER_WINDOW));
        for (VisitReminderTarget target : due) {
            boolean first = notificationLogService.recordIfFirst(
                    NotificationType.VISIT_REMINDER_24H,
                    NotificationTargetType.VISIT,
                    target.visitId());
            if (!first) {
                continue;
            }
            // A missing recipient (subscriber/user not found) is logged and skipped inside
            // the notifier itself — mirrors DefaultVisitReportNotifier.
            visitReminderNotifier.sendVisitReminder(target.visitId(), target.subscriberId(), target.scheduledFor());
        }
    }
}
