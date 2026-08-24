package com.thinq.fms.integration.juspay;

/**
 * Rule A9a's six ways a well-formed payment can fail, plus success and the one state Rule A9b
 * insists is neither.
 *
 * <p>The PRD is explicit that these are not interchangeable: the recovery differs, and so does
 * whose problem it is (Rule A9c). Collapsing them into "failed" is the specific mistake this
 * enum prevents — it sends a trader to a bank that cannot help them.
 */
public enum PayinOutcome {

    /** The money arrived. */
    CONFIRMED(true, false),

    /** The bank declined the payment. */
    BANK_DECLINED(true, false),

    /** Not enough money in the trader's bank account. */
    INSUFFICIENT_FUNDS_AT_BANK(true, false),

    /** Above the bank's own per-payment limit, which this system cannot see in advance. */
    ABOVE_BANK_LIMIT(true, false),

    /**
     * The bank has not answered.
     *
     * <p><b>Rule A9b: this is not a failure, and the recovery is the opposite of one.</b> It is
     * titled "Awaiting confirmation", it withholds every action for 30 seconds, and it is the one
     * outcome that does not restore the amount to the input field. Above all: <b>wait, and
     * specifically do not retry</b> — a retry here can debit the trader twice.
     */
    AWAITING_BANK(false, true),

    /** This system, or the gateway, was unreachable. Rule A9c: our problem, said as ours. */
    SERVICE_UNREACHABLE(true, false),

    /** The trader cancelled before approving. */
    CANCELLED_BY_USER(true, false),

    /**
     * The gateway reported a status this system does not recognise.
     *
     * <p>Treated exactly as {@link #AWAITING_BANK} for recovery purposes, because an unrecognised
     * status is an unknown outcome and Rule A9b already says what to do with one. Never mapped to
     * a failure: a status this system has not seen before is far more likely to be a new success
     * variant than a new failure, and telling a trader their payment failed when it succeeded is
     * the more expensive error.
     */
    UNKNOWN(false, true);

    private final boolean terminal;
    private final boolean awaitingResolution;

    PayinOutcome(boolean terminal, boolean awaitingResolution) {
        this.terminal = terminal;
        this.awaitingResolution = awaitingResolution;
    }

    public boolean isTerminal() {
        return this.terminal;
    }

    /** Whether Rule A9b's wait-and-do-not-retry handling applies. */
    public boolean isAwaitingResolution() {
        return this.awaitingResolution;
    }

    /**
     * Whether it is safe to offer the trader another attempt.
     *
     * <p>False for both unresolved states. Offering a retry while the bank has not answered is
     * how one payment becomes two, and the trader has no way to know which one landed.
     */
    public boolean mayRetry() {
        return this.terminal && this != CONFIRMED;
    }
}
