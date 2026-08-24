package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule B4's derivation is the number this whole product exists to explain, so it is tested
 * as a property rather than as a handful of examples.
 *
 * <p>The calculator is pure — no I/O below the point it is called — which is precisely why
 * these tests can generate thousands of inputs without a container, a database or a mock.
 * That purity is the reason the assembler and the calculator are separate classes; merging
 * them would make this file impossible to write.
 */
class WithdrawableCalculatorTest {

    private final WithdrawableCalculator calculator = new WithdrawableCalculator();

    @Test
    @DisplayName("the six signed terms always sum to the pre-floor figure")
    void termsSumToPreFloorFigure() {
        Random random = new Random(20260821L); // fixed seed: a failure must be reproducible

        for (int i = 0; i < 10_000; i++) {
            WithdrawableInputs inputs = randomInputs(random);

            Derivation derivation = calculator.compute(inputs);

            long summed = derivation.terms().stream()
                    .mapToLong(DerivationTerm::signedPaise)
                    .sum();

            assertThat(summed)
                    .as("the terms shown to the trader must reconcile to the figure they explain")
                    .isEqualTo(derivation.preFloorPaise());
        }
    }

    @Test
    @DisplayName("the withdrawable figure is never negative — Rule B9's single exception")
    void withdrawableIsNeverNegative() {
        Random random = new Random(20260822L);

        for (int i = 0; i < 10_000; i++) {
            Derivation derivation = calculator.compute(randomInputs(random));

            assertThat(derivation.withdrawable().paise())
                    .as("what can reach a bank today has no negative answer")
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    @DisplayName("flooring engages only when the raw sum is below zero, and never otherwise")
    void flooringEngagesOnlyBelowZero() {
        Random random = new Random(20260823L);

        for (int i = 0; i < 10_000; i++) {
            Derivation derivation = calculator.compute(randomInputs(random));

            if (derivation.preFloorPaise() < 0L) {
                assertThat(derivation.withdrawable().isZero()).isTrue();
                assertThat(derivation.flooredAtZero()).isTrue();
            } else {
                assertThat(derivation.withdrawable().paise()).isEqualTo(derivation.preFloorPaise());
                assertThat(derivation.flooredAtZero()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("all six terms are present even when their value is zero")
    void everyTermIsPresentIncludingZeroValued() {
        // REQ-102 requires every term of Rule B4 shown with its sign, including terms whose
        // value is zero. A calculator that omitted them would make the client's "render
        // every term" requirement unimplementable, so the omission is prevented here.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO));

        assertThat(derivation.terms()).hasSize(6);
        assertThat(derivation.terms())
                .extracting(DerivationTerm::code)
                .containsExactly(
                        TermCode.SETTLED_LEDGER,
                        TermCode.ADDED_TODAY,
                        TermCode.UNSETTLED_PROCEEDS,
                        TermCode.CHARGES_UNPOSTED,
                        TermCode.SHORTFALL_OUTSTANDING,
                        TermCode.COLLATERAL_MET);
    }

    @Test
    @DisplayName("the shortfall term stays visible when it forces the figure to zero")
    void shortfallTermRemainsVisibleWhenItFloorsTheFigure() {
        // The case Rule B9's exception exists for: a shortfall larger than the balance.
        // The figure floors, and the term that caused it must still be shown — otherwise
        // the trader sees zero with no explanation, which is the failure REQ-102 forbids.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(50_000L),   // settled ledger
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ofPaise(200_000L),  // shortfall, four times the balance
                Money.ZERO));

        assertThat(derivation.withdrawable().isZero()).isTrue();
        assertThat(derivation.flooredAtZero()).isTrue();
        assertThat(derivation.preFloorPaise()).isEqualTo(-150_000L);

        DerivationTerm shortfall = termOf(derivation, TermCode.SHORTFALL_OUTSTANDING);
        assertThat(shortfall.amount().paise()).isEqualTo(200_000L);
        assertThat(shortfall.sign()).isEqualTo(TermSign.MINUS);
        assertThat(derivation.largestDeduction()).isEqualTo(TermCode.SHORTFALL_OUTSTANDING);
    }

    @ParameterizedTest(name = "collateral-met {0}p is added back, not subtracted")
    @CsvSource({"1", "12345", "9999999"})
    @DisplayName("the collateral-met term is the one that adds")
    void collateralMetIsAdditive(long collateralMetPaise) {
        // Rule B4's counter-intuitive term. The account blocks the full margin requirement
        // against cash; where pledged securities covered part of it, that cash was never
        // truly committed and is added back. Getting its sign wrong understates every
        // affected trader's withdrawable balance.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(100_000L), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
                Money.ofPaise(collateralMetPaise)));

        assertThat(derivation.preFloorPaise()).isEqualTo(100_000L + collateralMetPaise);
        assertThat(termOf(derivation, TermCode.COLLATERAL_MET).sign()).isEqualTo(TermSign.PLUS);
    }

    @Test
    @DisplayName("each of the six inputs reaches its own term, with its own sign")
    void everyInputIsWiredToItsOwnTerm() {
        // The property tests cannot catch a mis-wiring here. termsSumToPreFloorFigure folds
        // the same terms the calculator built, through the same accessor, so it verifies that
        // the figure reconciles to the terms — not that either came from the right input.
        // Swapping which input feeds ADDED_TODAY and which feeds CHARGES_UNPOSTED leaves the
        // total identical, because both are MINUS, and the whole suite passed with that
        // mutation in place. The trader would have seen their deposits filed under charges.
        //
        // REQ-102 is a requirement about the explanation, so the mapping needs pinning
        // directly. Each magnitude below is distinct and recognisable, so a swap of any pair
        // fails here and names both terms involved.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(1_000_000L),  // SETTLED_LEDGER
                Money.ofPaise(200_000L),    // ADDED_TODAY
                Money.ofPaise(30_000L),     // UNSETTLED_PROCEEDS
                Money.ofPaise(4_000L),      // CHARGES_UNPOSTED
                Money.ofPaise(500L),        // SHORTFALL_OUTSTANDING
                Money.ofPaise(60L)));       // COLLATERAL_MET

        assertThat(termOf(derivation, TermCode.SETTLED_LEDGER).amount().paise()).isEqualTo(1_000_000L);
        assertThat(termOf(derivation, TermCode.ADDED_TODAY).amount().paise()).isEqualTo(200_000L);
        assertThat(termOf(derivation, TermCode.UNSETTLED_PROCEEDS).amount().paise()).isEqualTo(30_000L);
        assertThat(termOf(derivation, TermCode.CHARGES_UNPOSTED).amount().paise()).isEqualTo(4_000L);
        assertThat(termOf(derivation, TermCode.SHORTFALL_OUTSTANDING).amount().paise()).isEqualTo(500L);
        assertThat(termOf(derivation, TermCode.COLLATERAL_MET).amount().paise()).isEqualTo(60L);

        // Signs too, since a term reaching the right line with the wrong direction is the
        // other half of the same defect. Only two terms add.
        assertThat(termOf(derivation, TermCode.SETTLED_LEDGER).sign()).isEqualTo(TermSign.PLUS);
        assertThat(termOf(derivation, TermCode.ADDED_TODAY).sign()).isEqualTo(TermSign.MINUS);
        assertThat(termOf(derivation, TermCode.UNSETTLED_PROCEEDS).sign()).isEqualTo(TermSign.MINUS);
        assertThat(termOf(derivation, TermCode.CHARGES_UNPOSTED).sign()).isEqualTo(TermSign.MINUS);
        assertThat(termOf(derivation, TermCode.SHORTFALL_OUTSTANDING).sign()).isEqualTo(TermSign.MINUS);
        assertThat(termOf(derivation, TermCode.COLLATERAL_MET).sign()).isEqualTo(TermSign.PLUS);
    }

