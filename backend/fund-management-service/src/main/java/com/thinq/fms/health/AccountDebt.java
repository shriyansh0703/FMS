package com.thinq.fms.health;

import com.thinq.fms.platform.money.Money;
import com.thinq.fms.settings.DebitInterestRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * A negative balance, presented as something owed (REQ-501, REQ-502, Rule H1).
 *
 * <p><b>Rule H1's point is presentational and this type enforces it structurally.</b> A debit
 * balance shown with the same treatment as a credit reads as money available, and a trader acts on
 * it. So this carries a positive {@code amountOwed} rather than a negative balance: there is no way
 * to hand it to a renderer that formats it like an available figure, because it is not one.
 *
 * <p>The accrual is optional and its absence is meaningful. REQ-708 forbids quoting a provisional
 * rate in a message and requires the debt still stated when no rate is obtainable — so a debt with
 * no accrual figure is a legitimate, expected state, not a failure.
 */
public record AccountDebt(Money amountOwed, String causeEntryDescription, DebitInterestRate rate,
                          int daysOutstanding) {

    public AccountDebt {
        Objects.requireNonNull(amountOwed, "amountOwed");
        Objects.requireNonNull(causeEntryDescription, "causeEntryDescription");
        Objects.requireNonNull(rate, "rate");

        if (!amountOwed.isPositive()) {
            throw new IllegalArgumentException(
                    "a debt is a positive amount owed, not a negative balance; got " + amountOwed
                            + ". Rule H1 forbids presenting a debit with the same treatment as a credit.");
        }
        if (causeEntryDescription.isBlank()) {
            // REQ-501 requires the entry that created the debt named in plain language. "You owe
            // ₹4,000" with no cause is the message traders escalate rather than act on.
            throw new IllegalArgumentException("the entry that created the debt must be named");
        }
        if (daysOutstanding < 0) {
            throw new IllegalArgumentException("days outstanding is not negative; got " + daysOutstanding);
        }
    }

    /**
     * Interest accrued so far, where a rate is available.
     *
     * <p>Simple daily accrual on the outstanding amount. Empty where no rate can be obtained — the
     * debt is still stated, and Account Health Flow 1 requires saying the accrual figure is
     * unavailable rather than presenting the debt as static.
     */
    public Optional<Money> accruedSoFar() {
        return this.rate.annualPercent().map(annual -> {
            BigDecimal daily = annual
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
            BigDecimal accrued = BigDecimal.valueOf(this.amountOwed.paise())
                    .multiply(daily)
                    .multiply(BigDecimal.valueOf(this.daysOutstanding));
            // Rounded to whole paise, down: this system never over-states what a trader owes.
            return Money.ofPaise(accrued.setScale(0, RoundingMode.DOWN).longValueExact());
        });
    }

    /**
     * The total to clear the debt exactly, including accrual (REQ-502).
     *
     * <p>"The amount owed at the moment of payment, including any accrual since it was last
     * displayed" — a trader who pays the figure they were shown an hour ago and remains a rupee in
     * debt has been given a number that was never going to work.
     */
    public Money amountToClear() {
        return this.accruedSoFar().map(this.amountOwed::plus).orElse(this.amountOwed);
    }

    /** Whether the accrual can be stated at all (REQ-708). */
    public boolean accrualAvailable() {
        return this.rate.canComputeAccrual();
    }

    /** Whether the rate itself may appear in a message (REQ-708's stand-in prohibition). */
    public boolean rateQuotableInMessages() {
        return this.rate.quotableInMessages();
    }
}
