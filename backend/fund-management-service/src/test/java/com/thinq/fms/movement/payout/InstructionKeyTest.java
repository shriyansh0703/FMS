package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.FmsInvariantException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A collision here is a silently missing payout — one account's instruction deduplicated
 * against another's, with no error raised anywhere. That is why this is property-tested
 * across the full component ranges rather than checked with a few examples.
 */
class InstructionKeyTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 8, 21);

    @Test
    @DisplayName("distinct (instruction, run date) pairs never collide")
    void distinctPairsNeverCollide() {
        Set<Long> seen = new HashSet<>();
        Random random = new Random(20260821L);

        for (int i = 0; i < 20_000; i++) {
            long seq = random.nextLong(1L, 5_000_000_000L);
            LocalDate date = InstructionKey.ORDINAL_EPOCH.plusDays(random.nextLong(0L, 99_999L));

            InstructionKey key = InstructionKey.of(seq, date);

            // Round-tripping proves the components occupy disjoint positions: if they
            // overlapped, one of these would come back wrong long before a collision showed up.
            assertThat(key.instructionSeq()).isEqualTo(seq);
            assertThat(key.runDate()).isEqualTo(date);

            seen.add(key.value());
        }

        assertThat(seen).as("every generated pair produced a distinct key").hasSize(20_000);
    }

    @Test
    @DisplayName("adjacent sequences cannot collide across the ordinal boundary")
    void adjacentSequencesCannotCollideAcrossTheOrdinalBoundary() {
        // The random test above is weaker than it looks. Over a space of ~5e9, 20,000 draws
        // collide with probability about 4% — so a BROKEN additive encoding (seq + ordinal
        // instead of seq * 100000 + ordinal) would pass its hasSize assertion roughly 96% of the
        // time. The round-trip assertions are what actually carry it.
        //
        // This test attacks the boundary directly: the only way two distinct pairs can share a
        // key is if the ordinal component can reach into the sequence component. Exhausting the
        // top and bottom of the ordinal range against adjacent sequences proves it cannot.
        Set<Long> keys = new HashSet<>();
        int pairs = 0;

        for (long seq : new long[]{1L, 2L, 999L, 1_000L, 4_294_967_296L}) {
            for (long ordinal : new long[]{0L, 1L, 99_998L, InstructionKey.MAX_RUN_DATE_ORDINAL}) {
                keys.add(InstructionKey.of(seq, InstructionKey.ORDINAL_EPOCH.plusDays(ordinal)).value());
                pairs++;
            }
        }
        // An additive encoding collapses (1, 99_999) onto (100_000, 0) and similar. A positional
        // one cannot, so every pair must be distinct.
        assertThat(keys).as("no two distinct (sequence, ordinal) pairs share a key").hasSize(pairs);
    }

    @Test
    @DisplayName("the canonical constructor refuses a value of() would never produce")
    void canonicalConstructorRefusesMalformedValues() {
        // A record's canonical constructor cannot be hidden, so this is reachable. Without the
        // guard it decodes to sequence 0 — a payout request that does not exist.
        assertThatThrownBy(() -> new InstructionKey(5L))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("instruction_key_malformed"));

        // Zero stays legal: it is the identity a record needs for equals and hashCode, and it
        // decodes to sequence 0 on the epoch, which of() also refuses.
        assertThat(new InstructionKey(0L).value()).isZero();
    }

    @Test
    @DisplayName("the same instruction on the same run date always produces the same key")
    void sameInputsProduceSameKey() {
        // This is the whole point: a re-run after a crash must reissue an identical
        // reference so the prior payment status can be looked up by it.
        InstructionKey first = InstructionKey.of(4_242L, RUN_DATE);
        InstructionKey second = InstructionKey.of(4_242L, RUN_DATE);

        assertThat(first).isEqualTo(second);
        assertThat(first.userRefNo()).isEqualTo(second.userRefNo());
    }

    @Test
    @DisplayName("the same instruction on a different run date produces a different key")
    void differentRunDateProducesDifferentKey() {
        assertThat(InstructionKey.of(4_242L, RUN_DATE))
                .isNotEqualTo(InstructionKey.of(4_242L, RUN_DATE.plusDays(1)));
    }

    @Test
    @DisplayName("the encoding is exactly (seq * 100000) + ordinal")
    void encodingIsAsSpecified() {
        long ordinal = InstructionKey.runDateOrdinal(RUN_DATE);

        assertThat(InstructionKey.of(7L, RUN_DATE).value()).isEqualTo(7L * 100_000L + ordinal);
    }

    @Test
    @DisplayName("a mandated return uses the same encoding through an explicit entry point")
    void mandatedReturnUsesTheSameEncoding() {
        // The distinction is at the call site, not in the arithmetic — a sweep nobody
        // requested has no payout request id, so its sequence comes from elsewhere and the
        // named factory is what stops a reader assuming a request id was passed.
        assertThat(InstructionKey.forMandatedReturn(9_000_001L, RUN_DATE))
                .isEqualTo(InstructionKey.of(9_000_001L, RUN_DATE));
    }

    @Test
    @DisplayName("overflow throws rather than truncating into a valid-looking key")
    void overflowThrowsRatherThanTruncating() {
        long tooLarge = InstructionKey.MAX_INSTRUCTION_SEQ + 1L;

        assertThatThrownBy(() -> InstructionKey.of(tooLarge, RUN_DATE))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("instruction_seq_overflow"))
                // It pages, because a key that cannot be built means an instruction that
                // cannot be safely issued.
                .satisfies(e -> assertThat(((FmsInvariantException) e).pagesOnCall()).isTrue());
    }

    @Test
    @DisplayName("the largest encodable sequence round-trips on EVERY encodable run date")
    void largestEncodableSequenceRoundTripsOnEveryDate() {
        // The previous version tested ordinal 0 only — the single date at which the old,
        // off-by-one bound happened to hold. MAX_INSTRUCTION_SEQ claimed to be the largest
        // encodable sequence while overflowing for every run date from ordinal 75,808 onward,
        // and a boundary test that only probes one end of a two-dimensional bound proves nothing
        // about the corner where it actually fails.
        for (long ordinal : new long[]{0L, 1L, 75_807L, 75_808L, InstructionKey.MAX_RUN_DATE_ORDINAL}) {
            LocalDate date = InstructionKey.ORDINAL_EPOCH.plusDays(ordinal);
            InstructionKey key = InstructionKey.of(InstructionKey.MAX_INSTRUCTION_SEQ, date);

            assertThat(key.instructionSeq())
                    .as("sequence round-trips at ordinal %d", ordinal)
                    .isEqualTo(InstructionKey.MAX_INSTRUCTION_SEQ);
            assertThat(key.runDate()).as("date round-trips at ordinal %d", ordinal).isEqualTo(date);
        }
    }

    @Test
    @DisplayName("one past the maximum is refused at every run date, not just at the epoch")
    void oneAboveMaximumIsRefusedEverywhere() {
        for (long ordinal : new long[]{0L, 75_808L, InstructionKey.MAX_RUN_DATE_ORDINAL}) {
            LocalDate date = InstructionKey.ORDINAL_EPOCH.plusDays(ordinal);
            assertThatThrownBy(() -> InstructionKey.of(InstructionKey.MAX_INSTRUCTION_SEQ + 1, date))
                    .as("refused at ordinal %d", ordinal)
                    .isInstanceOf(FmsInvariantException.class);
        }
    }

    @Test
    @DisplayName("a run date before the epoch is refused")
    void runDateBeforeEpochIsRefused() {
        assertThatThrownBy(() -> InstructionKey.of(1L, InstructionKey.ORDINAL_EPOCH.minusDays(1)))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("run_date_out_of_range"));
    }

    @Test
    @DisplayName("a run date past the encodable window is refused rather than wrapping")
    void runDatePastWindowIsRefused() {
        // 99,999 days is about 273 years. The check exists so that if this system somehow
        // outlives it, the failure is loud rather than a key that collides with day zero.
        assertThatThrownBy(() -> InstructionKey.of(1L, InstructionKey.ORDINAL_EPOCH.plusDays(100_000L)))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("run_date_out_of_range"));
    }

    @Test
    @DisplayName("a non-positive sequence is refused")
    void nonPositiveSequenceIsRefused() {
        assertThatThrownBy(() -> InstructionKey.of(0L, RUN_DATE))
                .isInstanceOf(FmsInvariantException.class);
        assertThatThrownBy(() -> InstructionKey.of(-1L, RUN_DATE))
                .isInstanceOf(FmsInvariantException.class);
    }
}
