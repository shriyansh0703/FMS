package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One withdrawal request, through its whole life.
 *
 * <p><b>Mutable, and deliberately so.</b> Almost everything else in this system is a record,
 * because value semantics make reasoning easier. This is not a value: it is an entity with an
 * identity and a lifecycle, and the state machine's whole purpose is to constrain how it changes.
 * Modelling it immutably would move transition validation to whatever code assembled the next
 * instance, which is exactly where it would get skipped.
 *
 * <p>State changes go through {@link #transitionTo}, which enforces §7.5's table. There is no
 * setter for state.
 */
public final class PayoutRequest {

    private final long id;
    private final AccountRef account;
    private final Money amount;
    private final String destinationRef;
    private final String destinationMasked;
    private final String fmsReference;

    /** Rule W11: what was true when the trader committed, so a later question has an answer. */
    private final Money withdrawableAtRequest;
    private final LocalDate arrivalDateQuoted;
    private final Instant requestedAt;

    private PayoutState state;
    private Money withdrawableAtSettle;
    private Money amountSent;
    private SettlementReasonCode settlementReasonCode;
    private String settlementReasonText;
    private String bankReference;
    private LocalDate creditedOn;
    private Instant closedAt;
    private int version;

    /**
     * The version the row carried when this instance was loaded — the value an update compares
     * against. {@link #version} counts mutations since; this stays put, so a stateless repository
     * can do a compare-and-set without knowing how many times the caller mutated the entity.
     */
    private int loadedVersion;

    public PayoutRequest(long id,
                         AccountRef account,
                         Money amount,
                         String destinationRef,
                         String destinationMasked,
                         String fmsReference,
                         Money withdrawableAtRequest,
                         LocalDate arrivalDateQuoted,
                         Instant requestedAt,
                         PayoutState state,
                         int version) {
        this.id = id;
        this.account = Objects.requireNonNull(account, "account");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.destinationRef = Objects.requireNonNull(destinationRef, "destinationRef");
        this.destinationMasked = Objects.requireNonNull(destinationMasked, "destinationMasked");
        this.fmsReference = Objects.requireNonNull(fmsReference, "fmsReference");
        this.withdrawableAtRequest = Objects.requireNonNull(withdrawableAtRequest, "withdrawableAtRequest");
        this.arrivalDateQuoted = Objects.requireNonNull(arrivalDateQuoted, "arrivalDateQuoted");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.state = Objects.requireNonNull(state, "state");
        this.version = version;
        this.loadedVersion = version;

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a withdrawal request is for a positive amount; got " + amount);
        }
    }

    /**
     * Move to a new state, or refuse.
     *
     * @throws FmsInvariantException when §7.5's table forbids the transition. An invariant rather
     *     than a client error, because the caller is this system's own code: a forbidden
     *     transition means the orchestrator reached a state it believes is impossible, and
     *     continuing would move money on a false premise. It pages.
     */
    public void transitionTo(PayoutState next) {
        Objects.requireNonNull(next, "next");
        if (!this.state.canTransitionTo(next)) {
            throw new FmsInvariantException("illegal_state_transition",
                    "request " + this.id + " cannot move from " + this.state + " to " + next);
        }
        this.state = next;
        this.version++;
    }

    /**
     * Record what the rail did, moving to the outcome's terminal state.
     *
     * <p>Rule W11's second half: the withdrawable figure at settlement is stamped alongside the
     * one at request, because "why did I receive less than I asked for?" is answered by the
     * difference between them.
     */
    public void recordSettlement(SettlementOutcome outcome, Money withdrawableAtSettle, Instant at) {
        Objects.requireNonNull(outcome, "outcome");

        if (!outcome.amountRequested().equals(this.amount)) {
            // The outcome belongs to a different instruction. Applying it would attribute another
            // request's payment to this one.
            throw new FmsInvariantException("settlement_amount_mismatch",
                    "settlement for " + outcome.amountRequested() + " applied to a request for " + this.amount);
        }

        transitionTo(outcome.state());
        this.withdrawableAtSettle = withdrawableAtSettle;
        this.amountSent = outcome.amountSent();
        this.settlementReasonCode = outcome.reasonCode();
        this.settlementReasonText = outcome.reasonText();
        this.bankReference = outcome.bankReference();
        this.creditedOn = outcome.creditedOn();
        this.closedAt = at;
    }

    public void cancel(Instant at) {
        transitionTo(PayoutState.CANCELLED);
        this.amountSent = Money.ZERO;
        this.closedAt = at;
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

    public String destinationRef() {
        return this.destinationRef;
    }

    public String destinationMasked() {
        return this.destinationMasked;
    }

    public String fmsReference() {
        return this.fmsReference;
    }

    public Money withdrawableAtRequest() {
        return this.withdrawableAtRequest;
    }

    public LocalDate arrivalDateQuoted() {
        return this.arrivalDateQuoted;
    }

    public Instant requestedAt() {
        return this.requestedAt;
    }

    public PayoutState state() {
        return this.state;
    }

    /**
     * Rebuild a request from its stored row, bypassing the state machine.
     *
     * <p>Package-private, for the repository alone. Loading is not a transition: a row in PAID must
     * return as PAID without ACCEPTED being asserted first.
     */
    static PayoutRequest rehydrate(long id, AccountRef account, Money amount, String destinationRef,
                                   String destinationMasked, String fmsReference,
                                   Money withdrawableAtRequest, LocalDate arrivalDateQuoted,
                                   Instant requestedAt, PayoutState state, int version,
                                   Money withdrawableAtSettle, Money amountSent,
                                   SettlementReasonCode settlementReasonCode,
                                   String settlementReasonText, String bankReference,
                                   LocalDate creditedOn, Instant closedAt) {
        PayoutRequest r = new PayoutRequest(id, account, amount, destinationRef, destinationMasked,
                fmsReference, withdrawableAtRequest, arrivalDateQuoted, requestedAt, state, version);
        r.withdrawableAtSettle = withdrawableAtSettle;
        r.amountSent = amountSent;
        r.settlementReasonCode = settlementReasonCode;
        r.settlementReasonText = settlementReasonText;
        r.bankReference = bankReference;
        r.creditedOn = creditedOn;
        r.closedAt = closedAt;
        return r;
    }

    // Package-private reads of the settlement fields, for the repository. They are not public
    // because nothing outside this package should read a half-settled request field by field —
    // callers use the state and the outcome, which cannot be inconsistent with each other.

    Money withdrawableAtSettleOrNull() {
        return this.withdrawableAtSettle;
    }

    Money amountSentOrNull() {
        return this.amountSent;
    }

    SettlementReasonCode settlementReasonCodeOrNull() {
        return this.settlementReasonCode;
    }

    String settlementReasonTextOrNull() {
        return this.settlementReasonText;
    }

    String bankReferenceOrNull() {
        return this.bankReference;
    }

    LocalDate creditedOnOrNull() {
        return this.creditedOn;
    }

    Instant closedAtOrNull() {
        return this.closedAt;
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

    public int version() {
        return this.version;
    }

    public Optional<Money> amountSent() {
        return Optional.ofNullable(this.amountSent);
    }

    public Optional<Money> withdrawableAtSettle() {
        return Optional.ofNullable(this.withdrawableAtSettle);
    }

    public Optional<SettlementReasonCode> settlementReasonCode() {
        return Optional.ofNullable(this.settlementReasonCode);
    }

    /** Never rendered to a trader — an unmapped back-office phrase is not user-facing copy. */
    public Optional<String> settlementReasonText() {
        return Optional.ofNullable(this.settlementReasonText);
    }

    /** Rule C8: the bank's own reference, never ours. */
    public Optional<String> bankReference() {
        return Optional.ofNullable(this.bankReference);
    }

    public Optional<LocalDate> creditedOn() {
        return Optional.ofNullable(this.creditedOn);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(this.closedAt);
    }

    /** Whether this request still counts against Rule W4's one-open-request limit. */
    public boolean isOpen() {
        return this.state.isOpen();
    }
}
