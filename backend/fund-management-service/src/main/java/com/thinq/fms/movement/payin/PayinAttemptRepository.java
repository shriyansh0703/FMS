package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.AccountRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for payin attempts.
 *
 * <p>Like the payout repository, every method takes an {@link AccountRef} — authorisation is per
 * object and a method that could be called without one is a method a caller can forget to scope.
 */
public interface PayinAttemptRepository {

    PayinAttempt save(PayinAttempt attempt);

    Optional<PayinAttempt> findFor(AccountRef account, long id);

    /**
     * The attempt a gateway reference belongs to.
     *
     * <p><b>Not scoped by account, and that is deliberate.</b> A gateway callback arrives carrying
     * only the payment reference; there is no authenticated trader on that request. The reference
     * is the gateway's own and is globally unique, and V22's partial unique index on it is what
     * makes this lookup single-valued.
     */
    Optional<PayinAttempt> findByGatewayRef(String gatewayPaymentRef);

    /**
     * The most recent attempt that reached a confirmed state.
     *
     * <p>REQ-201 and Rule A1: the amount field opens on what the trader last added, and returns
     * empty for a first deposit rather than defaulting to a number they never chose.
     */
    Optional<PayinAttempt> lastConfirmedFor(AccountRef account);

    /** Attempts in a period, for the movements view. Failed ones included — Rule L8. */
    List<PayinAttempt> inPeriod(AccountRef account, LocalDate from, LocalDate to);
}
