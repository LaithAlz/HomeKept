package com.homekept.notification;

import com.homekept.FakeEmailSenderConfig;
import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.TestcontainersConfiguration;
import com.homekept.booking.BookingDayOfWeek;
import com.homekept.booking.BookingStatus;
import com.homekept.booking.LeadSource;
import com.homekept.booking.PropertyType;
import com.homekept.booking.TimeOfDay;
import com.homekept.booking.WalkthroughBooking;
import com.homekept.booking.WalkthroughBookingRepository;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.visit.Visit;
import com.homekept.visit.VisitReminderNotifier;
import com.homekept.visit.VisitRepository;
import com.homekept.visit.VisitStatus;
import com.homekept.visit.VisitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReminderScheduler} (#89): dedupe, the reminder window, status
 * filtering, and recipient resolution for both the walk-through and visit reminder passes.
 *
 * <p>Runs against real Postgres via Testcontainers, with a recording {@link EmailSender} in
 * place of SendGrid (see {@link FakeEmailSenderConfig}).
 *
 * <h2>On the "missing recipient" case for visits</h2>
 * <p>{@code visit.subscriber_id} and {@code subscriber.user_id} are both FK-enforced
 * ({@code REFERENCES ... RESTRICT}), so a persisted {@link Visit} can never point at a
 * subscriber (or a subscriber at a user) that doesn't exist — there is no way to build that
 * scenario through a real row. {@link #visitReminder_missingRecipient_skipsWithoutThrowing}
 * instead calls {@link VisitReminderNotifier} directly with a subscriber id that has no
 * backing row, the same technique {@code NotificationEmailIntegrationTest.unknownSubscriber_skipsSend}
 * already uses for exactly this reason. The booking side has no such constraint ({@code email}
 * is a plain column), so its missing-recipient test runs the full scheduler pass against a
 * real persisted row.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeEmailSenderConfig.class})
class ReminderSchedulerIntegrationTest {

    @Autowired RecordingEmailSender email;
    @Autowired ReminderScheduler reminderScheduler;
    @Autowired NotificationLogRepository notificationLogRepository;
    @Autowired VisitReminderNotifier visitReminderNotifier;

    @Autowired WalkthroughBookingRepository bookingRepository;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;

    private final List<Long> createdBookingIds     = new ArrayList<>();
    private final List<Long> createdVisitIds        = new ArrayList<>();
    private final List<Long> createdSubscriberIds   = new ArrayList<>();
    private final List<Long> createdPropertyIds     = new ArrayList<>();
    private final List<Long> createdUserIds         = new ArrayList<>();

    @BeforeEach
    void resetEmail() {
        email.reset();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdVisitIds) {
            visitRepository.deleteById(id);
        }
        createdVisitIds.clear();
        for (Long id : createdSubscriberIds) {
            subscriberRepository.deleteById(id);
        }
        createdSubscriberIds.clear();
        for (Long id : createdPropertyIds) {
            propertyRepository.deleteById(id);
        }
        createdPropertyIds.clear();
        for (Long id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
        for (Long id : createdBookingIds) {
            bookingRepository.deleteById(id);
        }
        createdBookingIds.clear();
    }

    // ── Walk-through booking reminder ───────────────────────────────────────────

    @Test
    void remindWalkthroughBookings_confirmedInWindow_sendsToBookingEmailOnce() {
        String bookingEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        WalkthroughBooking booking = seedBooking(BookingStatus.CONFIRMED,
                Instant.now().plus(10, ChronoUnit.HOURS), bookingEmail);

        reminderScheduler.remindWalkthroughBookings();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(bookingEmail);
        assertThat(email.sent.get(0).subject()).isEqualTo("Reminder: your HomeKept walk-through is coming up");
        assertThat(notificationLogRepository.existsByNotificationTypeAndTargetTypeAndTargetId(
                NotificationType.WALKTHROUGH_REMINDER_24H, NotificationTargetType.WALKTHROUGH_BOOKING,
                booking.getId())).isTrue();
    }

