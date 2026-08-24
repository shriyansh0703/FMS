package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.FmsInvariantException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The idempotency key carried on a payout instruction (lld-backend.md §6.3a).
 *
 * <p><b>Why this is a type and not a {@code long}.</b> TechExcel's
 * {@code Payout_Request_Addition} types {@code UserRefNo} as an Integer of length 20, so a
 * composite key must serialise into at most twenty decimal digits. An encoding that
 * silently overflowed or truncated would deduplicate one account's payout against
 * another's — a missing payout, with no error anywhere. Putting the arithmetic and its
 * bound in one place, with an assertion, is what stops that being invented per call site.
 *
 * <pre>
 *   UserRefNo = (instructionSeq * 100000) + runDateOrdinal
 *
 *   instructionSeq   15 digits available
 *   runDateOrdinal    5 digits — days since the epoch below, giving 99,999 days
 * </pre>
 *
 * <p>The two components occupy disjoint decimal positions, so no two distinct
 * (instruction, run date) pairs can collide.
 *
 * <p><b>On what this key does and does not buy.</b> It makes a re-run reissue an identical
 * reference, which is what lets the end-of-day run <i>query</i> the prior payment status
 * before reissuing. It does not, on its own, guarantee the rail refuses a duplicate:
 * TechExcel's duplication validation returns {@code Input_Value_Validation}, the same code
 * as an input-value rejection, with the same unspecified description — verified against the
 * contract on 21 Aug 2026. That is why the run reads before it reissues, and why that read
 * must not be removed as redundant.
 */
public record InstructionKey(long value) {

    /** Days are counted from here so the ordinal fits five digits for the next 273 years. */
    static final LocalDate ORDINAL_EPOCH = LocalDate.of(2026, 1, 1);

    static final long RUN_DATE_MULTIPLIER = 100_000L;
    static final long MAX_RUN_DATE_ORDINAL = 99_999L;

    /**
     * The largest instruction sequence this encoding can carry.
     *
     * <p>{@code UserRefNo} is twenty digits, but {@code long} tops out at ~9.22 × 10^18 —
     * nineteen digits — so the binding constraint is the Java type, not the vendor field.
     * Any value a {@code long} holds already fits {@code UserRefNo}, which means the failure
     * to guard against is arithmetic overflow rather than field-width truncation.
     *
     * <p><b>The run-date ordinal has to be subtracted first.</b> A plain
     * {@code Long.MAX_VALUE / RUN_DATE_MULTIPLIER} leaves room for the multiplication but not
     * for the ordinal added after it, so that value overflowed for every run date from ordinal
     * 75,808 onward — the constant claimed to be the largest encodable sequence while failing
     * on most dates.
     *
     * <p>At roughly 500 payouts a day this ceiling is not a constraint anyone will meet; it
     * is asserted because the consequence of passing it silently is a key that collides with
     * a different instruction.
     */
    static final long MAX_INSTRUCTION_SEQ =
            (Long.MAX_VALUE - MAX_RUN_DATE_ORDINAL) / RUN_DATE_MULTIPLIER;

    public InstructionKey {
        if (value < 0L) {
            throw new FmsInvariantException("instruction_key_negative",
                    "an instruction key is never negative; got " + value);
        }
        // A record's canonical constructor cannot be made less accessible than the record, so
        // `new InstructionKey(5)` is reachable and would decode to sequence 0 on a date five days
        // after the epoch — a key of() would never produce and that instructionSeq() would report
        // as a payout request that does not exist. The floor closes that: any value below the
        // multiplier decodes to a zero sequence, and of() requires a positive one.
        if (value > 0L && value < RUN_DATE_MULTIPLIER) {
            throw new FmsInvariantException("instruction_key_malformed",
                    "an instruction key decodes to a positive sequence; " + value
                            + " would decode to sequence 0. Build keys with of() or forMandatedReturn().");
        }
    }

    /**
     * Build the key for one instruction on one run date.
     *
     * @param instructionSeq the payout request's identifier, or — for a mandated settlement
     *     return that has no request behind it — a value drawn from the dedicated sequence
     *     described in {@link #forMandatedReturn}. "Use the request id" has no answer for a
     *     sweep nobody requested, and reusing another account's id would be exactly the
     *     collision this encoding exists to prevent.
     * @param runDate the date of the end-of-day run issuing the instruction
     * @throws FmsInvariantException if the components are out of range or the arithmetic
     *     overflows. Never truncates: a truncated key is a valid-looking key for a
     *     different instruction, which is the worst available outcome.
     */
    public static InstructionKey of(long instructionSeq, LocalDate runDate) {
        if (instructionSeq <= 0L) {
            throw new FmsInvariantException("instruction_seq_invalid",
                    "instruction sequence must be positive; got " + instructionSeq);
        }
        if (instructionSeq > MAX_INSTRUCTION_SEQ) {
            throw new FmsInvariantException("instruction_seq_overflow",
                    "instruction sequence " + instructionSeq + " exceeds the encodable maximum "
                            + MAX_INSTRUCTION_SEQ);
        }

        long ordinal = runDateOrdinal(runDate);

        try {
            long value = Math.addExact(Math.multiplyExact(instructionSeq, RUN_DATE_MULTIPLIER), ordinal);
            return new InstructionKey(value);
        } catch (ArithmeticException e) {
            // Unreachable while the bounds above hold — MAX_INSTRUCTION_SEQ now reserves room
            // for the ordinal, so the largest permitted pair lands inside long. Kept because
            // the failure it prevents is silent and expensive, and a defensive branch on a
            // money path is cheaper than the incident.
            throw new FmsInvariantException("instruction_key_overflow",
                    "instruction key overflowed for seq " + instructionSeq + " on " + runDate, e);
        }
    }

    /**
     * The key for a mandated settlement return with no user request behind it.
     *
     * <p>Takes its sequence from a dedicated source whose range does not overlap payout
     * request identifiers, so the two namespaces cannot meet. The caller supplies the value;
     * this method exists to make the distinction explicit at the call site rather than
     * leaving a bare {@code of(...)} that reads as though a request id were being passed.
     */
    public static InstructionKey forMandatedReturn(long mandatedReturnSeq, LocalDate runDate) {
        return of(mandatedReturnSeq, runDate);
    }

    static long runDateOrdinal(LocalDate runDate) {
        long ordinal = ChronoUnit.DAYS.between(ORDINAL_EPOCH, runDate);
        if (ordinal < 0L || ordinal > MAX_RUN_DATE_ORDINAL) {
            throw new FmsInvariantException("run_date_out_of_range",
                    "run date " + runDate + " is outside the encodable window starting " + ORDINAL_EPOCH);
        }
        return ordinal;
    }

    /** The value as TechExcel's {@code UserRefNo} expects it. */
    public long userRefNo() {
        return this.value;
    }

    /** The instruction component, recoverable for support and reconciliation. */
    public long instructionSeq() {
        return this.value / RUN_DATE_MULTIPLIER;
    }

    /** The run date this instruction belongs to, recoverable from the key alone. */
    public LocalDate runDate() {
        return ORDINAL_EPOCH.plusDays(this.value % RUN_DATE_MULTIPLIER);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
