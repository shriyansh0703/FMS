package com.thinq.fms.derivation;

/**
 * The six terms of Rule B4's withdrawable derivation.
 *
 * <p>Order is significant: it is the order the trader sees, and the client renders every
 * term including those whose value is zero (REQ-102). These are codes, not labels — the
 * client resolves copy from them, so no English lives here.
 */
public enum TermCode {
    /** What the account records as at the last completed settlement. */
    SETTLED_LEDGER,
    /** Funds added today cannot be withdrawn today. */
    ADDED_TODAY,
    /** Money from sales that have not completed settlement. Tradable, not withdrawable. */
    UNSETTLED_PROCEEDS,
    /** Costs already incurred that will appear on the account shortly. */
    CHARGES_UNPOSTED,
    /** Positions currently require more than the account holds. Added 20 Aug 26 for REQ-506. */
    SHORTFALL_OUTSTANDING,
    /**
     * The counter-intuitive one, and the reason Rule B4 says it must never be shown bare.
     * The account blocks the full margin requirement against cash; where pledged securities
     * covered part of it, that cash was never truly committed and is added back.
     */
    COLLATERAL_MET
}
