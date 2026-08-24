package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import com.thinq.fms.api.dto.PayoutRequestCommand;
import com.thinq.fms.api.dto.PayoutRequestResponse;
import com.thinq.fms.movement.payout.PayoutOrchestrator;
import com.thinq.fms.movement.payout.PayoutRequest;
import com.thinq.fms.platform.money.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

/**
 * Withdrawal requests (lld-backend.md §4.1).
 *
 * <p>The controller does three things: resolve the account from the principal, translate DTOs, and
 * call the orchestrator. Every rule — the amount against the withdrawable figure, the destination's
 * verification, Rule W4's one-open-request — lives in the domain or in the database, because §4.3
 * is explicit that a rule enforced only at the edge is a rule a second caller can skip.
 *
 * <p><b>No endpoint here takes an account identifier.</b> It comes from the authenticated subject,
 * so a caller cannot name someone else's account by editing a path or a body.
 */
@RestController
@RequestMapping("/api/v1/funds/payout")
@Tag(name = "Withdrawals", description = "Requesting and cancelling withdrawals. Rule W3: a request reserves nothing.")
public class PayoutController {

    private final PayoutOrchestrator orchestrator;

    public PayoutController(PayoutOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    }

    // consumes is declared explicitly (Stage 11, MEDIUM-1). With CSRF disabled, the property that
    // stops a cross-site forgery is that only JSON reaches this handler: the three content types an
    // HTML form can send cross-origin without a preflight get 415, and cross-origin JSON needs a
    // preflight no CORS configuration grants.
    //
    // Honest about what this line buys: removing it does NOT change today's behaviour — the message
    // converters already refuse those content types, which was measured, not assumed. It is here to
    // state the intent where a reader will see it, and to keep the refusal if a form-binding
    // converter is ever added. The behaviour itself is guarded by PayoutCsrfSurfaceTest, which is
    // sensitive to the converters rather than to this annotation.
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create the account's single open withdrawal request",
            description = """
                    Rule W3: the request reserves nothing. It is settled at end of day against
                    whatever is available then, which is why the response carries a shrink warning
                    key that must be shown before the trader commits.

                    Rule W4 allows one open request per account, and it is enforced by a partial
                    unique index rather than by a check here — a read-then-write would be a race
                    dressed as validation.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Malformed request",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description =
                    "`request_already_open` (Rule W4), `withdrawable_unavailable` (the derivation "
                            + "and RMS disagree), or `figures_stale`",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description =
                    "`amount_exceeds_withdrawable` (carries the figure) or `destination_not_verified`",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "`upstream_unavailable` or `calendar_unavailable`",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PayoutRequestResponse> request(@Valid @RequestBody PayoutRequestCommand command,
                                                         Principal principal) {
        AccountRef account = AuthenticatedAccount.of(principal);
        PayoutRequest created = this.orchestrator.request(
                account, command.amount().toMoney(), command.destinationRef());

        return ResponseEntity.status(HttpStatus.CREATED).body(PayoutRequestResponse.of(created));
    }

    @DeleteMapping("/{requestId}")
    @Operation(summary = "Cancel a request that has not yet been instructed",
            description = """
                    REQ-305. Permitted while ACCEPTED or QUEUED_FOR_RUN — REQ-619 keeps it
                    available after a rail outage, since a trader whose payout was deferred has
                    more reason to want it stopped, not less.

                    A request belonging to another trader answers 409 `not_cancellable` with
                    reason NOT_FOUND rather than 403: confirming existence would itself leak.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled"),
            @ApiResponse(responseCode = "400", description =
                    "`invalid_request` — the id in the path is not a number. Declared because it is "
                            + "reachable: the path variable is a `long`, so an unparseable segment is "
                            + "refused at binding and never reaches the handler",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description =
                    "`not_cancellable`, with reason ALREADY_INSTRUCTED, ALREADY_TERMINAL or NOT_FOUND",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    public PayoutRequestResponse cancel(@PathVariable long requestId, Principal principal) {
        AccountRef account = AuthenticatedAccount.of(principal);
        return PayoutRequestResponse.of(this.orchestrator.cancel(account, requestId));
    }

    @GetMapping
    @Operation(summary = "The account's open request, if any",
            description = "Returns 204 when there is none, which is an ordinary state rather than an error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The open request"),
            @ApiResponse(responseCode = "204", description = "No open request")
    })
    public ResponseEntity<PayoutRequestResponse> openRequest(Principal principal) {
        AccountRef account = AuthenticatedAccount.of(principal);
        Optional<PayoutRequest> open = this.orchestrator.openRequest(account);

        return open.map(r -> ResponseEntity.ok(PayoutRequestResponse.of(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
