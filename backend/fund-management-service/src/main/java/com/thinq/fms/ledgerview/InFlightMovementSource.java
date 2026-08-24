package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.AccountRef;

import java.time.LocalDate;
import java.util.List;

/**
 * Movements that are not in the ledger — because they have not landed, or never will.
 *
 * <h2>Why the movements view needs a second source at all</h2>
 *
 * <p>The ledger records what happened. A payin that failed at the bank never reaches it, because no
 * money moved; one still in flight has not reached it yet. Building the movements view from the
 * ledger alone therefore omitted exactly two things the requirements name:
 *
 * <ul>
 *   <li><b>Rule L8</b> — "failed and cancelled movements stay in the history … it is the entry a
 *       user most often needs to discuss."
 *   <li><b>REQ-402</b> — "show each item's current status, <b>including items not yet
 *       complete</b>."
 * </ul>
 *
 * <p>So the view is a union of two sources, and was always going to be. This interface is the
 * second one, kept narrow for the same reason {@link LedgerEntrySource} is: the query depends on
 * the capability, not on the payin module's repository.
 */
@FunctionalInterface
public interface InFlightMovementSource {

    /**
     * Attempts in the period that the ledger does not carry: in flight, failed, or cancelled.
     *
     * <p>A <b>confirmed</b> payin must not appear here — it is in the ledger, and returning it
     * from both sources would show the trader one deposit twice.
     *
     * @return empty list, never null
     */
    List<TransactionEntry> read(AccountRef account, LocalDate from, LocalDate to);
}
