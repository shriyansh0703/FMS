package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One entry as the trader sees it: described, classified, and paired with its reversal if it has
 * one.
 *
 * @param voucherNo        the back-office reference, shown as secondary detail
 * @param date             when it was posted
 * @param kind             what sort of event it is
 * @param description      the resolved copy key and its parameters. Never a settlement identifier
 * @param amount           the magnitude
 * @param credit           whether money came in
 * @param runningBalance   from TechExcel's {@code CLOSING_AMT}. <b>Never accumulated by FMS</b>
 *                         (HLD §9.1b) — two systems computing one running balance is Rule B12's
 *                         failure mode
 * @param segment          REQ-108: recorded from day one so a later split is a display change
 * @param reversedBy       the voucher of the entry that reverses this one, if any
 * @param reverses         the voucher this entry reverses, if it is a reversal
 * @param status           the movement's current state where this row is an in-flight or failed
 *                         attempt rather than a posted ledger entry. Null for a ledger entry,
 *                         which has already happened and has no status to report
 */
public record TransactionEntry(
        String voucherNo,
        LocalDate date,
        EntryKind kind,
        EntryDescriptionMapper.Description description,
        Money amount,
        boolean credit,
        Money runningBalance,
        String segment,
        String reversedBy,
        String reverses,
        String status) {

    public TransactionEntry {
        Objects.requireNonNull(voucherNo, "voucherNo");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
    }

    /**
     * Whether a later entry reverses this one.
     *
     * <p>REQ-404 requires the original flagged as reversed so a reader scanning the list does not
     * count it twice. Rule L2 keeps both entries — a correction is a compensating entry, never a
     * deletion — which is exactly why the flag is needed.
     */
    public boolean isReversed() {
        return this.reversedBy != null;
    }

    public boolean isReversal() {
        return this.reverses != null;
    }

    public Optional<String> reversedByIfAny() {
        return Optional.ofNullable(this.reversedBy);
    }

    /** Rule L4: did the trader cause this? Carried on the description, where the mapper decides. */
    public boolean userCaused() {
        return this.description.userCaused();
    }

    /**
     * Whether this row is a movement that has not finished.
     *
     * <p>REQ-402 requires items not yet complete shown with their status. A posted ledger entry has
     * no status because it has already happened; an attempt in flight does.
     */
    public boolean isInFlight() {
        return this.status != null && PENDING_STATUSES.contains(this.status);
    }

    public Optional<String> statusIfAny() {
        return Optional.ofNullable(this.status);
    }

    private static final java.util.Set<String> PENDING_STATUSES =
            java.util.Set.of("INITIATED", "AT_GATEWAY", "AWAITING_BANK");

    /** A ledger entry: something that already happened, so no status. */
    public static TransactionEntry posted(String voucherNo, LocalDate date, EntryKind kind,
                                          EntryDescriptionMapper.Description description,
                                          Money amount, boolean credit, Money runningBalance,
                                          String segment, String reversedBy, String reverses) {
        return new TransactionEntry(voucherNo, date, kind, description, amount, credit,
                runningBalance, segment, reversedBy, reverses, null);
    }
}
