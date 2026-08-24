package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.ledgerview.LedgerEntry;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TechExcel's {@code Ledger} endpoint — the source for the transaction list, the running balance,
 * and the settled ledger balance Rule B4's first term needs.
 *
 * <h2>OA-6 is answered, and the answer is half of what the design assumed</h2>
 *
 * <p>The LLD carried OA-6: "TechExcel's {@code Ledger} supports a date-bounded query per account
 * <b>with pagination</b>", flagged as the one assumption that does not fail safe. Read against
 * the contract on 21 Aug 2026, the endpoint's full input set is {@code Client_code}, {@code
 * FromDate}, {@code ToDate}, {@code COCDLIST}, {@code ShowAllData}, {@code ShowMargin},
 * {@code Merge_Company} and {@code TransType}.
 *
 * <p>So: <b>date-bounded per account, yes. Paginated, no.</b> There is no offset, no page size,
 * no cursor and no row limit. A query returns every entry in the window in one response.
 *
 * <p>That has two consequences this class cannot resolve on its own, and does not pretend to:
 *
 * <ol>
 *   <li>A wide window on an active account returns an unbounded response. This class bounds the
 *       <i>window</i> instead ({@link #MAX_WINDOW_DAYS}), which is the only lever the contract
 *       offers. A caller wanting a year walks it in windows.
 *   <li>REQ-403's "find a transaction by date, type and amount" cannot be served by asking
 *       TechExcel for page two. Either the client filters a bounded window, or the local entry
 *       mirror the LLD named as OA-6's failure path is needed.
 * </ol>
 *
 * <p>The second is a design decision above this layer, which is exactly what OA-6 said would
 * happen if the assumption failed: the question returns to the HLD rather than being absorbed
 * here. It is recorded, not decided.
 */
public final class TechExcelLedgerGateway extends TechExcelGateway
        implements com.thinq.fms.ledgerview.LedgerEntrySource {

    private static final String LEDGER_PATH = "/TechBoRest/api/entry/ledger";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * The widest window this gateway will request in one call.
     *
     * <p>Not a preference. With no pagination in the contract, the window is the only bound on
     * response size, and an unbounded response on a money path is a memory and latency failure
     * that arrives without warning on the busiest account.
     */
    public static final int MAX_WINDOW_DAYS = 92;

    public TechExcelLedgerGateway(JsonHttp http,
                                  TechExcelSession session,
                                  String companyCode,
                                  Duration callTimeout,
                                  CircuitBreaker circuitBreaker,
                                  MeterRegistry meters) {
        super(http, session, companyCode, callTimeout, circuitBreaker, meters);
    }

    /**
     * Every ledger entry for one account in one date window.
     *
     * @throws IllegalArgumentException if the window is inverted or wider than
     *     {@link #MAX_WINDOW_DAYS}. Refused here rather than truncated, because a silently
     *     truncated ledger is a trader's missing transaction
     */
    @Override
    public List<LedgerEntry> read(AccountRef account, LocalDate from, LocalDate to) {
        return entries(account, from, to);
    }

    public List<LedgerEntry> entries(AccountRef account, LocalDate from, LocalDate to) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("ledger window ends before it starts: " + from + " to " + to);
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (days > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                    "ledger window of " + days + " days exceeds the " + MAX_WINDOW_DAYS
                            + "-day maximum; TechExcel's Ledger has no pagination, so a caller "
                            + "wanting a wider range walks it in windows");
        }

        return call("ledger", () -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("Client_code", account.ucc());
            fields.put("FromDate", from.format(DATE));
            fields.put("ToDate", to.format(DATE));
            // Every entry in the window, margin entries included: Rule B4 needs the margin
            // entries and a ledger missing them would understate what is committed.
            fields.put("ShowAllData", "1");
            fields.put("ShowMargin", "1");

            JsonNode response = postAuthenticated(LEDGER_PATH, body(fields));
            return parse(response);
        });
    }

    private List<LedgerEntry> parse(JsonNode response) {
        JsonNode rows = response.isArray() ? response : response.get("Data");
        List<LedgerEntry> out = new ArrayList<>();
        if (rows == null || rows.isNull() || !rows.isArray()) {
            return out;
        }
        for (JsonNode row : rows) {
            out.add(toEntry(row));
        }
        return out;
    }

    private LedgerEntry toEntry(JsonNode row) {
        String voucherNo = text(row, "VOUCHERNO");
        if (voucherNo == null || voucherNo.isBlank()) {
            // An entry with no identifier cannot be referenced by support or paired with its
            // reversal, both of which REQ-404 requires.
            throw new FmsInvariantException("ledger_entry_without_voucher",
                    "TechExcel returned a ledger entry with no VOUCHERNO");
        }
        return new LedgerEntry(
                voucherNo,
                text(row, "COCD"),
                date(row, "VOUCHERDATE"),
                money(row, "DR_AMT"),
                money(row, "CR_AMT"),
                money(row, "CLOSING_AMT"),
                text(row, "NARRATION"),
                text(row, "TRANS_TYPE"),
                text(row, "SETTLEMENT_NO"),
                date(row, "SETL_PAYINDATE"),
                text(row, "MKT_TYPE"),
                "1".equals(trimmed(text(row, "OPENINGBALANCE"))),
                text(row, "USERREFNO"),
                text(row, "GATEWAYID"));
    }

    private Money money(JsonNode row, String field) {
        String raw = trimmed(text(row, field));
        return raw == null || raw.isBlank() ? Money.ZERO : toPaise(raw);
    }

    private static LocalDate date(JsonNode row, String field) {
        String raw = trimmed(text(row, field));
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10), DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    private static String trimmed(String s) {
        return s == null ? null : s.trim();
    }
}
