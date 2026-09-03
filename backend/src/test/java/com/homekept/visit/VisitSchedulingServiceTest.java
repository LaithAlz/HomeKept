package com.homekept.visit;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class VisitSchedulingServiceTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Test
    void placeholder_onAWeekday_staysOnThe15th() {
        LocalDate today = LocalDate.of(2026, 9, 3);
        LocalDate date = VisitSchedulingService.nextOccurrenceInWindow(10, today, today.plusMonths(4), TORONTO);
        assertThat(date).isEqualTo(LocalDate.of(2026, 10, 15)); // a Thursday
    }

    @Test
    void placeholder_onASunday_movesToMonday() {
        LocalDate today = LocalDate.of(2026, 9, 3);
        LocalDate date = VisitSchedulingService.nextOccurrenceInWindow(11, today, today.plusMonths(4), TORONTO);
        assertThat(date).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(date.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void placeholder_onASaturday_movesToMonday() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        LocalDate date = VisitSchedulingService.nextOccurrenceInWindow(8, today, today.plusMonths(4), TORONTO);
        assertThat(date).isEqualTo(LocalDate.of(2026, 8, 17)); // Aug 15 2026 is a Saturday
    }

    @Test
    void placeholder_withinSevenDays_isPushedOutAndStillLandsOnAWeekday() {
        LocalDate today = LocalDate.of(2026, 9, 10); // Sep 15 is 5 days away -> Sep 17 (Thursday)
        LocalDate date = VisitSchedulingService.nextOccurrenceInWindow(9, today, today.plusMonths(4), TORONTO);
        assertThat(date).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(date.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }
}
