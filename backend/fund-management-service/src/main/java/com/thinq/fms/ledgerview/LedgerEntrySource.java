package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;

import java.time.LocalDate;
import java.util.List;

/**
 * Where ledger entries come from.
 *
 * <p>One method, so the transaction query depends on the capability rather than on
 * {@code TechExcelLedgerGateway} and its HTTP client. In production the gateway satisfies it as a
 * method reference; in a test a list does.
 *
 * <p>The alternative was a test subclass passing {@code null} for the gateway and overriding the
 * read. That works and it is worse: it weakens a production invariant — the service genuinely must
 * not be constructed without a source — so that a test can avoid one. A seam costs one interface
 * and keeps the constructor honest.
 */
@FunctionalInterface
public interface LedgerEntrySource {

    /**
     * Entries for one account in one window, oldest first as the back office returns them.
     *
     * @throws VendorUnavailableException when the back office cannot be reached. Callers must not
     *     substitute an empty list: an empty period and an unreachable ledger look identical to a
     *     trader, and only one of them means "you have no transactions"
     */
    List<LedgerEntry> read(AccountRef account, LocalDate from, LocalDate to);
}
