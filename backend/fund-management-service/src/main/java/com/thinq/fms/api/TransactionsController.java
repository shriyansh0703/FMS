package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import com.thinq.fms.api.dto.TransactionsResponse;
import com.thinq.fms.ledgerview.StatementCsvWriter;
import com.thinq.fms.ledgerview.TransactionPeriod;
import com.thinq.fms.ledgerview.TransactionQueryService;
import com.thinq.fms.ledgerview.TransactionView;
import com.thinq.fms.platform.money.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Transaction history and its export (lld-backend.md §4.1).
 *
 * <p>Rule L5's two views are one parameter on one endpoint rather than two endpoints, because
 * REQ-402 requires each view reachable from the other <b>without losing the selected period</b> —
 * and the surest way to satisfy that is for the period to be the same parameter on the same route.
 *
 * <p>The export takes the same parameters, which is Rule L8a: an export returns precisely what is
 * on screen. An export that quietly returned something else would be the one document the trader
 * cannot check against the page they exported it from.
 */
@RestController
@RequestMapping("/api/v1/funds")
@Tag(name = "Transactions", description = "History in either of Rule L5's two views, and its CSV export.")
public class TransactionsController {

    private final TransactionQueryService transactions;
    private final StatementCsvWriter csv;

    public TransactionsController(TransactionQueryService transactions, StatementCsvWriter csv) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.csv = Objects.requireNonNull(csv, "csv");
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transactions for a period, in one view",
            description = """
                    Rule L5 separates "where is my money" from "explain my account".
                    `MOVEMENTS` carries only money the trader moved in or out; `ALL_ENTRIES` carries
                    every entry with its running balance. Rule L5a is the line that is easy to get
                    wrong: sale proceeds and charges are not payins.

                    The period defaults to the last 30 days (Rule L6) — chosen because the mandated
                    return of unused funds runs monthly or quarterly and is among the most-queried
                    entries, so a shorter default shows an empty table for a transaction the trader
                    knows happened.

                    An empty period returns an explicit empty result with the period echoed and a
                    wider one suggested (Rule L7), never a bare empty array.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entries, possibly none"),
            @ApiResponse(responseCode = "400", description =
                    "`invalid_request` — an inverted period, or one wider than the back office's "
                            + "ledger can answer in a single call",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "`upstream_unavailable`",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionsResponse list(
            @Parameter(description = "MOVEMENTS or ALL_ENTRIES. Defaults to MOVEMENTS, the common question.")
            @RequestParam(defaultValue = "MOVEMENTS") TransactionView view,
            @Parameter(description = "Inclusive. Omit both bounds for Rule L6's 30-day default.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {

        AccountRef account = AuthenticatedAccount.of(principal);
        TransactionPeriod period = TransactionPeriod.orDefault(from, to, this.transactions.today());

        return TransactionsResponse.of(this.transactions.list(account, view, period));
    }

    @GetMapping("/transactions/{reference}")
    @Operation(summary = "One entry in full",
            description = """
                    Looked up across ALL_ENTRIES regardless of view, so an entry the movements view
                    filters out is still reachable by reference.

                    An entry belonging to another trader answers 404 rather than 403 — confirming
                    existence would itself leak.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The entry"),
            @ApiResponse(responseCode = "400", description =
                    "`invalid_request` — a period bound that will not parse as a date. The refusal "
                            + "names the parameter and does not echo the submitted value",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such entry for this account, in this period",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransactionsResponse.EntryDto> detail(
            @PathVariable String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {

        AccountRef account = AuthenticatedAccount.of(principal);
        TransactionPeriod period = TransactionPeriod.orDefault(from, to, this.transactions.today());

        return this.transactions.detail(account, period, reference)
                .map(TransactionsResponse.EntryDto::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    @Operation(summary = "The same view and period, as CSV (REQ-407)",
            description = """
                    Rule L8a: an export returns precisely what is on screen — the same view, the
                    same period, the same running balance.

                    Amounts are plain unformatted decimals with no currency symbol and no thousands
                    separator, so the file is summable in a spreadsheet without cleaning. The type
                    column reads Debit or Credit rather than an internal kind, because the file is
                    read against a bank statement.

                    No unmasked account number appears anywhere (Profile PR-32); a field that could
                    contain one fails the export rather than being redacted.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The statement",
                    content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "400", description = "`invalid_request`",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StreamingResponseBody> statement(
            @RequestParam(defaultValue = "ALL_ENTRIES") TransactionView view,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {

        AccountRef account = AuthenticatedAccount.of(principal);
        TransactionPeriod period = TransactionPeriod.orDefault(from, to, this.transactions.today());

        var rows = this.transactions.statementRows(account, view, period);

        // Validate BEFORE the response begins. Gathering the rows early is not enough on its own:
        // the PR-32 check lives inside the writer, which runs inside the streaming body, by which
        // point the 200 has already been sent — so a violation arrived as a truncated file rather
        // than as a refusal. A test asserted the 400 and caught exactly that.
        this.csv.validate(rows);

        StreamingResponseBody body = out -> {
            try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                this.csv.write(writer, rows);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"statement-" + period.from() + "-to-" + period.to() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
