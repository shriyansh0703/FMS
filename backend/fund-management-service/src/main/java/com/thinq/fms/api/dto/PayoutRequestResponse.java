package com.thinq.fms.api.dto;

import com.thinq.fms.movement.payout.PayoutRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * An accepted withdrawal request (lld-backend.md §4.2).
 *
 * @param requestId          this system's identifier, used to cancel
 * @param fmsReference       what support quotes. Rule C8 keeps this distinct from the bank's own
 *                           transfer reference, which appears only once the money has moved
 * @param arrivalDateQuoted  REQ-303's quote, computed from the settlement calendar. The achieved
 *                           date is recorded separately so the two can be compared
 * @param shrinkWarningKey   Rule W3a. A copy key the client resolves — the request reserves
 *                           nothing and settles against whatever is available at end of day, so
 *                           the amount can shrink, and the trader must be told <b>before</b> they
 *                           commit rather than when it happens
 * @param state              the request's current state
 */
@Schema(description = "An accepted withdrawal request. Rule W3: this reserves nothing.")
public record PayoutRequestResponse(
        @Schema(example = "4242") long requestId,
        @Schema(example = "FMS-2026-0821-4242") String fmsReference,
        @Schema(description = "REQ-303: when the money should arrive.") LocalDate arrivalDateQuoted,
        @Schema(description = "Rule W3a copy key, shown before commitment.",
                example = "WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT") String shrinkWarningKey,
        @Schema(example = "ACCEPTED") String state,
        @Schema(description = "The withdrawable figure at the moment of request (Rule W11).")
        MoneyDto withdrawableAtRequest,
        @Schema(description = "Masked destination. Never the full account number (Profile PR-31).",
                example = "••••4471") String destinationMasked) {

    /** Rule W3a: the warning is unconditional, because the shrink is always possible. */
    private static final String SHRINK_WARNING_KEY = "WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT";

    public static PayoutRequestResponse of(PayoutRequest request) {
        return new PayoutRequestResponse(
                request.id(),
                request.fmsReference(),
                request.arrivalDateQuoted(),
                SHRINK_WARNING_KEY,
                request.state().name(),
                MoneyDto.of(request.withdrawableAtRequest()),
                request.destinationMasked());
    }
}
