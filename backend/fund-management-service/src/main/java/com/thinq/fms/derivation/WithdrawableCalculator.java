package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

import java.util.List;

/**
 * Rule B4, and nothing else.
 *
 * <p><b>This class performs no I/O.</b> Not a database call, not a vendor call, not a clock
 * read. That is not incidental tidiness — it is the reason
 * {@link WithdrawableCalculatorTest} can generate ten thousand inputs and assert that the
 * terms reconcile, without a container or a mock. Adding a single fetch here would make the
 * central correctness property of this system untestable, so the inputs are assembled
 * elsewhere and handed in whole.
 *
 * <p>Work is a fold over exactly six terms — constant, not "O(1)" in the hand-waving sense.
 */
public final class WithdrawableCalculator {

    /**
     * Compute the withdrawable figure and the derivation that explains it.
     *
     * <p>Preconditions: {@code inputs} is non-null and its magnitudes are non-negative,
     * both enforced by {@link WithdrawableInputs}' own constructor.
     *
     * @throws ArithmeticException on overflow while summing. Deliberately not caught: a
     *     wrapped total would be a silently wrong money figure, which is worse than a
     *     failed request.
     */
    public Derivation compute(WithdrawableInputs inputs) {
        List<DerivationTerm> terms = List.of(
                new DerivationTerm(TermCode.SETTLED_LEDGER, signOf(inputs.settledLedgerBalance()),
                        magnitudeOf(inputs.settledLedgerBalance())),
                new DerivationTerm(TermCode.ADDED_TODAY, TermSign.MINUS, inputs.moneyAddedToday()),
                new DerivationTerm(TermCode.UNSETTLED_PROCEEDS, TermSign.MINUS, inputs.unsettledSaleProceeds()),
                new DerivationTerm(TermCode.CHARGES_UNPOSTED, TermSign.MINUS, inputs.chargesIncurredNotPosted()),
                new DerivationTerm(TermCode.SHORTFALL_OUTSTANDING, TermSign.MINUS, inputs.marginShortfall()),
                new DerivationTerm(TermCode.COLLATERAL_MET, TermSign.PLUS,
                        inputs.committedMarginMetFromCollateral()));

        long preFloor = 0L;
        for (DerivationTerm term : terms) {
            preFloor = Math.addExact(preFloor, term.signedPaise());
        }

        // Rule B9's single exception. Negative values are legitimate everywhere else in
        // this system and are never clamped; the withdrawable figure alone floors at zero,
        // because "what can reach my bank today" has no negative answer.
        //
        // Nothing is hidden by the floor: the debt itself is still presented as a debt by
        // the health module, and the term that drove the figure below zero stays visible
        // in the derivation above.
        Money withdrawable = Money.ofPaise(preFloor).flooredAtZero();

        return new Derivation(withdrawable, preFloor, terms, largestDeduction(terms));
    }

    /**
     * The settled ledger balance is the one term that may legitimately be negative — a
     * debit balance is a real state. Its magnitude and direction are split here so that
     * {@link DerivationTerm}'s invariant (magnitude is never negative) holds for all six,
     * and so the client can render a negative opening term without special-casing it.
     */
    private static TermSign signOf(Money ledgerBalance) {
        return ledgerBalance.isNegative() ? TermSign.MINUS : TermSign.PLUS;
    }

    private static Money magnitudeOf(Money ledgerBalance) {
        return ledgerBalance.isNegative() ? ledgerBalance.negated() : ledgerBalance;
    }

    /**
     * The largest deduction, for REQ-102's requirement that it be named without the trader
     * opening the panel.
     *
     * <p>Largest by magnitude across every term whose sign is MINUS — not the first
     * negative term, which would only be right when the terms happened to be ordered by
     * size. Returns null when nothing was deducted, rather than naming an arbitrary term.
     */
    private static TermCode largestDeduction(List<DerivationTerm> terms) {
        TermCode largest = null;
        long largestPaise = 0L;

        for (DerivationTerm term : terms) {
            if (term.isDeduction() && term.amount().paise() > largestPaise) {
                largestPaise = term.amount().paise();
                largest = term.code();
            }
        }
        return largest;
    }
}
