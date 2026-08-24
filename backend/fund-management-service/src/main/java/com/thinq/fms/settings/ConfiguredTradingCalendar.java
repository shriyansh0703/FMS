package com.thinq.fms.settings;

import com.thinq.fms.platform.error.CalendarUnavailableException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Working days from configuration, rather than from a source nobody has nominated (EB-6).
 *
 * <p><b>What EB-6 actually blocks.</b> It blocks the decision about where the holiday list comes
 * from — an exchange feed, an operations spreadsheet, the back office. It does not block having a
 * calendar: the shape of the answer is the same whichever source wins, and a deployment can supply
 * the dates today. Waiting for the source before writing any calendar left
 * {@code ArrivalDateQuoter} unimplemented, which left the whole withdrawal path unable to start.
 *
 * <p><b>Weekends are hard-coded; holidays are not.</b> Saturday and Sunday are not a settlement
 * question in India, they are a property of the week, and treating them as configuration would
 * invite an environment that silently settles on a Sunday. Exchange holidays move every year and
 * belong in configuration.
 *
 * <p><b>An empty holiday list is refused, loudly.</b> That is the whole design decision here. A
 * calendar configured with no holidays is indistinguishable from one nobody configured, and it
 * would quote arrival dates that fall on Diwali — confidently, with no error anywhere. Rule W5
 * prefers a computed date that is sometimes longer to a promise that is sometimes wrong, and OA-5
 * requires the calendar's absence to surface rather than be assumed away. So an unconfigured
 * calendar throws {@link CalendarUnavailableException}, which {@code ArrivalDateCalculator} turns
 * into "the date cannot be computed" rather than a guess.
 */
public final class ConfiguredTradingCalendar implements ArrivalDateCalculator.TradingCalendar {

    private final Set<LocalDate> holidays;
    private final boolean configured;

    /**
     * @param holidays the exchange holidays for the period being quoted. Empty means unconfigured,
     *     which is a state this class reports rather than papers over
     */
    public ConfiguredTradingCalendar(Set<LocalDate> holidays) {
        Objects.requireNonNull(holidays, "holidays");
        this.holidays = Set.copyOf(holidays);
        this.configured = !holidays.isEmpty();
    }

    @Override
    public boolean isWorkingDay(LocalDate date) {
        Objects.requireNonNull(date, "date");

        if (!this.configured) {
            throw new CalendarUnavailableException(
                    "no trading holidays are configured, so this calendar cannot distinguish a "
                            + "settlement holiday from a working day. Configure fms.calendar.holidays, "
                            + "or accept that arrival dates are reported as uncomputable (OA-5).");
        }

        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !this.holidays.contains(date);
    }

    /** Whether a holiday list was supplied at all. */
    public boolean isConfigured() {
        return this.configured;
    }
}
