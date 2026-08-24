package com.thinq.fms.settings;

import com.thinq.fms.platform.error.CalendarUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EB-6's calendar, supplied by configuration rather than waiting for a nominated source. */
class ConfiguredTradingCalendarTest {

    private static final LocalDate DIWALI = LocalDate.of(2026, 11, 8);   // a Sunday-adjacent example
    private static final LocalDate TUESDAY = LocalDate.of(2026, 8, 25);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 22);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 23);

    @Test
    @DisplayName("an unconfigured calendar refuses to answer rather than assuming every weekday works")
    void anUnconfiguredCalendarRefuses() {
        // The decision this class exists to make. A calendar with no holidays is indistinguishable
        // from one nobody configured, and it would quote arrival dates falling on Diwali —
        // confidently, with no error anywhere. OA-5 requires the absence to surface.
        var calendar = new ConfiguredTradingCalendar(Set.of());

        assertThat(calendar.isConfigured()).isFalse();
        assertThatThrownBy(() -> calendar.isWorkingDay(TUESDAY))
                .isInstanceOf(CalendarUnavailableException.class)
                .hasMessageContaining("fms.calendar.holidays");
    }

    @Test
    @DisplayName("a configured holiday is not a working day")
    void aConfiguredHolidayIsNotAWorkingDay() {
        var calendar = new ConfiguredTradingCalendar(Set.of(DIWALI));

        assertThat(calendar.isWorkingDay(DIWALI)).isFalse();
        assertThat(calendar.isWorkingDay(TUESDAY)).isTrue();
    }

    @Test
    @DisplayName("weekends are never working days, and are not configurable")
    void weekendsAreNeverWorkingDays() {
        // Not a settlement question — a property of the week. Making it configurable would invite an
        // environment that silently settles on a Sunday.
        var calendar = new ConfiguredTradingCalendar(Set.of(DIWALI));

        assertThat(calendar.isWorkingDay(SATURDAY)).isFalse();
        assertThat(calendar.isWorkingDay(SUNDAY)).isFalse();
    }

    @Test
    @DisplayName("an unconfigured calendar makes the arrival date uncomputable, not guessed")
    void anUnconfiguredCalendarMakesTheDateUncomputable() {
        // End to end with the calculator: REQ-303 requires this reported rather than defaulted.
        var calculator = new ArrivalDateCalculator(
                java.time.LocalTime.of(15, 0), java.time.ZoneOffset.UTC,
                new ConfiguredTradingCalendar(Set.of()));

        ArrivalQuote quote = calculator.quoteFor(
                TUESDAY.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC), false, false);

        assertThat(quote.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("with holidays configured, the calculator skips them and names the cause")
    void withHolidaysConfiguredTheCalculatorSkipsThem() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        var calculator = new ArrivalDateCalculator(
                java.time.LocalTime.of(15, 0), java.time.ZoneOffset.UTC,
                new ConfiguredTradingCalendar(Set.of(monday)));

        ArrivalQuote quote = calculator.quoteFor(
                monday.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC), false, false);

        assertThat(quote.expectedOn()).contains(LocalDate.of(2026, 8, 25));
        assertThat(quote.deferredBy()).contains(ArrivalQuote.DeferralCause.NON_WORKING_DAY);
    }
}
