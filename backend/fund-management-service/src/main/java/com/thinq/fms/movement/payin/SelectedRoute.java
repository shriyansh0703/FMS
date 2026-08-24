package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * The route this system chose, and what to tell the trader about the choice.
 *
 * <p>REQ-702 settled that <b>the system selects and the user does not</b>. With {@code nbFee} at
 * ₹0 there is no cost on which to compare routes, so offering the choice asked the trader to make
 * a decision they had no basis for and had not requested. What they get instead is disclosure:
 * the arrival date before committing, and the route named afterwards.
 *
 * @param route             the rail that will carry it
 * @param fee               what the trader pays for this route. ₹0 in this phase
 * @param remainingHeadroom what is left on this route today after this payment, or empty where the
 *                          route has no cap
 * @param switchedFrom      the route that would have been chosen but could not carry the amount,
 *                          or null when the first choice was usable. Rule A12 requires an automatic
 *                          re-route to <b>say so</b>, including any fee the change introduces
 */
public record SelectedRoute(
        PaymentRoute route,
        Money fee,
        Optional<Money> remainingHeadroom,
        PaymentRoute switchedFrom) {

    public SelectedRoute {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(remainingHeadroom, "remainingHeadroom");

        if (route == switchedFrom) {
            throw new IllegalArgumentException(
                    "a route cannot have been switched from itself; got " + route);
        }
    }

    /** Whether Rule A12's automatic re-route happened and must be disclosed. */
    public boolean wasSwitched() {
        return this.switchedFrom != null;
    }

    public Optional<PaymentRoute> switchedFromIfAny() {
        return Optional.ofNullable(this.switchedFrom);
    }
}