    @Test
    void remindWalkthroughBookings_secondRun_dedupesAndSendsNothing() {
        String bookingEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        seedBooking(BookingStatus.CONFIRMED, Instant.now().plus(10, ChronoUnit.HOURS), bookingEmail);

        reminderScheduler.remindWalkthroughBookings();
        assertThat(email.sent).hasSize(1);

        email.reset();
        reminderScheduler.remindWalkthroughBookings();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void remindWalkthroughBookings_outsideWindow_isNotReminded() {
        String bookingEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        seedBooking(BookingStatus.CONFIRMED, Instant.now().plus(100, ChronoUnit.DAYS), bookingEmail);

        reminderScheduler.remindWalkthroughBookings();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void remindWalkthroughBookings_nonConfirmedStatus_isNotReminded() {
        String bookingEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        // PENDING bookings don't normally have scheduledFor set, but nothing stops the status
        // filter from being exercised directly: scheduledFor is in-window, status is not.
        seedBooking(BookingStatus.PENDING, Instant.now().plus(10, ChronoUnit.HOURS), bookingEmail);

        reminderScheduler.remindWalkthroughBookings();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void remindWalkthroughBookings_missingEmail_isRecordedAndSkippedWithoutThrowing() {
        WalkthroughBooking blankEmailBooking = seedBooking(BookingStatus.CONFIRMED,
                Instant.now().plus(10, ChronoUnit.HOURS), "");
        String validEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        seedBooking(BookingStatus.CONFIRMED, Instant.now().plus(11, ChronoUnit.HOURS), validEmail);

        // Must not throw, and the valid target in the same run must still get its reminder.
        reminderScheduler.remindWalkthroughBookings();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(validEmail);
        // The blank-email target was still claimed in the dedupe ledger (recordIfFirst runs
        // before the recipient check), so it will never be retried.
        assertThat(notificationLogRepository.existsByNotificationTypeAndTargetTypeAndTargetId(
                NotificationType.WALKTHROUGH_REMINDER_24H, NotificationTargetType.WALKTHROUGH_BOOKING,
                blankEmailBooking.getId())).isTrue();
    }

    // ── Visit reminder ───────────────────────────────────────────────────────────

    @Test
    void remindVisits_scheduledInWindow_sendsToSubscriberEmailOnce() {
        String custEmail = "reminder-visit-" + System.nanoTime() + "@test.local";
        Visit visit = seedVisit(VisitStatus.SCHEDULED, Instant.now().plus(10, ChronoUnit.HOURS), custEmail);

        reminderScheduler.remindVisits();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(custEmail);
        assertThat(email.sent.get(0).subject()).isEqualTo("Reminder: your HomeKept visit is coming up");
        assertThat(notificationLogRepository.existsByNotificationTypeAndTargetTypeAndTargetId(
                NotificationType.VISIT_REMINDER_24H, NotificationTargetType.VISIT, visit.getId())).isTrue();
    }

    @Test
    void remindVisits_secondRun_dedupesAndSendsNothing() {
        String custEmail = "reminder-visit-" + System.nanoTime() + "@test.local";
        seedVisit(VisitStatus.SCHEDULED, Instant.now().plus(10, ChronoUnit.HOURS), custEmail);

        reminderScheduler.remindVisits();
        assertThat(email.sent).hasSize(1);

        email.reset();
        reminderScheduler.remindVisits();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void remindVisits_outsideWindow_isNotReminded() {
        String custEmail = "reminder-visit-" + System.nanoTime() + "@test.local";
        seedVisit(VisitStatus.SCHEDULED, Instant.now().plus(100, ChronoUnit.DAYS), custEmail);

        reminderScheduler.remindVisits();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void remindVisits_nonScheduledStatus_isNotReminded() {
        String custEmail = "reminder-visit-" + System.nanoTime() + "@test.local";
        Visit visit = seedVisit(VisitStatus.SCHEDULED, Instant.now().plus(10, ChronoUnit.HOURS), custEmail);
        // Bypasses the state machine deliberately — test-only seeding of a non-SCHEDULED
        // status, mirroring how other integration tests seed entities directly.
        visit.setStatus(VisitStatus.CANCELLED);
        visitRepository.save(visit);

        reminderScheduler.remindVisits();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void visitReminder_missingRecipient_skipsWithoutThrowing() {
        // See class Javadoc: a persisted Visit can never have an orphaned subscriberId
        // (FK-enforced), so this calls the notifier directly — same technique
        // NotificationEmailIntegrationTest.unknownSubscriber_skipsSend uses.
        visitReminderNotifier.sendVisitReminder(1L, 999_999_999L, Instant.now().plus(10, ChronoUnit.HOURS));

        assertThat(email.sent).isEmpty();
    }

    // ── Combined pass ────────────────────────────────────────────────────────────

    @Test
    void sendDueReminders_runsBothPassesInOneInvocation() {
        String bookingEmail = "reminder-booking-" + System.nanoTime() + "@test.local";
        seedBooking(BookingStatus.CONFIRMED, Instant.now().plus(10, ChronoUnit.HOURS), bookingEmail);
        String custEmail = "reminder-visit-" + System.nanoTime() + "@test.local";
        seedVisit(VisitStatus.SCHEDULED, Instant.now().plus(10, ChronoUnit.HOURS), custEmail);

        reminderScheduler.sendDueReminders();

        assertThat(email.sent).hasSize(2);
        assertThat(email.sent.stream().map(RecordingEmailSender.Sent::toEmail))
                .containsExactlyInAnyOrder(bookingEmail, custEmail);
    }

    // ── Seeding helpers ──────────────────────────────────────────────────────────

    private WalkthroughBooking seedBooking(BookingStatus status, Instant scheduledFor, String bookingEmail) {
        WalkthroughBooking booking = new WalkthroughBooking(
                "Priya Sharma", bookingEmail, "(905) 555-0123",
                "14 Maple Ridge Crt", "Mississauga", "L5L 1A1",
                1998, "1500-2500",
                PropertyType.DETACHED, LocalDate.now().plusWeeks(1),
                TimeOfDay.AFTERNOON, Set.of(BookingDayOfWeek.WED),
                null, LeadSource.WEBSITE_DIRECT, null, Instant.now());
        booking.setStatus(status);
        booking.setScheduledFor(scheduledFor);
        WalkthroughBooking saved = bookingRepository.save(booking);
        createdBookingIds.add(saved.getId());
        return saved;
    }

    private Visit seedVisit(VisitStatus status, Instant scheduledFor, String custEmail) {
        long nano = System.nanoTime();

        User customer = userRepository.save(new User(
                custEmail, "x", "Nora", "Customer", Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customer.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Reminder Ln", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, com.homekept.property.PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        Subscriber subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        Visit visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null, scheduledFor, 120, VisitType.ROUTINE));
        visit.setStatus(status);
        visit = visitRepository.save(visit);
        createdVisitIds.add(visit.getId());
        return visit;
    }
}
