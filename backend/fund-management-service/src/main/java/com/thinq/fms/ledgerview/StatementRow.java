package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One line of a statement, already resolved to what the trader sees.
 *
 * <p>Deliberately separate from {@link LedgerEntry}: that is the back office's shape, this is the
 * screen's. Rule L8a requires an export to return precisely what is on screen, and the surest way
 * to satisfy that is for the export and the screen to render the same rows rather than each
 * deriving their own from the raw entries.
 *
 * @param date            the entry's own date
 * @param description     resolved plain-language copy. Never a settlement identifier
 * @param debitOrCredit   {@code Debit} or {@code Credit}. <b>Rule L8a is explicit</b>: the internal
 *                        kinds are this system's words for its own plumbing, and the file is read
 *                        against a bank statement, so it uses a bank statement's words
 * @param reference       the back-office reference, as secondary detail
 * @param amount          the magnitude
 * @param resultingBalance the running balance after this entry, from TechExcel's {@code CLOSING_AMT}
 */
public record StatementRow(
        LocalDate date,
        String description,
        String debitOrCredit,
        String reference,
        Money amount,
        Money resultingBalance) {

    public static final String DEBIT = "Debit";
    public static final String CREDIT = "Credit";

    public StatementRow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(resultingBalance, "resultingBalance");

        if (!DEBIT.equals(debitOrCredit) && !CREDIT.equals(debitOrCredit)) {
            // Rule L8a names these two words. An internal kind leaking into this field is the
            // exact defect the rule was written against.
            throw new IllegalArgumentException(
                    "a statement row is Debit or Credit, not '" + debitOrCredit + "'");
        }
    }

    public static StatementRow of(LedgerEntry entry, String description) {
        return new StatementRow(
                entry.voucherDate(),
                description,
                entry.isCredit() ? CREDIT : DEBIT,
                entry.settlementNo() != null && !entry.settlementNo().isBlank()
                        ? entry.settlementNo() : entry.voucherNo(),
                entry.isCredit() ? entry.credit() : entry.debit(),
                entry.closingBalance());
    }
}
