package com.thinq.fms.settings;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;

/**
 * Whether an amount clears the minimum add (REQ-703, Rule H3).
 *
 * <p><b>The waiver exists because the floor would otherwise trap a trader in debt.</b> An account
 * owing ₹40 cannot settle it while the minimum is ₹100 without depositing more than it owes, which
 * is a rule about our commercial floor standing in the way of the trader doing the one thing both
 * sides want. So the exact amount owed is always permitted.
 *
 * <p>The waiver is <b>exact</b>. ₹40 owed permits ₹40 and not ₹41 — anything above the debt is an
 * ordinary funding amount and meets the ordinary floor. A tolerance here would turn "settle your
 * debt" into a general way around the minimum.
 */
public final class MinimumAddPolicy {

    private final Money minimumAdd;

    public MinimumAddPolicy(Money minimumAdd) {
        this.minimumAdd = Objects.requireNonNull(minimumAdd, "minimumAdd");
    }

    /**
     * Whether this amount may be added.
     *
     * @param amountOwed the outstanding debit balance, or zero where the account owes nothing
     */
    public boolean permits(Money amount, Money amountOwed) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(amountOwed, "amountOwed");

        if (!amount.isPositive()) {
            return false;
        }
        if (amount.compareTo(this.minimumAdd) >= 0) {
            return true;
        }
        // The single exception: the amount is below the floor and settles the debt exactly.
        return amountOwed.isPositive() && amount.equals(amountOwed);
    }

    /**
     * The amount to suggest while the account is in debt (REQ-502, REQ-703).
     *
     * <p>The exact debt where there is one, so the trader is not left computing it, and the minimum
     * otherwise. Suggesting the minimum to an account owing less than it would tell them to deposit
     * more than they owe.
     */
    public Money suggestedAmount(Money amountOwed) {
        Objects.requireNonNull(amountOwed, "amountOwed");
        return amountOwed.isPositive() ? amountOwed : this.minimumAdd;
    }

    public Money minimumAdd() {
        return this.minimumAdd;
    }
}
