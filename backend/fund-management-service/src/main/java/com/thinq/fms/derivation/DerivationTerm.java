package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

/**
 * One line of Rule B4's derivation: what it is, which way it moves the figure, and how much.
 *
 * <p>{@code amount} is always the magnitude and is never negative; direction lives in
 * {@code sign}. Keeping them separate is what lets the client render "− ₹1,200.00" without
 * having to decide whether a negative amount means a deduction or a negative balance.
 */
public record DerivationTerm(TermCode code, TermSign sign, Money amount) {

    public DerivationTerm {
        if (amount.isNegative()) {
            // A term's magnitude is never negative. A negative input means the caller has
            // conflated magnitude with direction, which would make the sum silently wrong.
            throw new IllegalArgumentException(
                    "term " + code + " was given a negative magnitude; direction belongs in the sign");
        }
    }

    /** This term's contribution to the pre-floor total, signed. */
    public long signedPaise() {
        return this.sign.multiplier() * this.amount.paise();
    }

    public boolean isDeduction() {
        return this.sign == TermSign.MINUS;
    }
}
