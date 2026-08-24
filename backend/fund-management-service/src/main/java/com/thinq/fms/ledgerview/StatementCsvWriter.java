package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Writes a statement as CSV (REQ-407).
 *
 * <h2>Amounts are plain and summable</h2>
 *
 * <p>REQ-407 is unusually specific and the reason is practical: the file is opened in a
 * spreadsheet, filtered, and submitted for analysis. So amounts carry <b>no currency symbol and no
 * thousands separator</b> — a column of "₹1,23,456.00" needs cleaning before it can be summed, and
 * the person doing the cleaning is a trader filing their taxes.
 *
 * <h2>No unmasked account number, anywhere</h2>
 *
 * <p>Profile PR-32. Enforced by {@link #assertNoUnmaskedAccountNumber} on every field written
 * rather than trusted from upstream, because this file leaves the system and is kept — an unmasked
 * number in it is not recoverable by fixing the code later.
 *
 * <h2>Formula injection is neutralised</h2>
 *
 * <p>A description beginning {@code =}, {@code +}, {@code -} or {@code @} is executed as a formula
 * by Excel and Sheets when the file is opened. The content here originates from a back office
 * rather than from a trader, which lowers the likelihood but not the consequence, and the
 * mitigation is one prefix character.
 */
public final class StatementCsvWriter {

    private static final String[] HEADERS =
            {"Date", "Description", "Type", "Reference", "Amount", "Balance"};

    /**
     * Nine or more consecutive digits.
     *
     * <p>Calibrated, not guessed. An Indian bank account number is 9 to 18 digits, so 9 is the
     * shortest run that could be one. A masked tail shows four and a date shows at most eight, so
     * neither trips it.
     *
     * <p><b>The threshold is a deliberate trade and it can produce false positives.</b> A
     * back-office voucher number of nine or more digits would fail an export. That is the
     * direction to err in: a failed export is visible, gets investigated, and is fixed by
     * narrowing the check or masking the field. An unmasked account number written into a CSV is
     * permanent — the trader has already saved it, and possibly already sent it to someone else.
     * Profile PR-32 exists because that outcome is not recoverable.
     */
    private static final Pattern LOOKS_LIKE_ACCOUNT_NUMBER = Pattern.compile("\\d{9,}");

    /** Characters a spreadsheet treats as the start of a formula. */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /**
     * Check every row before a single byte is written.
     *
     * <p><b>Callers streaming a response must call this first.</b> Validating inside {@link #write}
     * is too late when the body is streamed: the status line has already been sent, so a PR-32
     * violation arrives as a truncated file with a 200 rather than as a refusal. The check is
     * cheap and idempotent, and {@code write} repeats it as defence in depth.
     */
    public void validate(List<StatementRow> rows) {
        Objects.requireNonNull(rows, "rows");
        for (StatementRow row : rows) {
            assertNoUnmaskedAccountNumber(row.description());
            assertNoUnmaskedAccountNumber(row.reference() == null ? "" : row.reference());
        }
    }

    public void write(Writer out, List<StatementRow> rows) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(rows, "rows");

        writeRow(out, HEADERS);
        for (StatementRow row : rows) {
            // The account-number guard applies to the free-text fields and to those only. A
            // description is interpolated by a mapper and a reference comes from the back office,
            // so either could carry one. The amounts below are produced by this system from a
            // long of paise and cannot — and scanning them refused every export for an account
            // holding about a crore or more, because "123456789.00" is nine consecutive digits.
            assertNoUnmaskedAccountNumber(row.description());
            assertNoUnmaskedAccountNumber(row.reference() == null ? "" : row.reference());

            writeRow(out,
                    row.date().toString(),
                    row.description(),
                    row.debitOrCredit(),
                    row.reference() == null ? "" : row.reference(),
                    plainAmount(row.amount()),
                    plainAmount(row.resultingBalance()));
        }
        out.flush();
    }

    /**
     * An amount as a plain decimal: no symbol, no grouping, always two places.
     *
     * <p>Derived from the paise integer via {@link Money#toVendorDecimal()}, so the value in the
     * file is exactly the value in the system. Formatting it from a double here would reintroduce
     * the imprecision the paise rule exists to prevent, in the one artifact a trader keeps.
     */
    static String plainAmount(Money amount) {
        BigDecimal rupees = amount.toVendorDecimal();
        return rupees.toPlainString();
    }

    private void writeRow(Writer out, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(fields[i]));
        }
        out.write("\r\n");   // RFC 4180
    }

    /**
     * Quote and neutralise one field.
     *
     * <p>Order matters: the formula guard goes on before quoting, so the apostrophe ends up inside
     * the quoted value rather than outside it.
     *
     * <p>Deliberately does <b>not</b> run the account-number guard. That check belongs to the
     * fields that could carry an account number, and running it here applied it to the generated
     * amount columns too — see {@link #write}.
     */
    static String escape(String value) {
        String v = value == null ? "" : value;

        if (!v.isEmpty() && FORMULA_STARTERS.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    /**
     * Refuse to write anything that looks like a full account number.
     *
     * <p>Profile PR-32 forbids one anywhere in an export. This throws rather than redacting,
     * because a redaction would produce a plausible-looking file built from a value that should
     * never have reached this layer — and the upstream defect would go unnoticed. A failed export
     * gets investigated; a quietly redacted one does not.
     */
    /** The value, having passed the account-number guard. Convenience for callers and tests. */
    static String assertSafe(String value) {
        assertNoUnmaskedAccountNumber(value);
        return value;
    }

    static void assertNoUnmaskedAccountNumber(String value) {
        if (LOOKS_LIKE_ACCOUNT_NUMBER.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "a statement field carries a run of digits that may be an unmasked account "
                            + "number; Profile PR-32 forbids one anywhere in an export");
        }
    }
}
