package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;

/**
 * The margin figures one source reports for one account, at one instant.
 *
 * <p>Every field is a {@link Money} in paise. No figure here is a rupee decimal and none is a
 * float — the conversion happened once, at the gateway that produced this (HLD §9.1c).
 *
 * @param availableMargin       what may be deployed on a new trade right now
 * @param usedMargin            what open positions currently consume
 * @param collateralValue       the haircut value of pledged securities. REQ-104 requires this
 *                              shown separately and never counted as withdrawable
 * @param committedMetFromCollateral the part of the margin requirement that pledged securities
 *                              covered, which Rule B4 adds back because that cash was never
 *                              truly committed. The counter-intuitive term
 * @param shortfall             what positions require beyond what the account holds. Zero when
 *                              there is no shortfall, never negative
 */
public record MarginFigures(
        Money availableMargin,
        Money usedMargin,
        Money collateralValue,
        Money committedMetFromCollateral,
        Money shortfall) {

    public MarginFigures {
        Objects.requireNonNull(availableMargin, "availableMargin");
        Objects.requireNonNull(usedMargin, "usedMargin");
        Objects.requireNonNull(collateralValue, "collateralValue");
        Objects.requireNonNull(committedMetFromCollateral, "committedMetFromCollateral");
        Objects.requireNonNull(shortfall, "shortfall");

        // A shortfall is a magnitude, like the derivation terms it feeds. A negative one would
        // mean "the account is over-margined", which is surplus and belongs in availableMargin.
        if (shortfall.isNegative()) {
            throw new IllegalArgumentException(
                    "shortfall is a magnitude and cannot be negative; surplus belongs in availableMargin");
        }
        if (collateralValue.isNegative()) {
            throw new IllegalArgumentException("collateralValue cannot be negative");
        }
        if (committedMetFromCollateral.isNegative()) {
            throw new IllegalArgumentException("committedMetFromCollateral cannot be negative");
        }
    }

    public boolean hasShortfall() {
        return this.shortfall.isPositive();
    }
}
