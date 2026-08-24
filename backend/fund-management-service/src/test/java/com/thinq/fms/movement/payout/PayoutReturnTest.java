package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/** REQ-306, REQ-307 and Rules W7, W8, W9. */
class PayoutReturnTest {

    @Test
    @DisplayName("a failed payout is never resent automatically")
    void aFailedPayoutIsNeverResentAutomatically() {
        // Rule W7. A destination that has just refused will likely refuse again, and where the
        // destination is wrong a resend is money going somewhere it should not.
        PayoutReturn returned = new PayoutReturn(1L, Money.ofPaise(500_000L),
                SettlementReasonCode.DESTINATION_REJECTED, "account closed");

        assertThat(returned.mayResendAutomatically()).isFalse();
    }

    @Test
    @DisplayName("a rejected destination needs attention before another request")
    void aRejectedDestinationNeedsAttention() {
        // The distinction decides what the trader is told to do: fix the account, or simply retry.
        assertThat(new PayoutReturn(1L, Money.ofPaise(1L),
                SettlementReasonCode.DESTINATION_REJECTED, "closed").destinationNeedsAttention())
                .isTrue();

        assertThat(new PayoutReturn(1L, Money.ofPaise(1L),
                SettlementReasonCode.INSUFFICIENT_BALANCE, "short").destinationNeedsAttention())
                .isFalse();
    }

    @Test
    @DisplayName("a return compensates a specific payout for a positive amount")
    void aReturnCompensatesASpecificPayout() {
        assertThatThrownBy(() -> new PayoutReturn(0L, Money.ofPaise(1L),
                SettlementReasonCode.NONE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayoutReturn(1L, Money.ZERO,
                SettlementReasonCode.NONE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- REQ-307 ----

    @Test
    @DisplayName("the next monthly return is the end of the current month, or the next")
    void theNextMonthlyReturn() {
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 8, 22),
                MandatedReturnSchedule.Frequency.MONTHLY)).isEqualTo(LocalDate.of(2026, 8, 31));

        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 8, 31),
                MandatedReturnSchedule.Frequency.MONTHLY))
                .as("on the day itself, today still qualifies").isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("the next quarterly return lands on a quarter end")
    void theNextQuarterlyReturn() {
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 8, 22),
                MandatedReturnSchedule.Frequency.QUARTERLY)).isEqualTo(LocalDate.of(2026, 9, 30));

        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 10, 1),
                MandatedReturnSchedule.Frequency.QUARTERLY)).isEqualTo(LocalDate.of(2026, 12, 31));

        // On the quarter end itself, today still qualifies — the run has not happened yet. Deferring
        // to the next quarter would silently skip a mandated return by three months.
        assertThat(MandatedReturnSchedule.nextAfter(LocalDate.of(2026, 9, 30),
                MandatedReturnSchedule.Frequency.QUARTERLY)).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @ParameterizedTest
    @EnumSource(MandatedReturnSchedule.Frequency.class)
    @DisplayName("the next return is never in the past")
    void theNextReturnIsNeverInThePast(MandatedReturnSchedule.Frequency frequency) {
        LocalDate from = LocalDate.of(2026, 8, 22);

        assertThat(MandatedReturnSchedule.nextAfter(from, frequency)).isAfterOrEqualTo(from);
    }

    @Test
    @DisplayName("a mandated return on the same date as an open request is a collision")
    void aCollisionIsDetected() {
        // Rule W9: they settle from the same balance in one payout. Sending both sends the same
        // money twice, and no reconciliation afterwards recovers the trader's trust in the figure.
        assertThat(MandatedReturnSchedule.collidesWithOpenRequest(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(MandatedReturnSchedule.collidesWithOpenRequest(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 29))).isFalse();
    }
}
