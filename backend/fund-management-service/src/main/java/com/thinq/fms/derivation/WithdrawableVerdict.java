package com.thinq.fms.derivation;

/**
 * Whether Rule B4's derivation and RMS's own figure agree.
 *
 * <p>The HLD settles the relationship: RMS's {@code GetWithdrawalAmt} is the authority on what may
 * leave, and Rule B4's six terms are the explanation of it. This enum records what happened when
 * the two were compared, because the disagreement case is not an error to swallow — it is a state
 * the trader is shown and an action that is blocked.
 */
public enum WithdrawableVerdict {

    /** The derivation reconciles to RMS's figure. The only verdict that permits a withdrawal. */
    RECONCILED,

    /**
     * Both answered and they disagree.
     *
     * <p>Neither is picked as the winner. Picking RMS would show a figure Rule B4 cannot explain,
     * which is the product; picking the derivation would let a trader request money RMS will
     * refuse at settlement. So the figure is unavailable and the action is blocked.
     *
     * <p>OA-1 is the open question of how often this happens. If RMS applies deductions
     * incompatible with Rule B4's terms, this becomes the normal state rather than the alarm, and
     * the design needs revisiting rather than the threshold loosening.
     */
    DIVERGENT,

    /** A source could not be reached, or the calendar was unavailable (OA-5). Fails safe. */
    UNAVAILABLE
}
