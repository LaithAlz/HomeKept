package com.homekept.notification;

import com.homekept.FakeEmailSenderConfig;
import com.homekept.FakeEmailSenderConfig.RecordingEmailSender;
import com.homekept.TestcontainersConfiguration;
import com.homekept.booking.BookingDayOfWeek;
import com.homekept.booking.BookingNotifier;
import com.homekept.booking.LeadSource;
import com.homekept.booking.TimeOfDay;
import com.homekept.booking.WalkthroughBooking;
import com.homekept.identity.PasswordResetNotifier;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.ActivationNotifier;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.PaymentFailedNotifier;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.subscription.SubscriptionCancelledNotifier;
import com.homekept.subscription.SubscriptionStartedNotifier;
import com.homekept.visit.Visit;
import com.homekept.visit.VisitReportNotifier;
import com.homekept.visit.VisitRepository;
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
 * Verifies the transactional-email wiring end-to-end through the real notifier beans and a
 * recording {@link EmailSender} (no SendGrid). Confirms each notifier resolves the right
 * recipient and selects the right template, and that an unresolved recipient is skipped.
 *
 * <p>Runs against real Postgres via Testcontainers.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeEmailSenderConfig.class})
class NotificationEmailIntegrationTest {

    @Autowired RecordingEmailSender email;
    @Autowired SubscriptionStartedNotifier subscriptionStartedNotifier;
    @Autowired VisitReportNotifier visitReportNotifier;
    @Autowired PaymentFailedNotifier paymentFailedNotifier;
    @Autowired SubscriptionCancelledNotifier subscriptionCancelledNotifier;
    @Autowired ActivationNotifier activationNotifier;
    @Autowired BookingNotifier bookingNotifier;
    @Autowired PasswordResetNotifier passwordResetNotifier;

    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;

    private final List<Long> createdSubscriberIds = new ArrayList<>();
    private final List<Long> createdPropertyIds   = new ArrayList<>();
    private final List<Long> createdUserIds        = new ArrayList<>();

    private String customerEmail;
    private Subscriber subscriber;
    private Visit visit;

    @BeforeEach
    void seed() {
        email.reset();
        long nano = System.nanoTime();

        customerEmail = "notif-cust-" + nano + "@test.local";
        User customer = userRepository.save(new User(
                customerEmail, "x", "Nora", "Customer",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customer.getId());

        Property property = propertyRepository.save(new Property(
                nano + " Notify Ln", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
        createdPropertyIds.add(property.getId());

        subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(),
                SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        createdSubscriberIds.add(subscriber.getId());

        visit = visitRepository.save(new Visit(
                subscriber.getId(), property.getId(), null,
                Instant.now().plus(7, ChronoUnit.DAYS), 120, VisitType.ROUTINE));
    }

    @AfterEach
    void tearDown() {
        for (Long subId : createdSubscriberIds) {
            visitRepository.findById(visit.getId()).ifPresent(v -> visitRepository.deleteById(v.getId()));
            subscriberRepository.deleteById(subId);
        }
        createdSubscriberIds.clear();
        for (Long id : createdPropertyIds) propertyRepository.deleteById(id);
        createdPropertyIds.clear();
        for (Long id : createdUserIds) userRepository.deleteById(id);
        createdUserIds.clear();
    }

    @Test
    void welcome_sendsToSubscriberEmail() {
        subscriptionStartedNotifier.onSubscriptionStarted(subscriber.getId(), "COMPLETE");

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(customerEmail);
        assertThat(email.sent.get(0).subject()).isEqualTo("Welcome to HomeKept");
    }

    @Test
    void visitReport_sendsToSubscriberEmail() {
        visitReportNotifier.sendVisitReport(visit);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(customerEmail);
        assertThat(email.sent.get(0).subject()).isEqualTo("Your HomeKept visit is complete");
    }

    @Test
    void paymentFailed_sendsToSubscriberEmail() {
        paymentFailedNotifier.onPaymentFailed(subscriber.getId());

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(customerEmail);
        assertThat(email.sent.get(0).subject())
                .isEqualTo("Action needed: your HomeKept payment didn't go through");
    }

    @Test
    void cancelled_sendsToSubscriberEmail() {
        subscriptionCancelledNotifier.onSubscriptionCancelled(subscriber.getId());

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).subject()).isEqualTo("Your HomeKept membership has been cancelled");
    }

    @Test
    void activation_sendsToGivenEmailWithTokenLink() {
        activationNotifier.sendActivationLink("invitee@test.local", "tok-123", 99L);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo("invitee@test.local");
        assertThat(email.sent.get(0).subject()).isEqualTo("Activate your HomeKept membership");
        assertThat(email.sent.get(0).htmlBody()).contains("/activate?token=tok-123");
    }

    @Test
    void passwordReset_sendsToGivenEmailWithTokenLink() {
        passwordResetNotifier.sendResetLink("resetme@test.local", "Nora", "tok-456", 42L);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo("resetme@test.local");
        assertThat(email.sent.get(0).subject()).isEqualTo("Reset your HomeKept password");
        assertThat(email.sent.get(0).htmlBody()).contains("/reset-password?token=tok-456");
    }

    @Test
    void unknownSubscriber_skipsSend() {
        subscriptionStartedNotifier.onSubscriptionStarted(999_999_999L, "COMPLETE");
        assertThat(email.sent).isEmpty();
    }

    @Test
    void bookingConfirmation_sendsToBookingEmailWithSubmittedDetails() {
        String bookingEmail = "notif-booking-" + System.nanoTime() + "@test.local";
        WalkthroughBooking booking = new WalkthroughBooking(
                "Priya Sharma", bookingEmail, "(905) 555-0123",
                "14 Maple Ridge Crt", "Mississauga", "L5L 1A1",
                1998, "1500-2500",
                com.homekept.booking.PropertyType.DETACHED, LocalDate.of(2026, 7, 6),
                TimeOfDay.AFTERNOON, Set.of(BookingDayOfWeek.WED, BookingDayOfWeek.THU),
                "Friendly dog in the yard", LeadSource.WEBSITE_DIRECT, null, Instant.now());

        bookingNotifier.sendBookingConfirmation(booking);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).toEmail()).isEqualTo(bookingEmail);
        assertThat(email.sent.get(0).subject()).isEqualTo("Your HomeKept walk-through request");
        assertThat(email.sent.get(0).htmlBody())
                .contains("Hi Priya,")
                .contains("July 6, 2026")
                .contains("afternoon")
                .contains("Wednesday and Thursday")
                .contains("14 Maple Ridge Crt, Mississauga");
    }

    @Test
    void bookingConfirmation_noDayPreferences_omitsThemFromBody() {
        String bookingEmail = "notif-booking-" + System.nanoTime() + "@test.local";
        WalkthroughBooking booking = new WalkthroughBooking(
                "Alex Chen", bookingEmail, "(905) 555-0000",
                "1 Main St", "Oakville", "L6J 1A1",
                null, null,
                com.homekept.booking.PropertyType.SEMI, LocalDate.of(2026, 7, 13),
                TimeOfDay.MORNING, Set.of(),
                null, LeadSource.WEBSITE_DIRECT, null, Instant.now());

        bookingNotifier.sendBookingConfirmation(booking);

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).htmlBody()).doesNotContain(", on ");
    }
}
