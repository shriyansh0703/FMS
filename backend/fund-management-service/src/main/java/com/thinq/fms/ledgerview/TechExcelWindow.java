package com.thinq.fms.ledgerview;

/**
 * The ledger window bound, stated once.
 *
 * <p>Exists so that {@link TransactionPeriod} — a domain type — does not have to import the
 * TechExcel gateway to know its limit, and so the number is not written twice. The constraint is
 * the vendor's: its {@code Ledger} endpoint has no pagination, so the date window is the only
 * bound on response size.
 */
public final class TechExcelWindow {

    /** Matches {@code TechExcelLedgerGateway.MAX_WINDOW_DAYS}; a test asserts they agree. */
    public static final int MAX_WINDOW_DAYS = 92;

    private TechExcelWindow() {
    }
}
