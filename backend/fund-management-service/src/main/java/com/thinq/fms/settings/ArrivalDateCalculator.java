package com.thinq.fms.settings;

import com.thinq.fms.platform.error.CalendarUnavailableException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * When a withdrawal requested now is expected to arrive (REQ-707, Rule W5).
 *
 * <p>Rule W5's position is that a fixed promise which is sometimes wrong is worse than a computed
 * date which is sometimes longer, so nothing here rounds up to a comfortable answer or falls back to
 * "within 24 hours". Each factor that defers the date is recorded and returned, because the rule
 * requires each one named to the trader rather than folded into a later date with no explanation.
 *
 * <p><b>The calendar is a dependency, not a detail.</b> Working days decide the answer, and when the
 * calendar cannot be read the honest result is {@link ArrivalQuote#unavailable()} — REQ-303 forbids
 * presenting a default as though it were computed.
 */
public final class ArrivalDateCalculator {

    /** Whether a given date is a working day for settlement. */
    @FunctionalInterface
    public interface TradingCalendar {
        boolean isWorkingDay(LocalDate date);
    }

    /** How far forward to look for a working day before giving up rather than looping. */
    private static final int MAX_WORKING_DAY_SEARCH = 14;

    private final LocalTime payoutCutoff;
    private final ZoneId zone;
    private final TradingCalendar calendar;

    public ArrivalDateCalculator(LocalTime payoutCutoff, ZoneId zone, TradingCalendar calendar) {
        this.payoutCutoff = Objects.requireNonNull(payoutCutoff, "payoutCutoff");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    /**
     * Quote the arrival for a request made at this instant.
     *
     * @param tradedToday      settlement of today's trading has to complete first (Rule W5)
     * @param orderOutstanding an order is outstanding against the funds (Rule W5)
     */
    public ArrivalQuote quoteFor(Instant requestedAt, boolean tradedToday, boolean orderOutstanding) {
        Objects.requireNonNull(requestedAt, "requestedAt");

        var local = requestedAt.atZone(this.zone);
        LocalDate candidate = local.toLocalDate();
        List<ArrivalQuote.DeferralCause> causes = new ArrayList<>();

        // After the cut-off the request misses today's run, so the earliest carrier is tomorrow's.
        if (!local.toLocalTime().isBefore(this.payoutCutoff)) {
            causes.add(ArrivalQuote.DeferralCause.AFTER_CUTOFF);
            candidate = candidate.plusDays(1);
        }
        if (tradedToday) {
            causes.add(ArrivalQuote.DeferralCause.TRADED_TODAY);
            candidate = candidate.plusDays(1);
        }
        if (orderOutstanding) {
            causes.add(ArrivalQuote.DeferralCause.ORDER_OUTSTANDING);
            candidate = candidate.plusDays(1);
        }

        try {
            LocalDate working = candidate;
            boolean movedForACalendarDay = false;
            for (int i = 0; i < MAX_WORKING_DAY_SEARCH; i++) {
                if (this.calendar.isWorkingDay(working)) {
                    if (movedForACalendarDay) {
                        causes.add(ArrivalQuote.DeferralCause.NON_WORKING_DAY);
                    }
                    return ArrivalQuote.on(working, causes);
                }
                working = working.plusDays(1);
                movedForACalendarDay = true;
            }
            // A fortnight of closures is not a real calendar. Reporting it as uncomputable is
            // better than quoting a date arrived at by a loop that ran out.
            return ArrivalQuote.unavailable();
        } catch (CalendarUnavailableException e) {
            // OA-5, and REQ-303's rule: say the date is unavailable rather than defaulting one.
            return ArrivalQuote.unavailable();
        }
    }
}
