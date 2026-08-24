package com.thinq.fms.qa;

import com.thinq.fms.derivation.Derivation;
import com.thinq.fms.derivation.DerivationResult;
import com.thinq.fms.derivation.DerivationTerm;
import com.thinq.fms.derivation.MarginFigures;
import com.thinq.fms.derivation.MarginSourceKind;
import com.thinq.fms.derivation.TermCode;
import com.thinq.fms.derivation.TermSign;
import com.thinq.fms.derivation.WithdrawableInputs;
import com.thinq.fms.derivation.WithdrawableVerdict;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalogue section TC-BAL, value contracts — {@code docs/qa/test-cases.md}.
 *
 * <p>The calculator that applies Rule B4 already has a test class. What it did not have was any
 * test of the types the calculator produces and consumes, and those types carry rules of their
 * own: a term whose magnitude is negative, a derivation whose terms do not explain its figure, a
 * margin snapshot reporting a negative shortfall. Each is a state Rule B4 says cannot exist, and
 * each was enforced in a constructor nothing exercised — {@code MarginFigures} was at zero
 * coverage, so every one of its guards was unverified.
 */
class DerivationContractTest {

    private static final Instant AT = Instant.parse("2026-08-24T09:15:00Z");

    // ---------------------------------------------------------------- MarginFigures (Rule B7/B9)

