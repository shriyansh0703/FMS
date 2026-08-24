package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One instruction to move money out, as handed to a {@link PayoutRail}.
 *
 * <p>Carries {@link InstructionKey} rather than a bare identifier because the key is what makes
 * a re-run after a crash reissue an identical reference, which is what lets the end-of-day run
 * query the prior payment's status before reissuing (lld-backend.md §6.3).
 *
 * @param key            the idempotency key, serialised into the rail's own reference field
 * @param account        whose money is moving
 * @param amount         what to send. May be less than the trader requested — a settlement
 *                       sends what is available, per Rule W3
 * @param destinationRef the destination fixed at request time (Rule W12). A later change to the
 *                       trader's bank accounts never redirects an instruction already in flight
 * @param runDate        the end-of-day run this instruction belongs to
 */
public record PaymentInstruction(
        InstructionKey key,
        AccountRef account,
        Money amount,
        String destinationRef,
        LocalDate runDate) {

    public PaymentInstruction {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(destinationRef, "destinationRef");
        Objects.requireNonNull(runDate, "runDate");

        if (!amount.isPositive()) {
            // A zero-amount instruction is not a no-op at a payment rail: it is a request the
            // rail may accept, log and reference, which then has to be reconciled against a
            // payout nobody made. The run declines to instruct instead.
            throw new IllegalArgumentException("a payment instruction moves a positive amount; got " + amount);
        }
    }
}
