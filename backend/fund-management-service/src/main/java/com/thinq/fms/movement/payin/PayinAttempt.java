package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One attempt to add money, through its whole life — <b>including the ones that failed</b>.
 *
 * <p>Rule L8: a deposit that failed is part of what happened to the account, and it is the entry a
 * trader most often needs to discuss. Its recorded reason stays with it. So this is never deleted
 * and never rewritten to look like it did not happen.
 *
 * <p>Mutable, like {@code PayoutRequest} and for the same reason: it is an entity with a lifecycle,
 * and the state machine's purpose is to constrain how it changes. State moves only through
 * {@link #recordOutcome} and {@link #reverse}.
 */
public final class PayinAttempt {

    private final long id;
    private final AccountRef account;
    private final Money amount;
    private final PaymentRoute route;
    private final Instant startedAt;

    private PayinState state;
    private String gatewayPaymentRef;
    private PayinOutcome outcome;
    private String sourceMasked;
    private Instant resolvedAt;
    private int version;

    /**
     * The version the database row carried when this instance was loaded, which is the value an
     * update must compare against.
     *
     * <p>{@link #version} counts mutations made since then and is what gets written; this stays
     * put. Keeping both is what lets a stateless repository do a compare-and-set without being
     * told how many times the caller mutated the entity in between — comparing against
     * {@code version} itself would always match, and the lost-update it is there to prevent would
     * go through silently.
     */
    private int loadedVersion;

    public PayinAttempt(long id,
                        AccountRef account,
                        Money amount,
                        PaymentRoute route,
                        Instant startedAt,
                        PayinState state,
                        int version) {
        this.id = id;
        this.account = Objects.requireNonNull(account, "account");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.route = Objects.requireNonNull(route, "route");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.state = Objects.requireNonNull(state, "state");
        this.version = version;
        this.loadedVersion = version;

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a payin is for a positive amount; got " + amount);
        }
    }

    /**
     * Rebuild an attempt from its stored row, bypassing the state machine.
     *
     * <p>Package-private and used by the repository alone. Loading is not a transition — a row in
     * CONFIRMED must come back as CONFIRMED without INITIATED being asserted first — so this is
     * the one way to set the mutable fields directly, and it is deliberately not reachable from
     * the orchestrator.
     */
    static PayinAttempt rehydrate(long id, AccountRef account, Money amount, PaymentRoute route,
                                  Instant startedAt, PayinState state, int version,
                                  String gatewayPaymentRef, PayinOutcome outcome,
                                  String sourceMasked, Instant resolvedAt) {
        PayinAttempt attempt = new PayinAttempt(id, account, amount, route, startedAt, state, version);
        attempt.gatewayPaymentRef = gatewayPaymentRef;
        attempt.outcome = outcome;
        attempt.sourceMasked = sourceMasked;
        attempt.resolvedAt = resolvedAt;
        return attempt;
    }

    /** The version the row carried at load, for the repository's compare-and-set. */
    int loadedVersion() {
        return this.loadedVersion;
    }

    /** Called by the repository once a write has landed, to re-anchor the comparison. */
    void writtenAt(int persistedVersion) {
        this.version = persistedVersion;
        this.loadedVersion = persistedVersion;
    }

    /**
     * Record the reference the gateway will know this attempt by, <b>before the gateway is
     * called</b>.
     *
     * <p>Assigning it afterwards looks equivalent and is not. A timeout abandons the wait, not the
     * call, so the gateway may create, hold and settle an order for a request whose response never
     * arrived. A row that does not yet carry its reference cannot be found by the confirmation that
     * follows, and the money is refused as belonging to a reference this system never issued —
     * which Rule A7 forbids. The reference derives from the attempt id, so it is knowable the
     * moment the row exists, and the only reason to wait is inattention.
     *
     * <p>Assignment does not move the state. The attempt stays INITIATED until the gateway
     * acknowledges it, because an unacknowledged attempt has not reached the gateway — it is
     * merely addressable if it turns out to have done so.
     */
    public void willUseGatewayReference(String gatewayPaymentRef) {
        Objects.requireNonNull(gatewayPaymentRef, "gatewayPaymentRef");
        if (this.state != PayinState.INITIATED) {
            throw new FmsInvariantException("payin_reference_assigned_late",
                    "attempt " + this.id + " is " + this.state
                            + "; its gateway reference must be assigned before the gateway is called");
        }
        this.gatewayPaymentRef = gatewayPaymentRef;
        this.version++;
    }

    /**
     * The gateway acknowledged the attempt, under the reference it was already assigned.
     *
     * <p>The equality check is not ceremony. {@code JuspayGateway.toOrder} currently echoes the
     * order id it was passed rather than reading it from the response body, which is what makes the
     * two values identical today. That coupling is invisible at both ends, so if the response ever
     * becomes the source, this fails loudly here instead of silently storing a reference the
     * confirmation will not match.
     */
    public void sentToGateway(String gatewayPaymentRef) {
        Objects.requireNonNull(gatewayPaymentRef, "gatewayPaymentRef");
        if (!gatewayPaymentRef.equals(this.gatewayPaymentRef)) {
            throw new FmsInvariantException("payin_reference_mismatch",
                    "attempt " + this.id + " was issued to the gateway as " + this.gatewayPaymentRef
                            + " but the gateway answered for " + gatewayPaymentRef);
        }
        transitionTo(PayinState.AT_GATEWAY);
    }

    /**
     * Apply what the gateway reported.
     *
     * <p><b>Idempotent by design, because Rule A6 says repeat confirmations are expected.</b> A
     * second confirmation of an already-confirmed payment changes nothing and produces no
     * additional entry — the caller is a gateway that will retry on anything that looks like a
     * failure, so returning success having done nothing is the correct response.
     *
     * @return true when this call changed the attempt, false when it was a repeat
     */
    public boolean recordOutcome(PayinOutcome reported, Instant at) {
        Objects.requireNonNull(reported, "reported");
        PayinState next = PayinState.forOutcome(reported);

        if (this.state == next) {
            // Rule A6's expected condition, not an error.
            return false;
        }
        if (this.state.isTerminal() && next != PayinState.REVERSED) {
            // A confirmation arriving after a terminal state is Rule A7 territory — money that
            // reached the firm is never discarded because the trader stopped watching. But a
            // FAILED attempt turning CONFIRMED is a contradiction the gateway should not produce,
            // and acting on it would credit money against an attempt already reported as failed.
            throw new FmsInvariantException("payin_terminal_state_changed",
                    "attempt " + this.id + " is " + this.state + " and cannot become " + next);
        }
        transitionTo(next);
        this.outcome = reported;
        if (next.isTerminal()) {
            this.resolvedAt = at;
        }
        return true;
    }

    /**
     * Undo a confirmed payin (REQ-206, Rule A10).
     *
     * <p>A reversal, never a deletion. Both this row and the compensating entry remain visible, and
     * the account may legitimately fall into debit if the money was already used — handled as a
     * debt by the health module rather than prevented by refusing the reversal.
     */
    public void reverse(Instant at) {
        if (this.state != PayinState.CONFIRMED) {
            throw new FmsInvariantException("payin_not_reversible",
                    "only a confirmed payin can be reversed; attempt " + this.id + " is " + this.state);
        }
        transitionTo(PayinState.REVERSED);
        this.resolvedAt = at;
    }

    public void recordSourceMasked(String sourceMasked) {
        // REQ-612: last four digits only. The full number is never stored and never rendered.
        this.sourceMasked = sourceMasked;
    }

    private void transitionTo(PayinState next) {
        if (!this.state.canTransitionTo(next)) {
            throw new FmsInvariantException("illegal_payin_transition",
                    "attempt " + this.id + " cannot move from " + this.state + " to " + next);
        }
        this.state = next;
        this.version++;
    }

    // ---- accessors ----

    public long id() {
        return this.id;
    }

    public AccountRef account() {
        return this.account;
    }

    public Money amount() {
        return this.amount;
    }

    public PaymentRoute route() {
        return this.route;
    }

    public PayinState state() {
        return this.state;
    }

    public Instant startedAt() {
        return this.startedAt;
    }

    public int version() {
        return this.version;
    }

    /** Rule A5: only a confirmed payin is money the account has. */
    public boolean affectsBalance() {
        return this.state.affectsBalance();
    }

    public Optional<String> gatewayPaymentRef() {
        return Optional.ofNullable(this.gatewayPaymentRef);
    }

    public Optional<PayinOutcome> outcome() {
        return Optional.ofNullable(this.outcome);
    }

    public Optional<String> sourceMasked() {
        return Optional.ofNullable(this.sourceMasked);
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(this.resolvedAt);
    }
}
