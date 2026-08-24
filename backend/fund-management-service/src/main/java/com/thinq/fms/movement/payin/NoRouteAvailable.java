package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.Money;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Why no route could carry an amount, with the figure the trader needs.
 *
 * <p>REQ-701 is explicit that a refusal must state the remaining headroom rather than being
 * generic: being stopped by a limit before paying is the point, and "that did not work" teaches
 * the trader nothing about what would.
 *
 * <p>Carries figures rather than copy, because Rule G1 forbids restating a cap in message text —
 * a template naming ₹2,00,000 becomes wrong the day Payments changes it, and nobody looks in
 * message copy when they change a limit.
 *
 * @param headroomByRoute what remains today on each capped route, so the client can say which
 *                        route came closest and by how much
 * @param bestHeadroom    the largest headroom available across all routes, or empty when no route
 *                        is executable at all
 */
public record NoRouteAvailable(
        Map<PaymentRoute, Money> headroomByRoute,
        Optional<Money> bestHeadroom) {

    public NoRouteAvailable {
        headroomByRoute = Map.copyOf(Objects.requireNonNull(headroomByRoute, "headroomByRoute"));
        Objects.requireNonNull(bestHeadroom, "bestHeadroom");
    }
}
