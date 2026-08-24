package com.thinq.fms.movement.payout;

/**
 * Why a settlement sent less than was requested, or nothing at all.
 *
 * <p>REQ-308 requires an outcome specific enough to name the deduction that accounts for a gap.
 * These are codes, not copy — the client resolves wording from them, so no English lives here.
 */
public enum SettlementReasonCode {
    /** The full amount was sent. */
    NONE,

    /**
     * Margin blocked against open positions, quantified by TechExcel's {@code RMSData}. The one
     * cause the contract lets this system name numerically rather than by phrase-matching.
     */
    MARGIN_BLOCKED,

    /** The account did not hold enough at settlement time. */
    INSUFFICIENT_BALANCE,

    /** The destination account was refused by the bank or the back office. */
    DESTINATION_REJECTED,

    /** The account is blocked from payouts for a compliance or operational reason. */
    ACCOUNT_BLOCKED,

    /**
     * {@code Reject_Reason} carried a phrase the configured table does not map (OA-4).
     *
     * <p>The verbatim text is stored in {@code settlement_reason_text} and raises an operational
     * alert so the table can be extended. It is <b>never shown to the trader</b>: an unmapped
     * back-office string is not user-facing copy. The trader sees the generic partial-settlement
     * message, which is how this degrades without either lying to them or losing the detail.
     */
    UNSPECIFIED
}
