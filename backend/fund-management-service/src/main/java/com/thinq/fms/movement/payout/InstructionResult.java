package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * What the rail knows about an instruction right now.
 *
 * <p><b>Why this is not simply a {@link SettlementOutcome}.</b> TechExcel's
 * {@code Payout_Request_Addition} puts a payment entry into an authorisation queue; it does not
 * execute it. The status view then reports {@code AUTHO} — {@code 1} authorised, {@code 0} not —
 * alongside {@code AUTH_DUE_AMT}, the authorised amount.
 *
 * <p>So immediately after a successful post, the row exists with {@code AUTHO = 0}, no authorised
 * amount and no rejection. That is not an outcome. Reading it as one produced
 * {@code PARTLY_PAID} with nothing sent — a terminal state that closed the request, told the
 * trader they had received zero, and left the money where it was.
 *
 * <p>A settled outcome and a pending one demand opposite actions, so the type makes the caller
 * choose rather than letting a null or a zero decide for them.
 */
public sealed interface InstructionResult {

    /** The rail has finished with this instruction. */
    record Settled(SettlementOutcome outcome) implements InstructionResult {
        public Settled {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * The rail holds the instruction but has not authorised it yet.
     *
     * <p>The request stays {@code INSTRUCTED} and a later run resolves it — which is exactly the
     * path §6.3's read-before-reissue already exists to serve. Nothing is retried and nothing is
     * reissued: the instruction is with the rail, and issuing it again is how one payout becomes
     * two.
     *
     * @param requested what was instructed, so a caller can report the in-flight amount without a
     *     second lookup
     */
    record PendingAuthorisation(Money requested) implements InstructionResult {
        public PendingAuthorisation {
            Objects.requireNonNull(requested, "requested");
        }
    }

    /** The settled outcome, or empty while authorisation is pending. */
    default Optional<SettlementOutcome> settledOutcome() {
        return this instanceof Settled s ? Optional.of(s.outcome()) : Optional.empty();
    }

    default boolean isPending() {
        return this instanceof PendingAuthorisation;
    }
}
