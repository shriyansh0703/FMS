package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One entry from TechExcel's ledger, as this system reads it.
 *
 * <p>Deliberately far narrower than TechExcel's row, which carries 52 fields including the
 * account holder's PAN, email, telephone and address. Those are regulated identifiers and the
 * ratified taxonomy's rule R4 forbids them reaching an event property — so they are dropped at
 * this boundary rather than carried and filtered later. A field absent from this record cannot
 * leak from it.
 *
 * @param voucherNo      TechExcel's identifier for the entry. Stable, and what support quotes
 * @param segment        from {@code COCD}: BSE_CASH, NSE_CASH, NSE_FNO, CD_NSE, CD_BSE. REQ-108
 *                       requires every entry record its segment from day one, so that separating
 *                       the segments later is a display change rather than a data migration
 * @param voucherDate    when the entry was posted to the ledger
 * @param debit          the debit amount, zero when this is a credit
 * @param credit         the credit amount, zero when this is a debit
 * @param closingBalance from {@code CLOSING_AMT}. <b>TechExcel supplies the running balance and
 *                       this system never accumulates one</b> (hld.md §9.1b) — two systems
 *                       computing one running balance is Rule B12's failure mode
 * @param narration      TechExcel's own description. Raw, and never rendered to a trader:
 *                       REQ-401 requires plain language, which {@code EntryDescriptionMapper}
 *                       produces from the structured fields instead
 * @param transType      R (receipt), P (payment), J (journal), SJ (system journal)
 * @param settlementNo   the settlement this entry belongs to, for a transaction bill
 * @param settlementPayinDate from {@code SETL_PAYINDATE}. Present on a transaction bill, which is
 *                       what makes Rule B4's unsettled-proceeds deduction measurable in
 *                       settlement days
 * @param marketType     from {@code MKT_TYPE}, e.g. "M-T+1 Normal" on a transaction bill
 * @param openingBalance whether {@code OPENINGBALANCE} flagged this as the period's opening entry.
 *                       REQ-406's two stamped period endpoints are sourced from these
 * @param userRefNo      our reference where this entry came from an instruction we issued
 * @param gatewayId      the payment gateway's reference, where one applies
 */
public record LedgerEntry(
        String voucherNo,
        String segment,
        LocalDate voucherDate,
        Money debit,
        Money credit,
        Money closingBalance,
        String narration,
        String transType,
        String settlementNo,
        LocalDate settlementPayinDate,
        String marketType,
        boolean openingBalance,
        String userRefNo,
        String gatewayId) {

    public LedgerEntry {
        Objects.requireNonNull(voucherNo, "voucherNo");
        Objects.requireNonNull(voucherDate, "voucherDate");
        Objects.requireNonNull(debit, "debit");
        Objects.requireNonNull(credit, "credit");

        if (debit.isNegative() || credit.isNegative()) {
            // A negative debit is a credit wearing the wrong field. Accepting one would make the
            // running balance disagree with the entries that are supposed to explain it.
            throw new IllegalArgumentException(
                    "debit and credit are magnitudes; a reversal is its own entry, per Rule L2");
        }
        if (debit.isPositive() && credit.isPositive()) {
            throw new IllegalArgumentException(
                    "an entry is a debit or a credit, not both; voucher " + voucherNo);
        }
    }

    /** The signed effect on the balance: credits add, debits subtract. */
    public Money signedAmount() {
        return this.credit.minus(this.debit);
    }

    public boolean isCredit() {
        return this.credit.isPositive();
    }

    /**
     * Whether this entry is a trade contract note rather than a cash movement.
     *
     * <p>{@code SETL_PAYINDATE} is populated only on a transaction bill, which is what makes the
     * settlement calendar relevant to Rule B4: the unsettled-proceeds deduction is measured in
     * settlement days, and those dates come from entries like this one.
     */
    public boolean isTransactionBill() {
        return this.settlementPayinDate != null;
    }
}
