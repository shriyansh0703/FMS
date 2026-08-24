package com.thinq.fms.ledgerview;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The date range a transaction list covers (Rule L6, REQ-403).
 *
 * <p><b>Thirty days by default, and the reason is specific.</b> The mandated return of unused funds
 * runs on a monthly or quarterly cycle and is among the most-queried entries, so a seven-day
 * default routinely shows an empty table for a transaction the trader knows happened. The number
 * is not a round-figure guess.
 *
 * @param from inclusive
 * @param to   inclusive
 */
public record TransactionPeriod(LocalDate from, LocalDate to) {

    /** Rule L6. */
    public static final int DEFAULT_DAYS = 30;

    /**
     * The widest window one request may cover.
     *
     * <p>Bounded by TechExcel's contract rather than by preference: its {@code Ledger} endpoint
     * takes {@code FromDate} and {@code ToDate} and offers <b>no pagination at all</b> — no offset,
     * no cursor, no row limit (OA-6, answered against the contract on 21 Aug 2026). The window is
     * therefore the only bound on response size, and an unbounded response on a money path fails
     * without warning on the busiest account.
     */
    public static final int MAX_DAYS = TechExcelWindow.MAX_WINDOW_DAYS;

    public TransactionPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("the period ends before it starts: " + from + " to " + to);
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "a period of " + days + " days exceeds the " + MAX_DAYS + "-day maximum; the "
                            + "back office's ledger has no pagination, so a wider range is walked "
                            + "in windows rather than requested in one call");
        }
    }

    /** Rule L6's default: the last thirty days, inclusive of today. */
    public static TransactionPeriod lastThirtyDays(LocalDate today) {
        return new TransactionPeriod(today.minusDays(DEFAULT_DAYS - 1L), today);
    }

    /**
     * A period from optional bounds, defaulting per Rule L6.
     *
     * <p>Either bound absent means the caller did not choose one, so the default applies. Filling
     * only the missing half would produce a window the caller never asked for and cannot predict.
     */
    public static TransactionPeriod orDefault(LocalDate from, LocalDate to, LocalDate today) {
        return from == null || to == null ? lastThirtyDays(today) : new TransactionPeriod(from, to);
    }

    public long days() {
        return ChronoUnit.DAYS.between(this.from, this.to) + 1;
    }

    /** A wider period to offer when this one is empty (Rule L7). */
    public TransactionPeriod widened() {
        LocalDate widerFrom = this.from.minusDays(Math.min(this.days() * 2, MAX_DAYS - this.days()));
        return new TransactionPeriod(widerFrom, this.to);
    }
}
