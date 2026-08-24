package com.thinq.fms.settings;

import com.thinq.fms.platform.error.CalendarUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-707, REQ-303 and Rule W5 — a computed date, its causes, and an honest absence. */
class ArrivalDateCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime CUTOFF = LocalTime.of(15, 0);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 21);

    /** Weekdays only, which is enough to exercise the working-day search. */
    private static final ArrivalDateCalculator.TradingCalendar WEEKDAYS =
            date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;

    private ArrivalDateCalculator calculator(ArrivalDateCalculator.TradingCalendar calendar) {
        return new ArrivalDateCalculator(CUTOFF, IST, calendar);
    }

    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(IST).toInstant();
    }

    @Test
    @DisplayName("before the cut-off on a working day, the money arrives the same day and nothing is deferred")
    void beforeTheCutoffArrivesSameDay() {
        ArrivalQuote quote = calculator(WEEKDAYS).quoteFor(at(FRIDAY, 14, 59), false, false);

        assertThat(quote.isAvailable()).isTrue();
        assertThat(quote.expectedOn()).contains(FRIDAY);
        assertThat(quote.deferredBy()).as("nothing delayed this, which is itself information").isEmpty();
    }

    @Test
    @DisplayName("at the cut-off exactly, the request has missed today's run")
    void atTheCutoffTheRequestHasMissedTodaysRun() {
        // The boundary decides whether a 3pm request goes today or tomorrow. Getting it wrong either
        // promises a date the run cannot meet or defers one it could have.
        ArrivalQuote quote = calculator(WEEKDAYS).quoteFor(at(FRIDAY, 15, 0), false, false);

        assertThat(quote.expectedOn()).contains(FRIDAY.plusDays(3)); // Monday
        assertThat(quote.deferredBy()).contains(ArrivalQuote.DeferralCause.AFTER_CUTOFF);
    }

    @Test
    @DisplayName("a weekend is skipped and named as a cause")
    void aWeekendIsSkippedAndNamed() {
        // Rule W5 requires each deferring factor named. A date that silently jumps to Monday leaves
        // the trader wondering whether something went wrong.
        ArrivalQuote quote = calculator(WEEKDAYS).quoteFor(at(FRIDAY, 16, 0), false, false);

        assertThat(quote.expectedOn()).contains(LocalDate.of(2026, 8, 24));
        assertThat(quote.deferredBy()).contains(
                ArrivalQuote.DeferralCause.AFTER_CUTOFF, ArrivalQuote.DeferralCause.NON_WORKING_DAY);
    }

    @Test
    @DisplayName("trading today and an outstanding order each defer, and each is named")
    void tradingAndOutstandingOrdersEachDefer() {
        ArrivalQuote quote = calculator(date -> true).quoteFor(at(FRIDAY, 10, 0), true, true);

        assertThat(quote.expectedOn()).contains(FRIDAY.plusDays(2));
        assertThat(quote.deferredBy()).containsExactly(
                ArrivalQuote.DeferralCause.TRADED_TODAY,
                ArrivalQuote.DeferralCause.ORDER_OUTSTANDING);
    }

    @Test
    @DisplayName("an unavailable calendar reports no date rather than defaulting one")
    void anUnavailableCalendarReportsNoDate() {
        // REQ-303 and REQ-707. A trader plans around a date, and a guessed one that proves wrong
        // costs more than an honest absence.
        ArrivalQuote quote = calculator(date -> {
            throw new CalendarUnavailableException("settlement calendar not loaded");
        }).quoteFor(at(FRIDAY, 10, 0), false, false);

        assertThat(quote.isAvailable()).isFalse();
        assertThat(quote.expectedOn()).isEmpty();
    }

    @Test
    @DisplayName("a calendar with no working day at all gives up rather than quoting a looped date")
    void aCalendarWithNoWorkingDayGivesUp() {
        ArrivalQuote quote = calculator(date -> false).quoteFor(at(FRIDAY, 10, 0), false, false);

        assertThat(quote.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("the cut-off is read in the account's zone, not the server's")
    void theCutoffIsReadInTheAccountsZone() {
        // 14:00 UTC is 19:30 IST — past the cut-off. A calculator using the server's zone would
        // quote same-day for a request that has plainly missed the run.
        Instant afterCutoffInIst = LocalDateTime.of(FRIDAY, LocalTime.of(14, 0))
                .atZone(ZoneOffset.UTC).toInstant();

        ArrivalQuote quote = calculator(date -> true).quoteFor(afterCutoffInIst, false, false);

        assertThat(quote.deferredBy()).contains(ArrivalQuote.DeferralCause.AFTER_CUTOFF);
    }
}
