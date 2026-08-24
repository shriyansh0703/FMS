package com.thinq.fms.movement.payout;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The eight states a withdrawal request can occupy, and the only transitions between them
 * (lld-backend.md §7.5).
 *
 * <p><b>Why a transition table and not a class per state.</b> Eight states with a fixed legal
 * transition set, where the <i>illegal</i> transitions are the interesting ones. A table is
 * diffable in review and testable in one loop; eight state classes would be ceremony around a
 * lookup.
 *
 * <p>This enum must stay in step with V21's {@code fms_payout_state_vocabulary} constraint and
 * with {@code fms_payout_one_open_per_account}'s predicate. Adding a state here means adding it
 * to the constraint, and — only if it is an <i>open</i> state — to the index predicate too.
 */
public enum PayoutState {

    /** The trader has submitted. Rule W3: this reserves nothing. */
    ACCEPTED(false, true),

    /** A rail outage moved it here. It is still open, and the next run will try again. */
    QUEUED_FOR_RUN(false, true),

    /** The run has instructed the rail. Still open: the money has not landed or failed yet. */
    INSTRUCTED(false, true),

    /** The rail sent the full amount. */
    PAID(true, false),

    /** The rail sent less than was requested. REQ-308 requires the gap explained. */
    PARTLY_PAID(true, false),

    /** The rail sent nothing — {@code Reject = 1}, or nothing was available. */
    NOTHING_SENT(true, false),

    /**
     * The bank refused the money after the rail had sent it.
     *
     * <p>Reachable only from {@code PAID} or {@code PARTLY_PAID}, which is why the table below
     * allows one transition out of a terminal state. This is not a deletion: the original entry
     * stands and a compensating entry is added (Rule W7, Rule L2).
     */
    RETURNED(true, false),

    /** The trader cancelled before the money left. Permitted while open, per REQ-619. */
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean open;

    PayoutState(boolean terminal, boolean open) {
        this.terminal = terminal;
        this.open = open;
    }

    /**
     * Whether the request has reached an end state.
     *
     * <p>{@code RETURNED} is terminal and is also reachable <i>from</i> a terminal state. The
     * two facts coexist deliberately — see the transition table.
     */
    public boolean isTerminal() {
        return this.terminal;
    }

    /**
     * Whether this state counts as an open request for Rule W4.
     *
     * <p><b>Must match V21's {@code fms_payout_one_open_per_account} predicate exactly.</b> The
     * index is the actual guarantee — a service check has a window and a constraint does not —
     * so this method is for presentation and for the test that asserts the two agree, never for
     * pre-checking before a write.
     */
    public boolean isOpen() {
        return this.open;
    }

    private static final Map<PayoutState, Set<PayoutState>> LEGAL;

    static {
        EnumMap<PayoutState, Set<PayoutState>> t = new EnumMap<>(PayoutState.class);
        t.put(ACCEPTED,       EnumSet.of(CANCELLED, INSTRUCTED, QUEUED_FOR_RUN));
        t.put(QUEUED_FOR_RUN, EnumSet.of(CANCELLED, INSTRUCTED));
        t.put(INSTRUCTED,     EnumSet.of(QUEUED_FOR_RUN, PAID, PARTLY_PAID, NOTHING_SENT));
        t.put(PAID,           EnumSet.of(RETURNED));
        t.put(PARTLY_PAID,    EnumSet.of(RETURNED));
        t.put(NOTHING_SENT,   EnumSet.noneOf(PayoutState.class));
        t.put(RETURNED,       EnumSet.noneOf(PayoutState.class));
        t.put(CANCELLED,      EnumSet.noneOf(PayoutState.class));
        LEGAL = Collections.unmodifiableMap(t);
    }

    /** The states reachable from this one. Empty for a state nothing follows. */
    public Set<PayoutState> allowedNext() {
        return LEGAL.get(this);
    }

    public boolean canTransitionTo(PayoutState next) {
        return LEGAL.get(this).contains(next);
    }

    /**
     * The entry point for the first state of a request's life.
     *
     * <p>Separate from {@link #canTransitionTo} because "no previous state" is not the same
     * question as "which state follows this one", and folding the two would let a null
     * masquerade as a legal predecessor.
     */
    public static boolean isLegalInitialState(PayoutState first) {
        return first == ACCEPTED;
    }
}
