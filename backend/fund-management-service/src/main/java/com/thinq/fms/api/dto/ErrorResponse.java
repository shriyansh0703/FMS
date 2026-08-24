package com.thinq.fms.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * The one error shape every endpoint returns (lld-backend.md §4.4).
 *
 * <p><b>The code is the contract; the message is not.</b> Clients branch on {@code code} and
 * resolve their own copy from it, which is what lets wording change without a client release and
 * what stops an internal string reaching a trader. The message is a developer-facing explanation
 * and is deliberately not guaranteed stable.
 *
 * @param code    stable, machine-readable, safe to send to a client
 * @param message developer-facing. Never rendered to a trader as copy
 * @param details values the client needs to explain the refusal without a second request — the
 *                withdrawable figure behind an {@code amount_exceeds_withdrawable}, the remaining
 *                headroom behind a {@code no_route_available}. Absent where there are none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The uniform error body. Clients branch on `code`, never on `message`.")
public record ErrorResponse(
        @Schema(description = "Stable machine-readable code.", example = "amount_exceeds_withdrawable")
        String code,

        @Schema(description = "Developer-facing explanation. Not user copy.")
        String message,

        @Schema(description = "Values needed to explain the refusal without re-fetching.")
        Map<String, Object> details) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, details.isEmpty() ? null : Map.copyOf(details));
    }
}
