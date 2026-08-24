package com.thinq.fms.api;

import com.thinq.fms.api.dto.MoneyDto;
import com.thinq.fms.api.dto.RouteHeadroomResponse;
import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.movement.payin.RouteCap;
import com.thinq.fms.movement.payin.RouteCapLedger;
import com.thinq.fms.platform.money.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Remaining daily headroom per route (REQ-701).
 *
 * <p>Returns figures and lets the client render them. Rule G1 forbids restating a cap in message
 * copy: a template naming ₹2,00,000 becomes wrong the day Payments changes the value, and nobody
 * looks in message templates when they change a limit.
 */
@RestController
@RequestMapping("/api/v1/funds/payin")
@Tag(name = "Adding funds", description = "Route limits, measured against what has already gone out today.")
public class PayinLimitsController {

    private final RouteCapLedger caps;
    private final Map<PaymentRoute, RouteCap> configuration;

    public PayinLimitsController(RouteCapLedger caps, Map<PaymentRoute, RouteCap> configuration) {
        this.caps = Objects.requireNonNull(caps, "caps");
        this.configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
    }

    @GetMapping("/limits")
    @Operation(summary = "Remaining headroom on each route today",
            description = """
                    REQ-701 and Rule A12. The cap is daily and measured against everything already
                    sent on that route today — not per transaction, which would let the same amount
                    pass twice and defer the refusal to the trader's own bank.

                    A route with no cap returns a null `remainingToday`. That means unbounded and
                    must not be rendered as zero.""")
    public RouteHeadroomResponse limits(Principal principal) {
        AccountRef account = AuthenticatedAccount.of(principal);

        List<RouteHeadroomResponse.RouteHeadroom> routes = new ArrayList<>();
        for (Map.Entry<PaymentRoute, RouteCap> entry : this.configuration.entrySet()) {
            routes.add(new RouteHeadroomResponse.RouteHeadroom(
                    entry.getKey().name(),
                    this.caps.remainingToday(account, entry.getKey()).map(MoneyDto::of).orElse(null),
                    MoneyDto.of(entry.getValue().fee())));
        }
        return new RouteHeadroomResponse(routes);
    }
}
