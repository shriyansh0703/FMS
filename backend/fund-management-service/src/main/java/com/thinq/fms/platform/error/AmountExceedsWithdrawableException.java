package com.thinq.fms.platform.error;

import com.thinq.fms.platform.money.Money;

/**
 * The trader asked for more than Rule B4's figure allows.
 *
 * <p>Carries the withdrawable figure so the client need not re-fetch to explain the refusal.
 * REQ-102 requires the figure explained rather than asserted, and a refusal that says only "too
 * much" makes the trader go looking for the number that would have worked.
 */
public class AmountExceedsWithdrawableException extends FmsUnprocessableException {

    private final Money requested;
    private final Money withdrawable;

    public AmountExceedsWithdrawableException(Money requested, Money withdrawable) {
        super("amount_exceeds_withdrawable",
                "requested " + requested + " against a withdrawable figure of " + withdrawable);
        this.requested = requested;
        this.withdrawable = withdrawable;
    }

    public Money requested() {
        return this.requested;
    }

    public Money withdrawable() {
        return this.withdrawable;
    }
}
