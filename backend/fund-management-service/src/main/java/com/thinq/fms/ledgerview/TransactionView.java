package com.thinq.fms.ledgerview;

/**
 * Rule L5's two views, and why there are exactly two.
 *
 * <p>"Where is my money" and "explain my account" are different questions. Most queries are the
 * first and most of the content is the second, so presenting only the combined view buries the
 * common question in the rare one.
 */
public enum TransactionView {

    /**
     * Money the trader moved in or out, with its current status.
     *
     * <p>Rule L5a draws the line and it is easy to get wrong: sale proceeds, mark-to-market and
     * charges are <b>not</b> payins. A payin is money the trader moved from their own bank.
     * Including trading outcomes here would answer a question nobody asked and hide the one they
     * did.
     */
    MOVEMENTS,

    /** Every entry, with the running balance. The full account explanation. */
    ALL_ENTRIES;

    /** Whether an entry belongs in this view. */
    public boolean includes(EntryKind kind) {
        return this == ALL_ENTRIES || kind.isMoneyMovement();
    }
}
