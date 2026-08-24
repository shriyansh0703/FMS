package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses the route a payment takes (REQ-702, Rule A12).
 *
 * <p><b>The system selects; the trader does not.</b> REQ-702 settled a direct conflict with an
 * earlier requirement that every route be presented and chosen from. With {@code nbFee} at ₹0
 * there is no cost to compare, so the choice offered a decision nobody had a basis to make. Rule
 * A12's automatic re-route became the primary mechanism rather than an exception to a rule that
 * contradicted it.
 *
 * <p><b>Order is preference, not capability.</b> The list below is the order routes are tried, and
 * every route in it is one this system can execute — Rule A9d forbids offering a self-service
 * route, because the button would promise a payment and deliver instructions.
 *
 * <p>Pure apart from the ledger read, so its behaviour is fully testable against a stub ledger.
 */
public final class RouteSelector {

    /**
     * Preference order.
     *
     * <p>UPI first because it settles fastest and costs nothing; net banking next for amounts
     * above UPI's ceiling; NEFT last because it is uncapped and therefore always able to carry
     * whatever the first two could not — which is what makes the fallback terminate.
     */
    private static final List<PaymentRoute> PREFERENCE =
            List.of(PaymentRoute.UPI, PaymentRoute.NET_BANKING, PaymentRoute.NEFT);

    private final RouteCapLedger caps;
    private final Map<PaymentRoute, RouteCap> configuration;

    public RouteSelector(RouteCapLedger caps, Map<PaymentRoute, RouteCap> configuration) {
        this.caps = Objects.requireNonNull(caps, "caps");
        this.configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Pick a route for an amount, or explain why none can carry it.
     *
     * <p>Walks the preference order and takes the first route whose <b>remaining headroom today</b>
     * covers the amount. Headroom, not the cap: Rule A12 measures against what is already spent,
     * and checking the cap alone would let the same amount pass twice.
     *
     * @return the chosen route, or a {@link NoRouteAvailable} carrying the headroom figures
     *     REQ-701 requires stated rather than a generic refusal
     */
    public Selection select(AccountRef account, Money amount) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a payin is for a positive amount; got " + amount);
        }

        PaymentRoute firstChoice = null;
        Map<PaymentRoute, Money> headroom = new LinkedHashMap<>();

        for (PaymentRoute route : PREFERENCE) {
            RouteCap cap = this.configuration.get(route);
            if (cap == null) {
                // A route with no configuration is not executable. Treating a missing entry as
                // "uncapped" would send money down a rail nobody configured.
                continue;
            }
            if (firstChoice == null) {
                firstChoice = route;
            }

            Optional<Money> remaining = this.caps.remainingToday(account, route);
            remaining.ifPresent(m -> headroom.put(route, m));

            boolean fits = remaining.isEmpty() || remaining.get().compareTo(amount) >= 0;
            if (fits) {
                // switchedFrom is set only when an earlier, more preferred route was tried and
                // could not carry it — Rule A12 requires the change disclosed, and disclosing a
                // change that did not happen would be as confusing as hiding one that did.
                PaymentRoute switchedFrom = route == firstChoice ? null : firstChoice;
                return new Selection(
                        new SelectedRoute(route, cap.fee(), remaining, switchedFrom), null);
            }
        }

        Optional<Money> best = headroom.values().stream().max(Money::compareTo);
        return new Selection(null, new NoRouteAvailable(headroom, best));
    }

    /**
     * Routes that could carry an amount right now, for Rule A9d's recovery offer.
     *
     * <p>Rule A9d: the alternative route offered beside <i>Try Again</i> is shown only if this
     * system can execute it and its remaining headroom covers the amount. Where nothing qualifies,
     * <i>Try Again</i> stands alone — so this returning empty is a supported answer rather than a
     * problem to work around.
     *
     * @param excluding the route that just failed, which is not an alternative to itself
     */
    public List<PaymentRoute> alternativesFor(AccountRef account, Money amount, PaymentRoute excluding) {
        List<PaymentRoute> out = new ArrayList<>();
        for (PaymentRoute route : PREFERENCE) {
            if (route == excluding || !this.configuration.containsKey(route)) {
                continue;
            }
            Optional<Money> remaining = this.caps.remainingToday(account, route);
            if (remaining.isEmpty() || remaining.get().compareTo(amount) >= 0) {
                out.add(route);
            }
        }
        return out;
    }

    /**
     * Either a chosen route or the reason none was available. Exactly one is non-null.
     *
     * <p>A record rather than a nullable return or an exception: "no route has headroom today" is
     * an ordinary product state carrying figures the trader must see, and throwing would make the
     * headroom something a catch block has to dig out.
     */
    public record Selection(SelectedRoute selected, NoRouteAvailable unavailable) {

        public Selection {
            if ((selected == null) == (unavailable == null)) {
                throw new IllegalArgumentException(
                        "a selection is exactly one of a chosen route or an explanation");
            }
        }

        public boolean isSelected() {
            return this.selected != null;
        }
    }
}
