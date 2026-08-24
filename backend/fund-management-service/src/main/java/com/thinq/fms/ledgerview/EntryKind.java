package com.thinq.fms.ledgerview;

/**
 * The kinds of money event a ledger entry can be, from the account holder's point of view.
 *
 * <p>Rule L5a draws the line these values encode: sale proceeds, mark-to-market and charges are
 * <b>not</b> payins. A payin is money the account holder moved from their own bank. Collapsing
 * the two would put trading outcomes in the "where is my money" view, which Rule L5 reserves for
 * money the holder moved.
 */
public enum EntryKind {

    /** Money the holder moved in from their own bank. Rule L4: user-caused. */
    PAYIN(true),

    /** Money the holder asked to send to their own bank. User-caused. */
    PAYOUT(false),

    /**
     * Money returned because the settlement calendar required it, not because anyone asked.
     *
     * <p>The reason Rule L4 exists, and the reason {@code userCaused} belongs to the mapper: this
     * and {@link #PAYOUT} are both {@code TRANS_TYPE = P} in TechExcel, and only the mapping knows
     * which is which.
     */
    MANDATED_RETURN(false),

    /** Proceeds from a sale reaching the account. Not a payin. */
    SALE_PROCEEDS(false),

    /** The cost of a purchase leaving the account. */
    PURCHASE_COST(false),

    /** Brokerage, statutory charges, and the rest of the cost of trading. */
    CHARGES(false),

    /** Margin blocked or released against positions. TechExcel flags these with {@code voctype}. */
    MARGIN_MOVEMENT(false),

    /** Interest, penalties and other amounts the account accrues. */
    ACCOUNT_ACCRUAL(false),

    /** The period's opening balance, flagged by {@code OPENINGBALANCE}. */
    OPENING_BALANCE(false),

    /** A reversal of an earlier entry. Rule L2: never a deletion, always its own entry. */
    REVERSAL(false);

    private final boolean alwaysUserCaused;

    EntryKind(boolean alwaysUserCaused) {
        this.alwaysUserCaused = alwaysUserCaused;
    }

    /**
     * Whether this kind is always the holder's own doing.
     *
     * <p>Only {@link #PAYIN} is unconditionally so. {@link #PAYOUT} is user-caused when a request
     * lies behind it and not when the calendar forced it — which is the whole point of separating
     * it from {@link #MANDATED_RETURN}, and why the mapper decides rather than this enum.
     */
    public boolean isAlwaysUserCaused() {
        return this.alwaysUserCaused;
    }

    /** Whether this belongs in Rule L5's "where is my money" view rather than the full ledger. */
    public boolean isMoneyMovement() {
        return this == PAYIN || this == PAYOUT || this == MANDATED_RETURN;
    }
}
