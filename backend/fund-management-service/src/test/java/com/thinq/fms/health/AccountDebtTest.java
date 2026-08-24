package com.thinq.fms.health;

import com.thinq.fms.platform.money.Money;
import com.thinq.fms.settings.DebitInterestRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/** REQ-501, REQ-502 and Rule H1 — a debt presented as owed, with its accrual. */
class AccountDebtTest {

    private static final DebitInterestRate EIGHTEEN =
            DebitInterestRate.configured(new BigDecimal("18.00"));

    @Test
    @DisplayName("a debt is a positive amount owed, never a negative balance")
    void aDebtIsAPositiveAmountOwed() {
        // Rule H1. A debit shown with the same treatment as a credit reads as money available, and
        // the trader acts on it. The type cannot carry the wrong sign.
        assertThatThrownBy(() -> new AccountDebt(Money.ofPaise(-400_000L), "Brokerage", EIGHTEEN, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rule H1");

        assertThatThrownBy(() -> new AccountDebt(Money.ZERO, "Brokerage", EIGHTEEN, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the entry that created the debt must be named")
    void theCauseMustBeNamed() {
        // REQ-501. "You owe ₹4,000" with no cause is the message traders escalate rather than act on.
        assertThatThrownBy(() -> new AccountDebt(Money.ofPaise(400_000L), "  ", EIGHTEEN, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("accrual is computed daily on the outstanding amount")
    void accrualIsComputedDaily() {
        // ₹4,000 at 18% for 365 days is ₹720. Checking a full year makes the arithmetic legible.
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage", EIGHTEEN, 365);

        assertThat(debt.accruedSoFar()).contains(Money.ofPaise(72_000L));
    }

    @Test
    @DisplayName("accrual rounds down, so the trader is never over-charged by rounding")
    void accrualRoundsDown() {
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage", EIGHTEEN, 1);

        // One day at 18% on ₹4,000 is 197.26 paise; the trader owes 197.
        assertThat(debt.accruedSoFar()).contains(Money.ofPaise(197L));
    }

    @Test
    @DisplayName("a debt on its first day has accrued nothing")
    void aDebtOnItsFirstDayHasAccruedNothing() {
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage", EIGHTEEN, 0);

        assertThat(debt.accruedSoFar()).contains(Money.ZERO);
        assertThat(debt.amountToClear()).isEqualTo(Money.ofPaise(400_000L));
    }

    @Test
    @DisplayName("the amount to clear includes accrual, so paying it actually clears the debt")
    void theAmountToClearIncludesAccrual() {
        // REQ-502. A trader who pays the figure they were shown an hour ago and remains a rupee in
        // debt has been given a number that was never going to work.
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage", EIGHTEEN, 365);

        assertThat(debt.amountToClear()).isEqualTo(Money.ofPaise(472_000L));
    }

    @Test
    @DisplayName("with no rate the debt still stands, and the accrual is simply unavailable")
    void withNoRateTheDebtStillStands() {
        // Account Health Flow 1: state the debt and that the accrual figure is unavailable, rather
        // than presenting the debt as static.
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage",
                DebitInterestRate.unavailable(), 30);

        assertThat(debt.amountOwed()).isEqualTo(Money.ofPaise(400_000L));
        assertThat(debt.accruedSoFar()).isEmpty();
        assertThat(debt.accrualAvailable()).isFalse();
        assertThat(debt.amountToClear())
                .as("without a rate the best available figure is the principal")
                .isEqualTo(Money.ofPaise(400_000L));
    }

    @Test
    @DisplayName("a provisional rate computes a figure but may not be quoted in a message")
    void aProvisionalRateComputesButIsNotQuoted() {
        AccountDebt debt = new AccountDebt(Money.ofPaise(400_000L), "Brokerage",
                DebitInterestRate.provisional(new BigDecimal("18.00")), 365);

        assertThat(debt.accrualAvailable()).isTrue();
        assertThat(debt.accruedSoFar()).contains(Money.ofPaise(72_000L));
        assertThat(debt.rateQuotableInMessages())
                .as("REQ-708 forbids a stand-in reaching a trader").isFalse();

        // And the confirmed case, so the flag is a real distinction rather than a constant false.
        assertThat(new AccountDebt(Money.ofPaise(400_000L), "Brokerage", EIGHTEEN, 365)
                .rateQuotableInMessages()).isTrue();
    }

    @Test
    @DisplayName("negative days outstanding are refused")
    void negativeDaysAreRefused() {
        assertThatThrownBy(() -> new AccountDebt(Money.ofPaise(1L), "Brokerage", EIGHTEEN, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
