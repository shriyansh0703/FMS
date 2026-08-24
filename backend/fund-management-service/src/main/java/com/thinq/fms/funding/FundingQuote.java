package com.thinq.fms.funding;

import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What a trader is told before they commit to adding funds (REQ-202, Rule A3).
 *
 * <p><b>Rule A3 forbids using a route whose cost or arrival cannot be stated</b>, so this type
 * cannot represent a quote that omits either. There is no constructor that produces a route with an
 * unknown arrival — the unavailable case is its own value, and a caller has to handle it rather than
 * render a blank where a date should be.
 *
 * @param route             the route selected automatically; REQ-702 forbids asking the trader
 * @param amountPaid        what leaves their bank
 * @param amountCredited    what reaches the trading account, which differs when a cost applies
 * @param cost              the charge, zero where none applies
 * @param arrivesOn         the computed arrival date
 * @param routeChangedFrom  the route originally selected, where headroom forced a change (Rule A12)
 */
public record FundingQuote(PaymentRoute route,
                           Money amountPaid,
                           Money amountCredited,
                           Money cost,
                           LocalDate arrivesOn,
                           Optional<PaymentRoute> routeChangedFrom) {

    public FundingQuote {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(amountPaid, "amountPaid");
        Objects.requireNonNull(amountCredited, "amountCredited");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(arrivesOn, "arrivesOn");
        Objects.requireNonNull(routeChangedFrom, "routeChangedFrom");

        if (cost.isNegative()) {
            throw new IllegalArgumentException("a route cost is not negative; got " + cost);
        }
        if (!amountPaid.isPositive()) {
            throw new IllegalArgumentException("a funding amount is positive; got " + amountPaid);
        }
        // Rule A3 requires both figures shown together precisely so they can be checked against each
        // other. If they cannot reconcile here, the trader would be shown two numbers that do not
        // add up and asked to commit to them.
        if (!amountCredited.plus(cost).equals(amountPaid)) {
            throw new IllegalArgumentException(
                    "the amount credited plus the cost must equal the amount paid; "
                            + amountCredited + " + " + cost + " != " + amountPaid);
        }
        if (routeChangedFrom.map(from -> from == route).orElse(false)) {
            throw new IllegalArgumentException(
                    "a route change from " + route + " to itself is not a change; pass empty instead");
        }
    }

    /** A quote with no cost, where the amount paid is the amount credited. */
    public static FundingQuote free(PaymentRoute route, Money amount, LocalDate arrivesOn) {
        return new FundingQuote(route, amount, amount, Money.ZERO, arrivesOn, Optional.empty());
    }

    /** Whether the route differs from the one first selected, which Rule A12 requires disclosed. */
    public boolean routeWasChanged() {
        return this.routeChangedFrom.isPresent();
    }

    /** Whether a cost applies, which Rule A3 requires stated before commitment. */
    public boolean hasCost() {
        return this.cost.isPositive();
    }
}
