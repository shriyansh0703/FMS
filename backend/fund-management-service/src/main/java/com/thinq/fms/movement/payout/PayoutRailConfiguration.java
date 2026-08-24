package com.thinq.fms.movement.payout;

import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Asserts that exactly one {@link PayoutRail} is configured (lld-backend.md §7.6, hld.md R8).
 *
 * <p><b>Why this is a bean and not a comment.</b> Three systems in this estate can execute a
 * payout: Noren has {@code WithdrawFunds}, TechExcel has {@code Payout_Request_Addition}, and
 * Juspay has payout orders. If two were ever live at once they would instruct independently, and
 * Rule W9's combine-before-instruct step — the thing that stops one trader receiving two payments
 * for one request — would be protecting nothing.
 *
 * <p>A convention cannot enforce that. A constructor that refuses to start can. The failure is at
 * boot, on every environment, before any money moves.
 *
 * <p>Zero rails fails too, and deliberately. A service that starts with no payout rail accepts
 * withdrawal requests it can never settle, and the trader finds out at end of day.
 */
@Configuration
public class PayoutRailConfiguration {

    private final PayoutRail rail;

    public PayoutRailConfiguration(List<PayoutRail> rails) {
        if (rails.size() != 1) {
            throw new IllegalStateException(
                    "Exactly one PayoutRail must be configured; found " + rails.size()
                            + " " + rails.stream().map(r -> r.getClass().getSimpleName()).toList()
                            + ". Two live rails void Rule W9's no-double-payout guarantee, and none "
                            + "means accepting withdrawals that can never settle.");
        }
        this.rail = rails.get(0);
    }

    public PayoutRail rail() {
        return this.rail;
    }
}
