package com.thinq.fms.settings;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * When a withdrawal is expected to arrive, and what pushed it out (REQ-707, REQ-303, Rule W5).
 *
 * <p><b>Three things this carries that a {@code LocalDate} cannot.</b> Rule W5 requires each factor
 * that defers arrival to be named, REQ-707 forbids presenting a generic duration in place of a
 * computed date, and REQ-303 requires an uncomputable date to be reported as such rather than
 * defaulted. A bare date silently satisfies none of them: it cannot say why it is what it is, and an
 * absent one is indistinguishable from a guess.
 *
 * @param expectedOn the computed date, absent when it could not be computed
 * @param deferredBy the factors that pushed the date out, in the order they applied. Empty on the
 *     fastest path, which is itself information — nothing delayed this
 */
public record ArrivalQuote(Optional<LocalDate> expectedOn, List<DeferralCause> deferredBy) {

    /** Why an arrival is later than the fastest case. Each is named to the trader (Rule W5). */
    public enum DeferralCause {
        /** Requested after the day's cut-off, so the run that carries it is tomorrow's. */
        AFTER_CUTOFF,
        /** The next day is not a working day on the trading calendar. */
        NON_WORKING_DAY,
        /** Settlement of today's trading has to complete first. */
        TRADED_TODAY,
        /** An order is outstanding against the funds. */
        ORDER_OUTSTANDING
    }

    public ArrivalQuote {
        Objects.requireNonNull(expectedOn, "expectedOn");
        deferredBy = List.copyOf(Objects.requireNonNull(deferredBy, "deferredBy"));
    }

    public static ArrivalQuote on(LocalDate date, List<DeferralCause> causes) {
        return new ArrivalQuote(Optional.of(Objects.requireNonNull(date)), causes);
    }

    /**
     * The date could not be computed — most often because the trading calendar is unavailable.
     *
     * <p>REQ-303 and REQ-707 both require this stated rather than filled in. A trader plans around
     * a date, and a guessed one that proves wrong costs more than an honest absence.
     */
    public static ArrivalQuote unavailable() {
        return new ArrivalQuote(Optional.empty(), List.of());
    }

    public boolean isAvailable() {
        return this.expectedOn.isPresent();
    }
}
