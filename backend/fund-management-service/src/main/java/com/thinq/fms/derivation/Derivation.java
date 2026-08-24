package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

import java.util.List;

/**
 * The output of Rule B4: the withdrawable figure, and the terms that explain it.
 *
 * <p>The figure and its explanation are one object on purpose. An explanation that can be
 * missing while its figure is present is a second source of truth about one number, which
 * is the failure REQ-102 and Rule B12 both exist to prevent — and it is why the API returns
 * the derivation inside the summary response rather than behind a second request.
 *
 * @param withdrawable  the figure a trader may act on, floored at zero
 * @param preFloorPaise the raw sum of the six signed terms, before Rule B9's floor. Retained
 *                      because the property tests assert the terms reconcile to <i>this</i>,
 *                      and because a support conversation about a zero figure needs to know
 *                      how far below zero it actually was
 * @param terms         all six, in Rule B4's order, including any whose value is zero
 * @param largestDeduction the deduction to name without the trader opening the panel
 *                         (REQ-102), or {@code null} when nothing was deducted
 */
public record Derivation(
        Money withdrawable,
        long preFloorPaise,
        List<DerivationTerm> terms,
        TermCode largestDeduction) {

    public Derivation {
        terms = List.copyOf(terms);

        // The figure and its explanation are one object, and these two checks are what make that
        // structural rather than conventional. Asserting them only in the calculator's tests
        // left any other construction site free to produce a Derivation whose terms do not
        // explain its figure — which is the exact contradiction Rule B12 and REQ-102 forbid.
        if (terms.size() != TermCode.values().length) {
            throw new IllegalArgumentException(
                    "Rule B4 has " + TermCode.values().length + " terms and all are shown, "
                            + "including those whose value is zero; got " + terms.size());
        }
        long expected = Math.max(0L, preFloorPaise);
        if (withdrawable.paise() != expected) {
            throw new IllegalArgumentException(
                    "the withdrawable figure must be the pre-floor sum floored at zero; "
                            + withdrawable + " does not follow from " + preFloorPaise);
        }
    }

    /**
     * Whether Rule B9's exception actually engaged.
     *
     * <p>Distinct from "the figure is zero": a genuinely zero balance and a balance driven
     * below zero by a shortfall are different states, and the second one has an explanation
     * the trader needs.
     */
    public boolean flooredAtZero() {
        return this.preFloorPaise < 0L;
    }
}
