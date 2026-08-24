package com.thinq.fms.movement.payout;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Objects;

/**
 * When unused funds must be returned whether or not the trader asked (REQ-307, Rule W8).
 *
 * <p>The regulator sets these dates; the trader chooses only the frequency. This computes the next
 * one so the funds view can state it before it happens — an unrequested debit that arrives with no
 * warning is indistinguishable from an error, and generates the support call that warning it would
 * have prevented.
 */
public final class MandatedReturnSchedule {

    /** How often the return runs. The trader's choice, presented with the date it produces. */
    public enum Frequency {
        MONTHLY,
        QUARTERLY
    }

    /** Quarter ends on the Indian financial calendar. */
    private static final List<Month> QUARTER_ENDS =
            List.of(Month.JUNE, Month.SEPTEMBER, Month.DECEMBER, Month.MARCH);

    private MandatedReturnSchedule() {
    }

    /**
     * The next mandated return on or after {@code from}.
     *
     * <p>Always the last day of the qualifying month, and always a date this method computes rather
     * than one a caller passes in — REQ-307 requires the date stated before it occurs, and a date
     * assembled at the call site is one that can differ between the banner and the run.
     */
    public static LocalDate nextAfter(LocalDate from, Frequency frequency) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(frequency, "frequency");

        LocalDate candidate = endOfMonth(from);
        if (frequency == Frequency.MONTHLY) {
            return !candidate.isBefore(from) ? candidate : endOfMonth(from.plusMonths(1));
        }

        for (int i = 0; i < 12; i++) {
            LocalDate month = from.plusMonths(i);
            if (QUARTER_ENDS.contains(month.getMonth())) {
                LocalDate end = endOfMonth(month);
                if (!end.isBefore(from)) {
                    return end;
                }
            }
        }
        throw new IllegalStateException("no quarter end within a year of " + from);
    }

    /**
     * Whether a trader's own open request and a mandated return fall on the same date.
     *
     * <p>Rule W9: they settle from the same available balance in one payout. Sending both would send
     * the same money twice, and no amount of reconciliation afterwards recovers a trader's trust in
     * the figure.
     */
    public static boolean collidesWithOpenRequest(LocalDate mandatedOn, LocalDate requestRunsOn) {
        Objects.requireNonNull(mandatedOn, "mandatedOn");
        Objects.requireNonNull(requestRunsOn, "requestRunsOn");
        return mandatedOn.equals(requestRunsOn);
    }

    private static LocalDate endOfMonth(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }
}
