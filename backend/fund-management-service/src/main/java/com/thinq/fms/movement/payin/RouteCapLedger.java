package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Optional;

/**
 * What one account has already sent on one route today (REQ-701, Rule A12).
 *
 * <p><b>This system owns this ledger because nothing else can supply it.</b> Juspay's
 * {@code Get Balance} is the gateway's own balance, not a per-customer remaining cap — verified
 * against the vendor reference on 21 Aug 2026. Only this system knows what this account has sent.
 *
 * <p>The requirement is precise about the shape and the PRD says why: enforcing a daily cap
 * <b>per transaction</b> lets a trader pass the same amount twice and be refused by their bank
 * instead of by us. So the ledger accumulates and the check is against the accumulation.
 */
public interface RouteCapLedger {

    /**
     * Headroom left on a route today.
     *
     * @return empty when the route has no cap at all, which is "unbounded" rather than any figure.
     *     A caller reading empty as zero would refuse every NEFT payment
     */
    Optional<Money> remainingToday(AccountRef account, PaymentRoute route);

    /**
     * Record money sent, so the next check sees it.
     *
     * <p><b>Must be atomic against concurrent calls for the same (account, route, day).</b> V23's
     * primary key on {@code (account_id, route, usage_date)} makes the upsert the natural
     * implementation; a read-modify-write would let two simultaneous payments each see the old
     * total and both pass a cap that only one of them fits under.
     *
     * <p>Called after a payment is <i>confirmed</i>, not when it is attempted. An attempt that
     * fails at the bank consumed no headroom, and consuming it would refuse a trader's retry
     * against a limit they never actually used.
     */
    void record(AccountRef account, PaymentRoute route, Money sent);

    /** Headroom on a given day, for the quote path and for support answering "why was I refused". */
    Optional<Money> remainingOn(AccountRef account, PaymentRoute route, LocalDate day);
}
