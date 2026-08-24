package com.thinq.fms.movement.payin;

import com.thinq.fms.ledgerview.EntryDescriptionMapper;
import com.thinq.fms.ledgerview.EntryKind;
import com.thinq.fms.ledgerview.InFlightMovementSource;
import com.thinq.fms.ledgerview.TransactionEntry;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Payin attempts the ledger does not carry, as movements the trader can see.
 *
 * <p>Implements {@link InFlightMovementSource} for the transaction list. Rule L8 requires failed
 * and cancelled deposits to stay in the history — they are the entries a trader most often needs to
 * discuss — and REQ-402 requires in-flight items shown with their status.
 *
 * <h2>What is deliberately excluded</h2>
 *
 * <p><b>Confirmed attempts.</b> A confirmed payin is in the ledger, and returning it from here too
 * would show one deposit twice. The filter is on {@link PayinState#affectsBalance()} rather than on
 * a list of states, so a future state that credits an account cannot be forgotten here.
 *
 * <p><b>Reversed attempts.</b> The reversal is a compensating ledger entry (Rule A10), so both the
 * original and the reversal are already in the ledger. Including the attempt row would make three
 * rows for two events.
 */
public final class PayinMovementSource implements InFlightMovementSource {

    /** No running balance applies: this money has not moved, so it changed no balance. */
    private static final Money NO_BALANCE = Money.ZERO;

    private final PayinAttemptRepository attempts;
    private final ZoneId zone;

    public PayinMovementSource(PayinAttemptRepository attempts, ZoneId zone) {
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public List<TransactionEntry> read(AccountRef account, LocalDate from, LocalDate to) {
        return this.attempts.inPeriod(account, from, to).stream()
                .filter(a -> !a.affectsBalance())
                .filter(a -> a.state() != PayinState.REVERSED)
                .map(this::toEntry)
                .toList();
    }

    private TransactionEntry toEntry(PayinAttempt attempt) {
        // A copy key per outcome, so the client explains a failure specifically rather than
        // generically — Rule A9a's six outcomes are not interchangeable and Rule A9c requires
        // whose problem it is to be named.
        String copyKey = attempt.outcome()
                .map(o -> "PAYIN_" + o.name())
                .orElse("PAYIN_" + attempt.state().name());

        var description = new EntryDescriptionMapper.Description(
                EntryKind.PAYIN, copyKey,
                Map.of("amountPaise", Long.toString(attempt.amount().paise()),
                        "route", attempt.route().name()),
                // The attempt's own reference, not a ledger voucher — there is no ledger entry.
                "PAYIN-" + attempt.id(),
                // Rule L4: the trader started this, whatever became of it.
                true);

        return new TransactionEntry(
                "PAYIN-" + attempt.id(),
                LocalDate.ofInstant(attempt.startedAt(), this.zone),
                EntryKind.PAYIN,
                description,
                attempt.amount(),
                true,
                NO_BALANCE,
                null,
                null,
                null,
                attempt.state().name());
    }
}
