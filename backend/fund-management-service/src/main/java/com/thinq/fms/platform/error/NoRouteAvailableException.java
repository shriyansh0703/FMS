package com.thinq.fms.platform.error;

import com.thinq.fms.movement.payin.NoRouteAvailable;

/**
 * No configured route has the headroom to carry this amount today.
 *
 * <p>Carries the headroom figures because REQ-701 requires the remaining amount stated rather than
 * a generic refusal: being stopped by a limit before paying is the point, and "that did not work"
 * teaches the trader nothing about what would.
 *
 * <p><b>This consumes no attempt.</b> Nothing was sent to a gateway and nothing counts against a
 * cap, so a trader who lowers the amount and retries is not penalised for the refusal.
 */
public class NoRouteAvailableException extends FmsUnprocessableException {

    private final transient NoRouteAvailable detail;

    public NoRouteAvailableException(NoRouteAvailable detail) {
        super("no_route_available", "no payment route has the headroom for this amount today");
        this.detail = detail;
    }

    public NoRouteAvailable detail() {
        return this.detail;
    }
}
