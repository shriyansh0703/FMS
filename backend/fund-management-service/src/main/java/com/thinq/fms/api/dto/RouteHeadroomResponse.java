package com.thinq.fms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What remains on each route today (REQ-701).
 *
 * <p>Figures, not copy. Rule G1 forbids restating a cap in message text, because a template naming
 * ₹2,00,000 becomes a lie the day Payments changes the value and nobody looks in message copy when
 * they change a limit. The client renders from these.
 */
@Schema(description = "Remaining daily headroom per payment route, measured against today's usage.")
public record RouteHeadroomResponse(List<RouteHeadroom> routes) {

    /**
     * @param remainingToday what is left today, or null where the route has no cap at all. Null
     *     means unbounded and must not be rendered as zero — reading it as zero would tell a
     *     trader NEFT is exhausted when it has no limit
     */
    @Schema(description = "One route's remaining headroom. A null cap means unbounded, not zero.")
    public record RouteHeadroom(
            @Schema(example = "UPI") String route,
            @Schema(description = "Remaining today. Null when the route is uncapped.")
            MoneyDto remainingToday,
            @Schema(description = "What this route costs the trader. Zero in this phase.")
            MoneyDto fee) {
    }
}
