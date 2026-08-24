package com.thinq.fms.settings;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * The rate charged daily on an outstanding debit balance (REQ-708).
 *
 * <p><b>Three states, and the difference between them is the requirement.</b> A configured rate, a
 * provisional stand-in, and no rate at all are not interchangeable: REQ-708 forbids sending any
 * message quoting a rate while it is a stand-in, and requires the debt still be stated when no rate
 * is available rather than presented as static. A plain {@code BigDecimal} cannot express either
 * rule, so it is not one.
 *
 * <p>The configured value is currently a placeholder — 18% per annum — until the real figure is set
 * up in the TechExcel back office and read from there. That is why {@link #provisional} exists and
 * why it defaults to the safe answer.
 */
public record DebitInterestRate(Optional<BigDecimal> annualPercent, boolean provisional) {

    public DebitInterestRate {
        Objects.requireNonNull(annualPercent, "annualPercent");
        annualPercent.ifPresent(rate -> {
            if (rate.signum() < 0) {
                throw new IllegalArgumentException("a debit interest rate is not negative; got " + rate);
            }
        });
    }

    /** A rate confirmed in the back office, safe to quote to a trader. */
    public static DebitInterestRate configured(BigDecimal annualPercent) {
        return new DebitInterestRate(Optional.of(Objects.requireNonNull(annualPercent)), false);
    }

    /**
     * A stand-in until TechExcel is set up.
     *
     * <p>Quotable in a non-production display where it is marked provisional, and never quotable in
     * a message — a trader who is told 18% and charged something else has been misinformed by us,
     * not by a configuration gap.
     */
    public static DebitInterestRate provisional(BigDecimal annualPercent) {
        return new DebitInterestRate(Optional.of(Objects.requireNonNull(annualPercent)), true);
    }

    /** No rate obtainable. The debt is still stated; the accrual figure is not. */
    public static DebitInterestRate unavailable() {
        return new DebitInterestRate(Optional.empty(), false);
    }

    /**
     * Whether this rate may appear in a message to a trader.
     *
     * <p>False for a stand-in and false when absent. REQ-708 states both prohibitions separately,
     * and they collapse to the same answer here because the consequence is the same: a figure the
     * trader would act on that this system cannot stand behind.
     */
    public boolean quotableInMessages() {
        return this.annualPercent.isPresent() && !this.provisional;
    }

    /**
     * Whether an accrual figure can be computed at all.
     *
     * <p>Distinct from {@link #quotableInMessages}: a provisional rate still produces a figure for
     * an internal or non-production display, which is why the debt banner can show something while
     * the dues email cannot.
     */
    public boolean canComputeAccrual() {
        return this.annualPercent.isPresent();
    }
}
