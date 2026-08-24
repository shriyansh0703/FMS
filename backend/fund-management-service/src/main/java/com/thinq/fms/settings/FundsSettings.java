package com.thinq.fms.settings;

import com.thinq.fms.platform.money.Money;

import java.time.LocalTime;
import java.util.Objects;

/**
 * The tunables this module reads rather than hard-codes (Rule G1).
 *
 * <p>Each of these is a commercial or operational decision that changes without a code change, and
 * each has a rule attached that only makes sense if the value is read at the point of use — a
 * minimum restated in message copy drifts from the one the form enforces, and a cut-off compiled
 * into an arrival calculation cannot be moved when operations move it.
 *
 * @param minimumAdd    the floor on adding funds, waived only by {@link MinimumAddPolicy}
 * @param payoutCutoff  the boundary after which a withdrawal is quoted for the next working day
 * @param debitInterest the rate on an outstanding debit balance, which may be provisional or absent
 */
public record FundsSettings(Money minimumAdd, LocalTime payoutCutoff, DebitInterestRate debitInterest) {

    public FundsSettings {
        Objects.requireNonNull(minimumAdd, "minimumAdd");
        Objects.requireNonNull(payoutCutoff, "payoutCutoff");
        Objects.requireNonNull(debitInterest, "debitInterest");

        if (!minimumAdd.isPositive()) {
            throw new IllegalArgumentException(
                    "a minimum add of zero or less disables the floor silently; got " + minimumAdd);
        }
    }

    /**
     * The values in the configuration table, with the interest rate marked provisional because it
     * is a stand-in until TechExcel carries the real one.
     */
    public static FundsSettings defaults() {
        return new FundsSettings(
                Money.ofPaise(10_000L),
                LocalTime.of(15, 0),
                DebitInterestRate.provisional(new java.math.BigDecimal("18.00")));
    }
}
