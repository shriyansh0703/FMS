package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;

/**
 * A payout that failed after being sent, returned by compensation (REQ-306, Rule W7).
 *
 * <p><b>A return is an addition, never an edit.</b> Rule W7 forbids removing or altering the
 * original entry: both stay in the history, because a trader who saw money leave needs to see it
 * come back rather than find the record of it gone. The same shape as Rule A10's payin reversal, and
 * for the same reason.
 *
 * <p><b>Nothing is resent automatically.</b> A destination that has just refused the money will
 * likely refuse it again, and where the destination is wrong a resend is money going somewhere it
 * should not. The trader decides, after the destination is dealt with.
 */
public record PayoutReturn(long originalRequestId,
                           Money amountReturned,
                           SettlementReasonCode reason,
                           String reasonText) {

    public PayoutReturn {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(amountReturned, "amountReturned");

        if (originalRequestId <= 0L) {
            throw new IllegalArgumentException(
                    "a return compensates a specific payout; got request id " + originalRequestId);
        }
        if (!amountReturned.isPositive()) {
            throw new IllegalArgumentException(
                    "a return of zero is not a return; got " + amountReturned);
        }
    }

    /**
     * Whether the destination itself needs attention before another request (REQ-306).
     *
     * <p>The distinction decides what the trader is told to do. A rejected destination means the
     * account details are wrong or closed and a second request will fail identically; any other
     * reason means they may simply try again.
     */
    public boolean destinationNeedsAttention() {
        return this.reason == SettlementReasonCode.DESTINATION_REJECTED;
    }

    /** Rule W7, stated as code so nothing reads this type as an instruction to retry. */
    public boolean mayResendAutomatically() {
        return false;
    }
}