    @Test
    @DisplayName("a negative ledger balance keeps its magnitude on its own term")
    void negativeLedgerBalanceIsCarriedAsAMinusOnItsOwnTerm() {
        // The one input allowed to be negative, and the only term whose sign is computed
        // rather than fixed. A debit balance must land on SETTLED_LEDGER as a MINUS carrying
        // the magnitude — not leak into another term, and not be clamped, which Rule B9
        // permits only for the final figure.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(-75_000L),
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO));

        DerivationTerm ledger = termOf(derivation, TermCode.SETTLED_LEDGER);
        assertThat(ledger.sign()).isEqualTo(TermSign.MINUS);
        assertThat(ledger.amount().paise()).isEqualTo(75_000L);
        assertThat(derivation.preFloorPaise()).isEqualTo(-75_000L);
        assertThat(derivation.withdrawable().isZero()).isTrue();
        assertThat(derivation.largestDeduction()).isEqualTo(TermCode.SETTLED_LEDGER);
    }

    @Test
    @DisplayName("the largest deduction is the largest, not the first")
    void largestDeductionIsTheLargest() {
        // REQ-102 requires the largest single deduction named without the trader opening
        // the derivation. Naming the first negative term instead would be right by accident
        // whenever the terms happen to be ordered by size.
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(1_000_000L),
                Money.ofPaise(10_000L),    // added today
                Money.ofPaise(700_000L),   // unsettled proceeds — the largest
                Money.ofPaise(5_000L),     // charges
                Money.ZERO,
                Money.ZERO));

        assertThat(derivation.largestDeduction()).isEqualTo(TermCode.UNSETTLED_PROCEEDS);
    }

    @Test
    @DisplayName("no deduction means no largest deduction, rather than an arbitrary one")
    void noDeductionMeansNoLargestDeduction() {
        Derivation derivation = calculator.compute(new WithdrawableInputs(
                Money.ofPaise(500_000L), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO));

        assertThat(derivation.largestDeduction()).isNull();
    }

    private static DerivationTerm termOf(Derivation derivation, TermCode code) {
        return derivation.terms().stream()
                .filter(t -> t.code() == code)
                .findFirst()
                .orElseThrow(() -> new AssertionError("term absent from the derivation: " + code));
    }

    /**
     * Values are bounded well inside {@code long} so the generator itself cannot overflow —
     * overflow behaviour is {@link Money}'s own concern and is tested there, not smuggled
     * into a property test about reconciliation.
     */
    private static WithdrawableInputs randomInputs(Random random) {
        return new WithdrawableInputs(
                money(random, -50_000_000L, 500_000_000L),  // a ledger balance may be negative
                money(random, 0L, 50_000_000L),
                money(random, 0L, 100_000_000L),
                money(random, 0L, 5_000_000L),
                money(random, 0L, 100_000_000L),
                money(random, 0L, 100_000_000L));
    }

    private static Money money(Random random, long minInclusive, long maxInclusive) {
        return Money.ofPaise(random.nextLong(minInclusive, maxInclusive + 1));
    }
}
