package com.thinq.fms.qa;

import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.movement.payin.RouteCap;
import com.thinq.fms.movement.payin.SelectedRoute;
import com.thinq.fms.movement.payout.InstructionKey;
import com.thinq.fms.movement.payout.InstructionResult;
import com.thinq.fms.movement.payout.MandatedReturnSchedule;
import com.thinq.fms.movement.payout.MandatedReturnSchedule.Frequency;
import com.thinq.fms.movement.payout.PaymentInstruction;
import com.thinq.fms.movement.payout.PayoutRail;
import com.thinq.fms.movement.payout.PayoutRailConfiguration;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementOutcome;
import com.thinq.fms.movement.payout.SettlementReasonCode;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalogue sections TC-ADD and TC-WDR, value contracts — {@code docs/qa/test-cases.md}.
 *
 * <p>The orchestrators on both money paths are tested. The values they pass between themselves and
 * the rails were not: {@code PayoutRailConfiguration} — the bean whose whole purpose is to refuse
 * to start when two rails could instruct independently — sat at 39% with its refusal branch
 * unexercised, which meant Rule W9's no-double-payout guarantee rested on a guard nothing had ever
 * fired.
 */
class MovementContractTest {

    private static final AccountRef ACCOUNT = AccountRef.of("UCC0001");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 8, 24);

    // ------------------------------------------------------------------- RouteCap (REQ-701, G1)

    @Test
    @DisplayName("TC-ADD-046 — an absent daily cap means unbounded, and is never read as zero")
    void anAbsentCapMeansUnboundedRatherThanZero() {
        // NEFT has no ceiling on the rail itself. Reading empty as zero would refuse every NEFT
        // payment while reporting a limit the configuration does not set.
        RouteCap uncapped = new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO);

        assertThat(uncapped.hasCap()).isFalse();
        assertThat(uncapped.remainingAfter(Money.ofPaise(50_00_000L))).isEmpty();
        assertThat(uncapped.isOverCap(Money.ofPaise(50_00_000L))).isFalse();
    }

    @Test
    @DisplayName("TC-ADD-047 — a zero daily cap is refused, because it disables a route silently")
    void aZeroCapIsRefused() {
        assertThatThrownBy(() ->
                new RouteCap(PaymentRoute.UPI, Optional.of(Money.ZERO), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("silently");
    }

    @Test
    @DisplayName("TC-ADD-048 — a negative cap is refused for the same reason")
    void aNegativeCapIsRefused() {
        assertThatThrownBy(() ->
                new RouteCap(PaymentRoute.UPI, Optional.of(Money.ofPaise(-1L)), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-ADD-049 — a negative route fee is refused")
    void aNegativeRouteFeeIsRefused() {
        assertThatThrownBy(() ->
                new RouteCap(PaymentRoute.NET_BANKING, Optional.empty(), Money.ofPaise(-1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fee cannot be negative");
    }

    @Test
    @DisplayName("TC-ADD-050 — headroom is the cap less what has already gone out today")
    void headroomIsTheCapLessTodaysUsage() {
        RouteCap upi = new RouteCap(PaymentRoute.UPI, Optional.of(Money.ofPaise(2_00_000_00L)), Money.ZERO);

        assertThat(upi.remainingAfter(Money.ofPaise(50_000_00L)))
                .contains(Money.ofPaise(1_50_000_00L));
        assertThat(upi.remainingAfter(Money.ZERO)).contains(Money.ofPaise(2_00_000_00L));
    }

    @Test
    @DisplayName("TC-ADD-051 — headroom floors at zero rather than reporting a negative remainder")
    void headroomFloorsAtZero() {
        RouteCap upi = new RouteCap(PaymentRoute.UPI, Optional.of(Money.ofPaise(1_000L)), Money.ZERO);

        assertThat(upi.remainingAfter(Money.ofPaise(1_500L))).contains(Money.ZERO);
    }

    @Test
    @DisplayName("TC-ADD-052 — being over cap is reported separately from a zero headroom")
    void beingOverCapIsReportedSeparately() {
        // A zeroed headroom and a cap that was lowered below today's usage look identical to a
        // trader and mean different things to an operator, so the second has its own predicate.
        RouteCap upi = new RouteCap(PaymentRoute.UPI, Optional.of(Money.ofPaise(1_000L)), Money.ZERO);

        assertThat(upi.isOverCap(Money.ofPaise(1_000L))).isFalse();
        assertThat(upi.isOverCap(Money.ofPaise(1_001L))).isTrue();
    }

    @Test
    @DisplayName("TC-ADD-053 — exhausting a cap exactly leaves zero headroom and is not over cap")
    void exhaustingACapExactlyIsNotOverCap() {
        RouteCap upi = new RouteCap(PaymentRoute.UPI, Optional.of(Money.ofPaise(2_000L)), Money.ZERO);

        assertThat(upi.remainingAfter(Money.ofPaise(2_000L))).contains(Money.ZERO);
        assertThat(upi.isOverCap(Money.ofPaise(2_000L))).isFalse();
    }

    // ------------------------------------------------------------ SelectedRoute (REQ-702, A12)

    @Test
    @DisplayName("TC-ADD-054 — a route that was not switched reports no switch to disclose")
    void aRouteThatWasNotSwitchedReportsNoSwitch() {
        SelectedRoute chosen = new SelectedRoute(
                PaymentRoute.UPI, Money.ZERO, Optional.of(Money.ofPaise(1_000L)), null);

        assertThat(chosen.wasSwitched()).isFalse();
        assertThat(chosen.switchedFromIfAny()).isEmpty();
    }

    @Test
    @DisplayName("TC-ADD-055 — an automatic re-route names the route it moved away from")
    void anAutomaticRerouteNamesWhatItMovedFrom() {
        SelectedRoute switched = new SelectedRoute(
                PaymentRoute.NET_BANKING, Money.ZERO, Optional.empty(), PaymentRoute.UPI);

        assertThat(switched.wasSwitched()).isTrue();
        assertThat(switched.switchedFromIfAny()).contains(PaymentRoute.UPI);
    }

    @Test
    @DisplayName("TC-ADD-056 — a route cannot be recorded as having switched from itself")
    void aRouteCannotSwitchFromItself() {
        assertThatThrownBy(() -> new SelectedRoute(
                PaymentRoute.UPI, Money.ZERO, Optional.empty(), PaymentRoute.UPI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("switched from itself");
    }

    @Test
    @DisplayName("TC-ADD-057 — an uncapped selected route carries empty headroom, not zero")
    void anUncappedSelectedRouteCarriesEmptyHeadroom() {
        SelectedRoute neft = new SelectedRoute(PaymentRoute.NEFT, Money.ZERO, Optional.empty(), null);

        assertThat(neft.remainingHeadroom()).isEmpty();
    }

    @Test
    @DisplayName("TC-ADD-058 — only routes this system can execute are offerable")
    void onlyExecutableRoutesExist() {
        // Rule A9d: a self-service rail is never offered, because the control would promise a
        // payment and deliver instructions. The enum is the enforcement.
        assertThat(PaymentRoute.values())
                .containsExactly(PaymentRoute.UPI, PaymentRoute.NET_BANKING, PaymentRoute.NEFT);
    }

    // ------------------------------------------------------------ SettlementOutcome (REQ-308)

    @Test
    @DisplayName("TC-WDR-046 — a settlement may send less than requested but never more")
    void aSettlementNeverSendsMoreThanRequested() {
        assertThatThrownBy(() -> outcome(PayoutState.PAID, 1_000L, 1_001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never more");
    }

    @Test
    @DisplayName("TC-WDR-047 — a negative amount sent is refused")
    void aNegativeAmountSentIsRefused() {
        assertThatThrownBy(() -> outcome(PayoutState.NOTHING_SENT, 1_000L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("TC-WDR-048 — an outcome must report a terminal state")
    void anOutcomeMustReportATerminalState() {
        assertThatThrownBy(() -> outcome(PayoutState.ACCEPTED, 1_000L, 1_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal state");
        assertThatThrownBy(() -> outcome(PayoutState.INSTRUCTED, 1_000L, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-WDR-049 — the gap between requested and sent is stated, not left to be worked out")
    void theGapBetweenRequestedAndSentIsStated() {
        // REQ-308 requires the amount requested, the amount sent, and the deduction accounting for
        // the difference. The third is only answerable if the first two are both retained.
        SettlementOutcome partial = outcome(PayoutState.PARTLY_PAID, 10_000L, 6_500L);

        assertThat(partial.shortfallAgainstRequest()).isEqualTo(Money.ofPaise(3_500L));
    }

    @Test
    @DisplayName("TC-WDR-050 — a full settlement leaves no gap to explain")
    void aFullSettlementLeavesNoGap() {
        assertThat(outcome(PayoutState.PAID, 10_000L, 10_000L).shortfallAgainstRequest())
                .isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("TC-WDR-051 — an absent bank reference reads as absent rather than as ours")
    void anAbsentBankReferenceReadsAsAbsent() {
        // Rule C8: the UTR and the FMS reference are different fields and never share a value.
        // Substituting ours sends a trader to a bank the number means nothing to.
        SettlementOutcome nothingSent = new SettlementOutcome(
                PayoutState.NOTHING_SENT, Money.ofPaise(1_000L), Money.ZERO,
                SettlementReasonCode.INSUFFICIENT_BALANCE, null, null, null);

        assertThat(nothingSent.bankReferenceIfPresent()).isEmpty();
        assertThat(nothingSent.creditedOnIfKnown()).isEmpty();
    }

    @Test
    @DisplayName("TC-WDR-052 — a credited date is carried so quoted and actual can be compared")
    void aCreditedDateIsCarried() {
        SettlementOutcome paid = new SettlementOutcome(
                PayoutState.PAID, Money.ofPaise(1_000L), Money.ofPaise(1_000L),
                SettlementReasonCode.NONE, null, "UTR123456", RUN_DATE);

        assertThat(paid.bankReferenceIfPresent()).contains("UTR123456");
        assertThat(paid.creditedOnIfKnown()).contains(RUN_DATE);
    }

    @Test
    @DisplayName("TC-WDR-053 — an unmapped vendor phrase is retained verbatim alongside UNSPECIFIED")
    void anUnmappedVendorPhraseIsRetained() {
        // OA-4 degrades here rather than at the trader: the code is UNSPECIFIED so no generated
        // copy claims a cause, and the phrase survives for the operational alert that extends the
        // mapping table.
        SettlementOutcome unmapped = new SettlementOutcome(
                PayoutState.PARTLY_PAID, Money.ofPaise(1_000L), Money.ofPaise(400L),
                SettlementReasonCode.UNSPECIFIED, "RMS HOLD CODE 88", null, null);

        assertThat(unmapped.reasonCode()).isEqualTo(SettlementReasonCode.UNSPECIFIED);
        assertThat(unmapped.reasonText()).isEqualTo("RMS HOLD CODE 88");
    }

    @ParameterizedTest
    @EnumSource(value = PayoutState.class, names = {"PAID", "PARTLY_PAID", "NOTHING_SENT", "RETURNED", "CANCELLED"})
    @DisplayName("TC-WDR-054 — every terminal state is expressible as a settlement outcome")
    void everyTerminalStateIsExpressible(PayoutState state) {
        assertThatCode(() -> outcome(state, 1_000L, state == PayoutState.NOTHING_SENT ? 0L : 1_000L))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------- PaymentInstruction (W3/W12)

    @Test
    @DisplayName("TC-WDR-055 — a zero-amount instruction is refused rather than sent as a no-op")
    void aZeroAmountInstructionIsRefused() {
        // A rail may accept, log and reference a zero-amount request, which then has to be
        // reconciled against a payout nobody made. The run declines to instruct instead.
        assertThatThrownBy(() -> new PaymentInstruction(
                InstructionKey.of(1L, RUN_DATE), ACCOUNT, Money.ZERO, "acc-4471", RUN_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive amount");
    }

    @Test
    @DisplayName("TC-WDR-056 — an instruction without a pinned destination is refused")
    void anInstructionWithoutADestinationIsRefused() {
        assertThatThrownBy(() -> new PaymentInstruction(
                InstructionKey.of(1L, RUN_DATE), ACCOUNT, Money.ofPaise(100L), null, RUN_DATE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-WDR-057 — an instruction without its run date or key is refused")
    void anInstructionWithoutItsRunDateOrKeyIsRefused() {
        assertThatThrownBy(() -> new PaymentInstruction(
                null, ACCOUNT, Money.ofPaise(100L), "acc-4471", RUN_DATE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentInstruction(
                InstructionKey.of(1L, RUN_DATE), ACCOUNT, Money.ofPaise(100L), "acc-4471", null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------- MandatedReturnSchedule (REQ-307, W9)

    @Test
    @DisplayName("TC-WDR-058 — the monthly return falls on the last day of the month")
    void theMonthlyReturnFallsOnTheLastDayOfTheMonth() {
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 2, 3), Frequency.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2028, 2, 3), Frequency.MONTHLY))
                .as("a leap year is the month's own length, not a fixed 28")
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("TC-WDR-059 — asking on the return date itself returns that date, not the next one")
    void askingOnTheDateItselfReturnsThatDate() {
        // REQ-307 requires the date stated before it occurs. Rolling forward on the day would
        // announce next month's date to a trader whose money is leaving this afternoon.
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 6, 30), Frequency.QUARTERLY))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 7, 1), Frequency.QUARTERLY))
                .isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("TC-WDR-060 — a mandated return on the run date is detected as a collision")
    void aMandatedReturnOnTheRunDateIsACollision() {
        // Rule W9: both settle from one balance in one payout. Detecting the collision is the
        // precondition for that, and the same money being sent twice is what it prevents.
        assertThat(MandatedReturnSchedule.collidesWithOpenRequest(RUN_DATE, RUN_DATE)).isTrue();
        assertThat(MandatedReturnSchedule.collidesWithOpenRequest(RUN_DATE, RUN_DATE.plusDays(1)))
                .isFalse();
    }

    @Test
    @DisplayName("TC-WDR-061 — the quarterly cycle uses the Indian financial quarter ends")
    void theQuarterlyCycleUsesIndianFinancialQuarterEnds() {
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 1, 15), Frequency.QUARTERLY))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 4, 1), Frequency.QUARTERLY))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 10, 2), Frequency.QUARTERLY))
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // ----------------------------------------------------- PayoutRailConfiguration (OA-3, Rule W9)

    @Test
    @DisplayName("TC-WDR-062 — a second payout rail stops the service starting")
    void aSecondPayoutRailStopsTheServiceStarting() {
        // Three systems in this estate can move money out. Two live at once would instruct
        // independently and Rule W9's combine-before-instruct step would protect nothing, so the
        // failure is at boot rather than at the first double payment.
        assertThatThrownBy(() -> new PayoutRailConfiguration(List.of(new StubRail(), new StubRail())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exactly one PayoutRail")
                .hasMessageContaining("found 2");
    }

    @Test
    @DisplayName("TC-WDR-063 — no payout rail also stops the service starting")
    void noPayoutRailAlsoStopsTheServiceStarting() {
        // A service that starts with no rail accepts withdrawal requests it can never settle, and
        // the trader finds out at end of day.
        assertThatThrownBy(() -> new PayoutRailConfiguration(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("found 0");
    }

    @Test
    @DisplayName("TC-WDR-064 — exactly one rail starts, and is the one handed to the run")
    void exactlyOneRailStarts() {
        StubRail only = new StubRail();

        assertThat(new PayoutRailConfiguration(List.of(only)).rail()).isSameAs(only);
    }

    // ------------------------------------------------------------------------------------ helpers

    private static SettlementOutcome outcome(PayoutState state, long requested, long sent) {
        return new SettlementOutcome(
                state, Money.ofPaise(requested), Money.ofPaise(sent),
                SettlementReasonCode.NONE, null, null, null);
    }

    /** A rail that is never called; these tests are about how many of them exist. */
    private static final class StubRail implements PayoutRail {
        @Override
        public InstructionResult instruct(PaymentInstruction instruction) {
            throw new UnsupportedOperationException("not called");
        }

        @Override
        public Optional<InstructionResult> statusOf(InstructionKey key, LocalDate runDate) {
            throw new UnsupportedOperationException("not called");
        }
    }
}
