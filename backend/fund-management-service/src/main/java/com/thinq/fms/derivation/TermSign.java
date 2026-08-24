package com.thinq.fms.derivation;

/**
 * Whether a term increases or reduces the withdrawable figure.
 *
 * <p>Carried explicitly rather than inferred from the amount's sign, because REQ-102
 * requires the trader to be able to tell a term that increases the figure from one that
 * reduces it — and an amount of zero carries no sign of its own.
 */
public enum TermSign {
    PLUS(1),
    MINUS(-1);

    private final int multiplier;

    TermSign(int multiplier) {
        this.multiplier = multiplier;
    }

    public int multiplier() {
        return this.multiplier;
    }
}
