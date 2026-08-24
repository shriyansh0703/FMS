package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The states a payin attempt occupies.
 *
 * <p><b>The interesting one is {@link #AWAITING_BANK}.</b> Rule A9b insists it is neither success
 * nor failure, because the recovery is the opposite of a failure's: wait, and specifically do not
 * retry. Modelling it as a flavour of failure — the obvious shortcut — is what produces a
 * "try again" button that debits a trader twice.
 *
 * <p>Rule A5 governs which of these affects a balance: <b>only {@link #CONFIRMED}</b>. Every other
 * state is money in flight, visible and attributable but contributing to nothing.
 */
public enum PayinState {

    /** The attempt exists; nothing has been sent to the gateway yet. */
    INITIATED(false, false),

    /** The gateway holds it and the trader is completing payment. */
    AT_GATEWAY(false, false),

    /**
     * The bank has not answered.
     *
     * <p>Rule A9b's state. Terminal in the sense that this system stops acting, but <b>not</b> a
     * resolution: the reconciler keeps asking, and no retry is offered while it stands.
     */
    AWAITING_BANK(false, false),

    /** The money arrived. The only state that affects a balance (Rule A5). */
    CONFIRMED(true, true),

    /** The attempt failed for one of Rule A9a's reasons. Stays in history (Rule L8). */
    FAILED(true, false),

    /** The trader cancelled before approving. Stays in history. */
    CANCELLED(true, false),

    /**
     * A confirmed payin was undone.
     *
     * <p>Rule A10: a deposit is reversed, never deleted. Both entries remain visible, and the
     * account may legitimately fall into debit as a result — handled as a debt rather than
     * prevented by refusing the reversal.
     */
    REVERSED(true, false);

    private final boolean terminal;
    private final boolean affectsBalance;

    PayinState(boolean terminal, boolean affectsBalance) {
        this.terminal = terminal;
        this.affectsBalance = affectsBalance;
    }

    public boolean isTerminal() {
        return this.terminal;
    }

    /** Rule A5. Only a confirmed payin is money the account has. */
    public boolean affectsBalance() {
        return this.affectsBalance;
    }

    /**
     * Whether this attempt is still in flight, for the movements view.
     *
     * <p>REQ-402 requires items not yet complete shown with their status, so this is what
     * distinguishes "still happening" from "finished" in the list.
     */
    public boolean isInFlight() {
        return !this.terminal;
    }

    private static final Map<PayinState, Set<PayinState>> LEGAL;

    static {
        EnumMap<PayinState, Set<PayinState>> t = new EnumMap<>(PayinState.class);
        // INITIATED resolves directly, and not only through AT_GATEWAY. A createOrder call that
        // times out abandons the wait, not the call (AbstractVendorGateway), so Juspay may hold and
        // settle an order this system never saw acknowledged. Rule A7 puts that money beyond
        // discarding, which means the confirmation has to be able to land on the row as it stands.
        // Without CONFIRMED and AWAITING_BANK here, storing the gateway reference early would only
        // move the refusal from "unknown reference" to "illegal transition".
        t.put(INITIATED,     EnumSet.of(AT_GATEWAY, CONFIRMED, AWAITING_BANK, FAILED, CANCELLED));
        // AWAITING_BANK is reachable from the gateway, and resolves either way afterwards —
        // Rule A7: money that reached the firm is never discarded because the trader stopped
        // watching, so a late confirmation must still be able to land.
        t.put(AT_GATEWAY,    EnumSet.of(CONFIRMED, FAILED, CANCELLED, AWAITING_BANK));
        t.put(AWAITING_BANK, EnumSet.of(CONFIRMED, FAILED));
        t.put(CONFIRMED,     EnumSet.of(REVERSED));
        t.put(FAILED,        EnumSet.noneOf(PayinState.class));
        t.put(CANCELLED,     EnumSet.noneOf(PayinState.class));
        t.put(REVERSED,      EnumSet.noneOf(PayinState.class));
        LEGAL = Collections.unmodifiableMap(t);
    }

    public Set<PayinState> allowedNext() {
        return LEGAL.get(this);
    }

    public boolean canTransitionTo(PayinState next) {
        return LEGAL.get(this).contains(next);
    }

    /** The state an outcome puts an attempt in. */
    public static PayinState forOutcome(PayinOutcome outcome) {
        return switch (outcome) {
            case CONFIRMED -> CONFIRMED;
            case CANCELLED_BY_USER -> CANCELLED;
            case AWAITING_BANK, UNKNOWN -> AWAITING_BANK;
            case BANK_DECLINED, INSUFFICIENT_FUNDS_AT_BANK, ABOVE_BANK_LIMIT, SERVICE_UNREACHABLE
                    -> FAILED;
        };
    }
}
