package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

/**
 * Everything Rule B4 needs, and nothing else.
 *
 * <p>Deliberately a plain value carrier with no source, no timestamp and no vendor
 * identity: those belong to the snapshot the assembler builds (Stage 4, TASK-17), and
 * keeping them out of here is what makes the calculator a pure function that the property
 * tests can drive directly.
 *
 * <p>Every field except {@code settledLedgerBalance} is a magnitude and must not be
 * negative. The ledger balance may be negative — a debit balance is a real state, and
 * Rule B9 forbids clamping it.
 */
public record WithdrawableInputs(
        Money settledLedgerBalance,
        Money moneyAddedToday,
        Money unsettledSaleProceeds,
        Money chargesIncurredNotPosted,
        Money marginShortfall,
        Money committedMarginMetFromCollateral) {

    public WithdrawableInputs {
        java.util.Objects.requireNonNull(settledLedgerBalance, "settledLedgerBalance");
        requireNonNegative(moneyAddedToday, "moneyAddedToday");
        requireNonNegative(unsettledSaleProceeds, "unsettledSaleProceeds");
        requireNonNegative(chargesIncurredNotPosted, "chargesIncurredNotPosted");
        requireNonNegative(marginShortfall, "marginShortfall");
        requireNonNegative(committedMarginMetFromCollateral, "committedMarginMetFromCollateral");
    }

    private static void requireNonNegative(Money value, String field) {
        java.util.Objects.requireNonNull(value, field);
        if (value.isNegative()) {
            throw new IllegalArgumentException(field + " is a magnitude and cannot be negative");
        }
    }
}
