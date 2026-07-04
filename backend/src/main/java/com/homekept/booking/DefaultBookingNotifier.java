package com.homekept.booking;

import com.homekept.notification.EmailSender;
import com.homekept.notification.EmailTemplates;
import com.homekept.notification.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sends the booking-confirmation email when a walk-through booking is submitted, via
 * {@link EmailSender} (SendGrid).
 *
 * <p>Unlike the subscriber-facing notifiers, no recipient-resolver lookup is needed here —
 * the prospective subscriber's name and email live directly on the {@link WalkthroughBooking}
 * (no account exists yet at this stage).
 *
 * <p>No PII is logged — only the booking ID (internal) is referenced, per CLAUDE.md.
 */
@Component
public class DefaultBookingNotifier implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultBookingNotifier.class);

    private static final DateTimeFormatter WEEK_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private static final Map<BookingDayOfWeek, String> DAY_NAMES = new EnumMap<>(BookingDayOfWeek.class);

    static {
        DAY_NAMES.put(BookingDayOfWeek.MON, "Monday");
        DAY_NAMES.put(BookingDayOfWeek.TUE, "Tuesday");
        DAY_NAMES.put(BookingDayOfWeek.WED, "Wednesday");
        DAY_NAMES.put(BookingDayOfWeek.THU, "Thursday");
        DAY_NAMES.put(BookingDayOfWeek.FRI, "Friday");
        DAY_NAMES.put(BookingDayOfWeek.SAT, "Saturday");
        DAY_NAMES.put(BookingDayOfWeek.SUN, "Sunday");
    }

    private final EmailSender emailSender;

    public DefaultBookingNotifier(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void sendBookingConfirmation(WalkthroughBooking booking) {
        String firstName = firstNameOf(booking.getFullName());
        String weekLabel = booking.getPreferredWeek() != null
                ? booking.getPreferredWeek().format(WEEK_FORMAT)
                : "";
        String timeOfDayLabel = booking.getTimeOfDay() != null
                ? booking.getTimeOfDay().name().toLowerCase(Locale.ENGLISH)
                : "";
        String dayPreferencesLabel = formatDayPreferences(booking.getDayPreferences());
        String addressLabel = formatAddress(booking.getStreetAddress(), booking.getCity());

        RenderedEmail rendered = EmailTemplates.bookingConfirmation(
                firstName, weekLabel, timeOfDayLabel, dayPreferencesLabel, addressLabel);
        emailSender.send(booking.getEmail(), firstName, rendered.subject(), rendered.htmlBody());
        log.info("booking_confirmation_email_dispatched bookingId={}", booking.getId());
    }

    /** Splits the stored full name on the first space, mirroring BookingService's activation split. */
    private static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String trimmed = fullName.trim();
        int spaceIndex = trimmed.indexOf(' ');
        return spaceIndex < 0 ? trimmed : trimmed.substring(0, spaceIndex);
    }

    private static String formatAddress(String streetAddress, String city) {
        boolean hasStreet = streetAddress != null && !streetAddress.isBlank();
        boolean hasCity = city != null && !city.isBlank();
        if (hasStreet && hasCity) {
            return streetAddress + ", " + city;
        }
        return hasStreet ? streetAddress : (hasCity ? city : "");
    }

    /** Renders day-of-week preferences in Mon-Sun order, e.g. "Wednesday and Thursday". */
    private static String formatDayPreferences(Set<BookingDayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>();
        for (BookingDayOfWeek day : BookingDayOfWeek.values()) {
            if (days.contains(day)) {
                ordered.add(DAY_NAMES.get(day));
            }
        }
        if (ordered.size() == 1) {
            return ordered.get(0);
        }
        String allButLast = String.join(", ", ordered.subList(0, ordered.size() - 1));
        return allButLast + " and " + ordered.get(ordered.size() - 1);
    }
}