    @Test
    @DisplayName("TC-BAL-031 — a margin snapshot with a negative shortfall is refused")
    void aNegativeShortfallIsRefused() {
        assertThatThrownBy(() -> margin(Money.ofPaise(-1L), Money.ZERO, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shortfall is a magnitude");
    }

    @Test
    @DisplayName("TC-BAL-032 — a negative collateral value is refused")
    void aNegativeCollateralValueIsRefused() {
        assertThatThrownBy(() -> margin(Money.ZERO, Money.ofPaise(-1L), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collateralValue");
    }

    @Test
    @DisplayName("TC-BAL-033 — a negative collateral-met portion is refused")
    void aNegativeCollateralMetPortionIsRefused() {
        assertThatThrownBy(() -> margin(Money.ZERO, Money.ZERO, Money.ofPaise(-1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("committedMetFromCollateral");
    }

    @Test
    @DisplayName("TC-BAL-034 — every margin field is required")
    void everyMarginFieldIsRequired() {
        assertThatThrownBy(() -> new MarginFigures(null, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MarginFigures(Money.ZERO, null, Money.ZERO, Money.ZERO, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MarginFigures(Money.ZERO, Money.ZERO, null, Money.ZERO, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MarginFigures(Money.ZERO, Money.ZERO, Money.ZERO, null, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MarginFigures(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-BAL-035 — a shortfall exists only when it is above zero, not merely present")
    void aShortfallExistsOnlyAboveZero() {
        assertThat(margin(Money.ZERO, Money.ZERO, Money.ZERO).hasShortfall()).isFalse();
        assertThat(margin(Money.ofPaise(1L), Money.ZERO, Money.ZERO).hasShortfall()).isTrue();
    }

    @Test
    @DisplayName("TC-BAL-036 — used margin may exceed available margin without being refused")
    void usedMarginMayExceedAvailableMargin() {
        // A fully deployed account is an ordinary state, not an error. Rule B9 forbids the
        // clamping that would hide it, and nothing here may refuse to represent it.
        MarginFigures figures = new MarginFigures(
                Money.ofPaise(1_00L), Money.ofPaise(9_00_000L),
                Money.ZERO, Money.ZERO, Money.ZERO);

        assertThat(figures.usedMargin()).isGreaterThan(figures.availableMargin());
    }

    @Test
    @DisplayName("TC-BAL-037 — available margin may be negative and is carried as such")
    void availableMarginMayBeNegative() {
        // Rule B9: a component may be negative where a position moved against the trader. Only
        // the withdrawable figure floors, and it floors in the calculator, not here.
        MarginFigures figures = new MarginFigures(
                Money.ofPaise(-5_000L), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);

        assertThat(figures.availableMargin().isNegative()).isTrue();
    }

    // ------------------------------------------------------------ WithdrawableInputs (Rule B4/B9)

    @Test
    @DisplayName("TC-BAL-038 — the settled ledger balance may be negative")
    void theSettledLedgerBalanceMayBeNegative() {
        assertThatCode(() -> new WithdrawableInputs(
                Money.ofPaise(-2_437L), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-BAL-039 — every deduction input is a magnitude and refuses a negative value")
    void everyDeductionInputIsAMagnitude() {
        Money minusOne = Money.ofPaise(-1L);

        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, minusOne, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
                .hasMessageContaining("moneyAddedToday");
        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, Money.ZERO, minusOne, Money.ZERO, Money.ZERO, Money.ZERO))
                .hasMessageContaining("unsettledSaleProceeds");
        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, Money.ZERO, Money.ZERO, minusOne, Money.ZERO, Money.ZERO))
                .hasMessageContaining("chargesIncurredNotPosted");
        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, minusOne, Money.ZERO))
                .hasMessageContaining("marginShortfall");
        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, minusOne))
                .hasMessageContaining("committedMarginMetFromCollateral");
    }

    @Test
    @DisplayName("TC-BAL-040 — a missing input is refused rather than defaulted to zero")
    void aMissingInputIsRefused() {
        // Rule B10: an unavailable figure is stated as unavailable, never as zero. Defaulting a
        // null here would produce exactly the ₹0.00 that rule exists to forbid.
        assertThatThrownBy(() -> new WithdrawableInputs(
                null, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WithdrawableInputs(
                Money.ZERO, null, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------ DerivationTerm (REQ-102)

    @Test
    @DisplayName("TC-BAL-041 — a term given a negative magnitude is refused, because direction is the sign's")
    void aTermWithANegativeMagnitudeIsRefused() {
        assertThatThrownBy(() ->
                new DerivationTerm(TermCode.ADDED_TODAY, TermSign.MINUS, Money.ofPaise(-100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction belongs in the sign");
    }

    @Test
    @DisplayName("TC-BAL-042 — a term's signed contribution follows its sign, not its magnitude")
    void aTermsSignedContributionFollowsItsSign() {
        assertThat(new DerivationTerm(TermCode.SETTLED_LEDGER, TermSign.PLUS, Money.ofPaise(500L))
                .signedPaise()).isEqualTo(500L);
        assertThat(new DerivationTerm(TermCode.ADDED_TODAY, TermSign.MINUS, Money.ofPaise(500L))
                .signedPaise()).isEqualTo(-500L);
    }

    @Test
    @DisplayName("TC-BAL-043 — a zero-valued term still declares its direction")
    void aZeroValuedTermStillDeclaresItsDirection() {
        // REQ-102 requires every term shown including those whose value is zero, and requires the
        // trader to be able to tell an increase from a reduction. A zero amount carries no sign of
        // its own, which is why the sign is a field rather than an inference.
        DerivationTerm zeroDeduction =
                new DerivationTerm(TermCode.UNSETTLED_PROCEEDS, TermSign.MINUS, Money.ZERO);

        assertThat(zeroDeduction.isDeduction()).isTrue();
        assertThat(zeroDeduction.signedPaise()).isZero();
    }

    @Test
    @DisplayName("TC-BAL-044 — only a MINUS term counts as a deduction")
    void onlyAMinusTermIsADeduction() {
        assertThat(new DerivationTerm(TermCode.COLLATERAL_MET, TermSign.PLUS, Money.ofPaise(10L))
                .isDeduction()).isFalse();
    }

    @Test
    @DisplayName("TC-BAL-045 — the two signs multiply in opposite directions")
    void theTwoSignsMultiplyInOppositeDirections() {
        assertThat(TermSign.PLUS.multiplier()).isEqualTo(1);
        assertThat(TermSign.MINUS.multiplier()).isEqualTo(-1);
    }

    // ---------------------------------------------------------------------- Derivation (Rule B12)

    @Test
    @DisplayName("TC-BAL-046 — a derivation carrying fewer than Rule B4's six terms is refused")
    void aDerivationMissingATermIsRefused() {
        List<DerivationTerm> five = new ArrayList<>(sixTerms());
        five.remove(0);

        assertThatThrownBy(() -> new Derivation(Money.ZERO, 0L, five, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all are shown");
    }

    @Test
    @DisplayName("TC-BAL-047 — a derivation whose figure does not follow from its terms is refused")
    void aDerivationWhoseFigureDoesNotFollowIsRefused() {
        // Rule B12 and REQ-102 both forbid a figure and an explanation that disagree. Enforcing it
        // in the constructor is what stops a second construction site producing one.
        assertThatThrownBy(() -> new Derivation(Money.ofPaise(999L), 1_000L, sixTerms(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not follow from");
    }

    @Test
    @DisplayName("TC-BAL-048 — a negative pre-floor sum must present as a zero figure")
    void aNegativePreFloorSumMustPresentAsZero() {
        assertThatThrownBy(() -> new Derivation(Money.ofPaise(-500L), -500L, sixTerms(), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> new Derivation(Money.ZERO, -500L, sixTerms(), TermCode.SHORTFALL_OUTSTANDING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-BAL-049 — flooring is distinguishable from a genuinely zero balance")
    void flooringIsDistinguishableFromAZeroBalance() {
        Derivation genuinelyZero = new Derivation(Money.ZERO, 0L, sixTerms(), null);
        Derivation drivenBelowZero =
                new Derivation(Money.ZERO, -1L, sixTerms(), TermCode.SHORTFALL_OUTSTANDING);

        assertThat(genuinelyZero.flooredAtZero()).isFalse();
        assertThat(drivenBelowZero.flooredAtZero()).isTrue();
    }

    @Test
    @DisplayName("TC-BAL-050 — a derivation's terms cannot be mutated after construction")
    void aDerivationsTermsCannotBeMutated() {
        List<DerivationTerm> mutable = new ArrayList<>(sixTerms());
        Derivation derivation = new Derivation(Money.ZERO, 0L, mutable, null);

        mutable.clear();

        assertThat(derivation.terms()).hasSize(TermCode.values().length);
        assertThatThrownBy(() -> derivation.terms().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("TC-BAL-051 — Rule B4's six terms are the enum, in the order the trader reads them")
    void ruleB4sSixTermsAreTheEnumInOrder() {
        assertThat(TermCode.values()).containsExactly(
                TermCode.SETTLED_LEDGER,
                TermCode.ADDED_TODAY,
                TermCode.UNSETTLED_PROCEEDS,
                TermCode.CHARGES_UNPOSTED,
                TermCode.SHORTFALL_OUTSTANDING,
                TermCode.COLLATERAL_MET);
    }

    // ------------------------------------------------------------------ DerivationResult (REQ-107)

    @Test
    @DisplayName("TC-BAL-052 — a RECONCILED result without its derivation is refused")
    void aReconciledResultWithoutItsDerivationIsRefused() {
        assertThatThrownBy(() -> new DerivationResult(
                WithdrawableVerdict.RECONCILED, null, Money.ZERO, AT, MarginSourceKind.FRONT_OFFICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must carry its derivation");
    }

    @Test
    @DisplayName("TC-BAL-053 — only a RECONCILED verdict yields a figure a trader may act on")
    void onlyReconciledYieldsAnActionableFigure() {
        assertThat(reconciled(Money.ofPaise(4_200L)).withdrawable()).contains(Money.ofPaise(4_200L));
        assertThat(divergent().withdrawable()).isEmpty();
        assertThat(DerivationResult.unavailable(AT, MarginSourceKind.BACK_OFFICE).withdrawable()).isEmpty();
    }

    @Test
    @DisplayName("TC-BAL-054 — a DIVERGENT verdict does not silently resolve to the RMS figure")
    void aDivergentVerdictDoesNotResolveToRms() {
        // The disagreement is the product state. Returning RMS's answer would pick a winner the
        // design says must not be picked, and the trader would see a figure Rule B4 cannot explain.
        DerivationResult result = divergent();

        assertThat(result.rmsAuthority()).isNotNull();
        assertThat(result.withdrawable()).isEmpty();
        assertThat(result.isActionable()).isFalse();
    }

    @Test
    @DisplayName("TC-BAL-055 — a DIVERGENT result still carries its derivation for the explanation")
    void aDivergentResultStillCarriesItsDerivation() {
        assertThat(divergent().derivationIfPresent()).isPresent();
        assertThat(DerivationResult.unavailable(AT, MarginSourceKind.FRONT_OFFICE).derivationIfPresent())
                .isEmpty();
    }

    @Test
    @DisplayName("TC-BAL-056 — an unavailable result still states when and by whom it was computed")
    void anUnavailableResultStillStatesItsProvenance() {
        // REQ-107 renders the instant and the source next to the figure. An outage removes the
        // figure; it does not remove the trader's right to know how current the answer is.
        DerivationResult result = DerivationResult.unavailable(AT, MarginSourceKind.BACK_OFFICE);

        assertThat(result.computedAt()).isEqualTo(AT);
        assertThat(result.computedBy()).isEqualTo(MarginSourceKind.BACK_OFFICE);
        assertThat(result.verdict()).isEqualTo(WithdrawableVerdict.UNAVAILABLE);
    }

    @Test
    @DisplayName("TC-BAL-057 — a verdict is required on every result")
    void aVerdictIsRequiredOnEveryResult() {
        assertThatThrownBy(() -> new DerivationResult(null, null, null, AT, MarginSourceKind.FRONT_OFFICE))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(WithdrawableVerdict.class)
    @DisplayName("TC-BAL-058 — actionability and a present figure agree for every verdict")
    void actionabilityAndAPresentFigureAgree(WithdrawableVerdict verdict) {
        DerivationResult result = switch (verdict) {
            case RECONCILED -> reconciled(Money.ofPaise(1L));
            case DIVERGENT -> divergent();
            case UNAVAILABLE -> DerivationResult.unavailable(AT, MarginSourceKind.FRONT_OFFICE);
        };

        assertThat(result.isActionable()).isEqualTo(result.withdrawable().isPresent());
    }

    @Test
    @DisplayName("TC-BAL-059 — the source that answered is one of the two the handover defines")
    void theSourceIsOneOfTheTwo() {
        assertThat(MarginSourceKind.values())
                .containsExactly(MarginSourceKind.FRONT_OFFICE, MarginSourceKind.BACK_OFFICE);
    }

    @Test
    @DisplayName("TC-BAL-060 — the three verdicts are exactly the states the design defines")
    void theThreeVerdictsAreExactlyTheStatesDefined() {
        assertThat(WithdrawableVerdict.values()).containsExactly(
                WithdrawableVerdict.RECONCILED,
                WithdrawableVerdict.DIVERGENT,
                WithdrawableVerdict.UNAVAILABLE);
    }

    // ------------------------------------------------------------------------------------ helpers

    private static MarginFigures margin(Money shortfall, Money collateral, Money collateralMet) {
        return new MarginFigures(Money.ZERO, Money.ZERO, collateral, collateralMet, shortfall);
    }

    private static List<DerivationTerm> sixTerms() {
        List<DerivationTerm> terms = new ArrayList<>();
        for (TermCode code : TermCode.values()) {
            TermSign sign = code == TermCode.SETTLED_LEDGER || code == TermCode.COLLATERAL_MET
                    ? TermSign.PLUS : TermSign.MINUS;
            terms.add(new DerivationTerm(code, sign, Money.ZERO));
        }
        return terms;
    }

    private static DerivationResult reconciled(Money withdrawable) {
        Derivation derivation = new Derivation(
                withdrawable, withdrawable.paise(), sixTerms(), null);
        return new DerivationResult(
                WithdrawableVerdict.RECONCILED, derivation, withdrawable, AT, MarginSourceKind.FRONT_OFFICE);
    }

    private static DerivationResult divergent() {
        Derivation derivation = new Derivation(Money.ofPaise(100L), 100L, sixTerms(), null);
        return new DerivationResult(
                WithdrawableVerdict.DIVERGENT, derivation, Money.ofPaise(250L), AT,
                MarginSourceKind.FRONT_OFFICE);
    }
}
