package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.movement.payout.InstructionKey;
import com.thinq.fms.movement.payout.InstructionResult;
import com.thinq.fms.movement.payout.PaymentInstruction;
import com.thinq.fms.movement.payout.PayoutRail;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementOutcome;
import com.thinq.fms.movement.payout.SettlementReasonCode;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The payout rail, over TechExcel's {@code Payout_Request_Addition} and
 * {@code Payment Request Status View Update}.
 *
 * <p>One of three systems that could execute a payout (hld.md R8). Exactly one implementation of
 * {@link PayoutRail} may be registered, asserted at startup — two live rails would instruct
 * independently and Rule W9's combine-before-instruct step would protect nothing.
 *
 * <p><b>The two endpoints are a pair, and the order matters.</b> {@link #statusOf} exists because
 * {@link #instruct} cannot be made safely idempotent from TechExcel's responses alone: its
 * duplication validation answers {@code Input_Value_Validation}, the identical code it uses for
 * an input-value rejection, with no distinguishing description (OA-7, verified against the
 * contract 21 Aug 2026). A re-run therefore reads the prior payment's status by
 * {@code UserRefNo} before deciding whether to send. Removing that read as redundant is how one
 * payout becomes two.
 */
public final class TechExcelPayoutRail extends TechExcelGateway implements PayoutRail {

    private static final String ADD_PATH = "/TechBoRest/api/entry/payout_request_addition_f";
    private static final String STATUS_PATH = "/TechBoRest/api/entry/payment_request_status_view_f";

    /** TechExcel's date fields are ten characters. */
    private static final DateTimeFormatter VOUCHER_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SettlementReasonMapper reasons;

    public TechExcelPayoutRail(JsonHttp http,
                               TechExcelSession session,
                               String companyCode,
                               SettlementReasonMapper reasons,
                               Duration callTimeout,
                               CircuitBreaker circuitBreaker,
                               MeterRegistry meters) {
        super(http, session, companyCode, callTimeout, circuitBreaker, meters);
        this.reasons = Objects.requireNonNull(reasons, "reasons");
    }

    @Override
    public InstructionResult instruct(PaymentInstruction instruction) {
        Objects.requireNonNull(instruction, "instruction");

        // ONE call() for the whole operation. Wrapping the status read in its own call() nested
        // the anti-corruption layer inside itself: two circuit-breaker samples per instruction,
        // two timers with the inner duration counted twice, and a slow status read reported as
        // the payout addition timing out.
        return call("payout_request_addition", () -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("client_id", instruction.account().ucc());
            fields.put("VOUCHERDATE", instruction.runDate().format(VOUCHER_DATE));
            // Amount is typed String with precision 20,2. toVendorAmount is the single
            // paise-to-decimal conversion point; no caller formats money itself.
            fields.put("Amount", toVendorAmount(instruction.amount()));
            fields.put("BankAccountNumber", instruction.destinationRef());
            // The composite idempotency key, in the twenty digits UserRefNo allows.
            fields.put("UserRefNo", instruction.key().userRefNo());

            postAuthenticated(ADD_PATH, body(fields));

            // Acceptance is not settlement. TechExcel takes the entry into an authorisation
            // queue, so the row that comes back is normally AUTHO = 0 with nothing authorised.
            // That is a pending instruction, not an outcome.
            return fetchStatus(instruction.key(), instruction.runDate())
                    .orElseGet(() -> new InstructionResult.PendingAuthorisation(instruction.amount()));
        });
    }

    @Override
    public Optional<InstructionResult> statusOf(InstructionKey key, LocalDate runDate) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(runDate, "runDate");

        return call("payment_request_status_view", () -> fetchStatus(key, runDate));
    }

    /**
     * Read the status view. <b>Not wrapped in {@code call()}</b> — the two public methods each
     * wrap it exactly once, so an instruction produces one breaker sample and one timer.
     */
    private Optional<InstructionResult> fetchStatus(InstructionKey key, LocalDate runDate) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("UserRefNo", key.userRefNo());
        fields.put("From_Date", runDate.format(VOUCHER_DATE));

        JsonNode response = postAuthenticated(STATUS_PATH, body(fields));
        JsonNode row = firstRow(response);

        // No record means nothing was instructed under this key. That is the only safe reading:
        // treating an absent record as "probably paid" would strand a trader's money, and
        // treating an error as absence would double-pay them. It is deliberately NOT the same
        // answer as "the record exists but is not authorised yet".
        return row == null ? Optional.empty() : Optional.of(toResult(key, row));
    }

    /**
     * Map TechExcel's status row onto a settlement outcome, per lld-backend.md §4.5.
     *
     * <pre>
     *   Reject = 1                     -> NOTHING_SENT
     *   AUTH_DUE_AMT &lt; Amount       -> PARTLY_PAID, reason from RMSData or Reject_Reason
     *   otherwise                      -> PAID
     * </pre>
     */
    private InstructionResult toResult(InstructionKey key, JsonNode row) {
        Money requested = amount(row, "Amount");
        Money authorised = amount(row, "AUTH_DUE_AMT");
        String rejectReason = text(row, "Reject_Reason");

        // A rejection is terminal whether or not the entry was ever authorised, so it is tested
        // first — a rejected row can carry an AUTH_DUE_AMT from an earlier authorisation step.
        if (isRejected(row)) {
            return new InstructionResult.Settled(new SettlementOutcome(
                    PayoutState.NOTHING_SENT, requested, Money.ZERO,
                    this.reasons.map(rejectReason), rejectReason,
                    bankReference(row, key), creditedOn(row)));
        }

        // NOT YET AUTHORISED. This is the normal state of a row read straight after posting, and
        // it is not an outcome. Falling through to the amount comparison read AUTH_DUE_AMT of
        // null as zero, concluded PARTLY_PAID with nothing sent, and closed a request whose money
        // had not yet moved — telling the trader they had received zero.
        if (!isAuthorised(row)) {
            return new InstructionResult.PendingAuthorisation(requested);
        }

        if (authorised.compareTo(requested) < 0) {
            Money blocked = amount(row, "RMSData");
            SettlementReasonCode code = blocked.isPositive()
                    // The one cause the contract lets this system name numerically. RMSData
                    // carries the amount held against open positions, which is exactly what
                    // REQ-308 asks be named for the gap.
                    ? SettlementReasonCode.MARGIN_BLOCKED
                    : this.reasons.map(rejectReason);

            return new InstructionResult.Settled(new SettlementOutcome(
                    PayoutState.PARTLY_PAID, requested, authorised,
                    code, rejectReason, bankReference(row, key), creditedOn(row)));
        }

        return new InstructionResult.Settled(new SettlementOutcome(
                PayoutState.PAID, requested, authorised,
                SettlementReasonCode.NONE, null, bankReference(row, key), creditedOn(row)));
    }

    /**
     * {@code AUTHO} is "1" when the payment entry has been authorised and "0" when it has not.
     *
     * <p>Absent is read as not authorised. A status view that omits the flag entirely is a shape
     * this system does not recognise, and the safe reading of an unrecognised payout status is
     * that the money has not moved — the opposite reading closes the request.
     */
    private static boolean isAuthorised(JsonNode row) {
        String autho = text(row, "AUTHO");
        return autho != null && autho.trim().equals("1");
    }

    /** {@code Reject} is "1" when rejected and null otherwise — not a boolean. */
    private static boolean isRejected(JsonNode row) {
        String reject = text(row, "Reject");
        return reject != null && reject.trim().equals("1");
    }

    /**
     * The bank's own reference, and never ours.
     *
     * <p>Rule C8: a trader chasing a payment needs the identifier their bank can trace. Handing
     * them the FMS reference would send them to a bank the value means nothing to.
     *
     * <p><b>The rule is enforced by V21's {@code fms_payout_refs_differ} constraint</b>, which
     * compares {@code bank_reference} against {@code fms_reference} — the two identifiers the rule
     * actually names. An earlier version of this method compared the gateway id against the
     * {@link InstructionKey} instead, which is the value sent as {@code UserRefNo} and is not the
     * FMS reference at all: it neither enforced Rule C8 nor was ever likely to fire, and it would
     * have raised a paging invariant if it had.
     */
    private static String bankReference(JsonNode row, InstructionKey key) {
        String gateway = text(row, "GateWayID");
        return gateway == null || gateway.isBlank() ? null : gateway;
    }

    private static LocalDate creditedOn(JsonNode row) {
        String utr = text(row, "UTR_TIME");
        if (utr == null || utr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(utr.substring(0, 10), VOUCHER_DATE);
        } catch (RuntimeException e) {
            // A date this system cannot read is recorded as unknown rather than guessed. REQ-303
            // compares the achieved date against the quoted one, and a wrong achieved date is
            // worse than an absent one.
            return null;
        }
    }

    private Money amount(JsonNode row, String field) {
        String raw = text(row, field);
        return raw == null || raw.isBlank() ? Money.ZERO : toPaise(raw.trim());
    }

    private static String text(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    /**
     * The single row for this {@code UserRefNo}.
     *
     * <p>More than one row for one key would mean TechExcel holds two payment entries under the
     * same reference, which makes "what happened to this instruction?" unanswerable. That pages
     * rather than picking one.
     */
    private static JsonNode firstRow(JsonNode response) {
        JsonNode data = response.isArray() ? response : response.get("Data");
        if (data == null || data.isNull() || !data.isArray() || data.isEmpty()) {
            return null;
        }
        if (data.size() > 1) {
            throw new FmsInvariantException("payout_status_ambiguous",
                    "TechExcel returned " + data.size() + " payment rows for one UserRefNo");
        }
        return data.get(0);
    }

    /**
     * {@code Input_Value_Validation} means something different here than on every other endpoint,
     * and the difference is the whole reason {@link #statusOf} exists.
     */
    @Override
    protected FmsException translateErrorCode(String path, TechExcelErrorCode code, JsonNode response) {
        if (code == TechExcelErrorCode.INPUT_VALUE_VALIDATION && ADD_PATH.equals(path)) {
            return new FmsInvariantException("payout_ambiguous_rejection",
                    "TechExcel refused a payout with Input_Value_Validation, which is both its "
                            + "input-value rejection and its duplication response. The caller must "
                            + "resolve this by reading payment status, never by re-sending.");
        }
        return super.translateErrorCode(path, code, response);
    }
}
