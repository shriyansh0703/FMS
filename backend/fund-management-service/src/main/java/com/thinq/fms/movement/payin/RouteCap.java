package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * One route's configured daily ceiling and fee.
 *
 * <p><b>Everything here is configuration, and Rule G1 forbids restating a cap in message copy.</b>
 * A copy string naming ₹2,00,000 becomes a lie the day Payments changes the value, and nobody
 * looks in message templates when they change a limit. So the figure travels as a value and the
 * client renders it.
 *
 * @param route      which rail
 * @param dailyCap   the ceiling per day, or empty where the rail has none (NEFT). Empty is not
 *                   zero — reading an absent cap as zero would refuse every NEFT payment
 * @param fee        the gateway charge passed to the trader. ₹0 in this phase and absorbed, kept
 *                   configurable so charging becomes a settings change rather than a release
 */
public record RouteCap(PaymentRoute route, Optional<Money> dailyCap, Money fee) {

    public RouteCap {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(dailyCap, "dailyCap");
        Objects.requireNonNull(fee, "fee");

        if (fee.isNegative()) {
            throw new IllegalArgumentException("a route fee cannot be negative; got " + fee);
        }
        if (dailyCap.isPresent() && !dailyCap.get().isPositive()) {
            // A cap of zero would disable the route silently. Removing a route is a configuration
            // decision that should be made explicitly, not expressed as a zero somebody typed.
            throw new IllegalArgumentException(
                    "a daily cap is positive or absent; a zero cap disables " + route + " silently");
        }
    }

    public boolean hasCap() {
        return this.dailyCap.isPresent();
    }

    /**
     * What remains today on this route given what has already gone out on it.
     *
     * <p><b>This is not Rule B9.</b> {@code Money.flooredAtZero()} is reserved for the withdrawable
     * figure and has exactly one caller, deliberately — borrowing it here would make its
     * documentation false and quietly spread a clamp that is supposed to be conspicuous. The floor
     * below is its own decision with its own reason: negative headroom is not a number to show a
     * trader, and "you have −₹500 remaining" is worse than "you have none".
     *
     * <p>Negative headroom is nonetheless a real anomaly — it means a cap was lowered below what
     * has already gone out today, or a usage row was written twice — so callers that can alert
     * should use {@link #isOverCap} rather than reading zero and assuming all is well.
     *
     * @return empty when the route has no cap, meaning "unbounded" rather than any figure
     */
    public Optional<Money> remainingAfter(Money sentToday) {
        Objects.requireNonNull(sentToday, "sentToday");
        return this.dailyCap.map(cap -> {
            Money remaining = cap.minus(sentToday);
            return remaining.isNegative() ? Money.ZERO : remaining;
        });
    }

    /**
     * Whether more has already gone out today than this route's cap allows.
     *
     * <p>Should never be true. When it is, the cap was lowered mid-day or a usage row was
     * double-counted, and both want an operator rather than a silently zeroed headroom.
     */
    public boolean isOverCap(Money sentToday) {
        Objects.requireNonNull(sentToday, "sentToday");
        return this.dailyCap.map(cap -> sentToday.compareTo(cap) > 0).orElse(false);
    }
}
