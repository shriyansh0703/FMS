package com.thinq.fms.platform.error;

/**
 * The withdrawable figure could not be established, so no withdrawal may be requested.
 *
 * <p>The HLD settles that RMS's figure is the authority and Rule B4 is its explanation. Where the
 * two disagree, <b>neither is silently picked as the winner</b> — the figure is presented as
 * unavailable and the action is blocked. Guessing here would mean either overstating what a trader
 * can take (and failing at settlement) or understating it (and withholding their own money).
 *
 * @see #verdict() the reconciliation verdict, which REQ-102's error path renders
 */
public class WithdrawableUnavailableException extends FmsConflictException {

    private final String verdict;

    public WithdrawableUnavailableException(String verdict, String message) {
        super("withdrawable_unavailable", message);
        this.verdict = verdict;
    }

    /** {@code DIVERGENT} or {@code UNAVAILABLE}. Never {@code RECONCILED}. */
    public String verdict() {
        return this.verdict;
    }
}
