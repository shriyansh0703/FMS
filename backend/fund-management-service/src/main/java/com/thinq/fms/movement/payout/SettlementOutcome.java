package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link PayoutRail} actually did with one instruction.
 *
 * <p>Shaped by REQ-308, which requires three things together: the amount requested, the amount
 * sent, and the deduction accounting for any gap. A rail that reported only success or failure
 * would leave "why did I receive less than I asked for?" unanswerable, which is the question
 * this record exists to make answerable months later.
 *
 * @param state          the terminal state this outcome puts the request in
 * @param amountRequested what the instruction asked the rail to send
 * @param amountSent     what the rail reports it sent. Zero for {@code NOTHING_SENT}, and never
 *                       greater than {@code amountRequested}
 * @param reasonCode     the mapped reason for any gap. {@code NONE} when nothing was deducted
 * @param reasonText     the vendor's verbatim phrase when it did not map (OA-4). Retained for
 *                       operational alerting, never rendered to a trader
 * @param bankReference  the bank's own transfer reference, when the rail has one. Rule C8: this
 *                       and the FMS reference are different fields and never share a value —
 *                       giving a trader ours sends them to a bank the reference means nothing to
 * @param creditedOn     the date the money actually reached the account, when known. REQ-303
 *                       compares this against the quoted date
 */
public record SettlementOutcome(
        PayoutState state,
        Money amountRequested,
        Money amountSent,
        SettlementReasonCode reasonCode,
        String reasonText,
        String bankReference,
        LocalDate creditedOn) {

    public SettlementOutcome {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(amountRequested, "amountRequested");
        Objects.requireNonNull(amountSent, "amountSent");
        Objects.requireNonNull(reasonCode, "reasonCode");

        if (amountSent.isNegative()) {
            throw new IllegalArgumentException("amountSent cannot be negative; got " + amountSent);
        }
        // Enforced here as well as by V21's CHECK constraint. The database stops a bad row being
        // stored; this stops a bad value being acted on before anything is stored, which is
        // where the trader-facing message is generated from.
        if (amountSent.compareTo(amountRequested) > 0) {
            throw new IllegalArgumentException(
                    "a settlement may send less than requested but never more; sent " + amountSent
                            + " against " + amountRequested);
        }
        if (!state.isTerminal()) {
            throw new IllegalArgumentException(
                    "a settlement outcome reports a terminal state; got " + state);
        }
    }

    public Optional<String> bankReferenceIfPresent() {
        return Optional.ofNullable(this.bankReference);
    }

    public Optional<LocalDate> creditedOnIfKnown() {
        return Optional.ofNullable(this.creditedOn);
    }

    /** The gap REQ-308 requires explained. Zero when the full amount was sent. */
    public Money shortfallAgainstRequest() {
        return this.amountRequested.minus(this.amountSent);
    }
}
