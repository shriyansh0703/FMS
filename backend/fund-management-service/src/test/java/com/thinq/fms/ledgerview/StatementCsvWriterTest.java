package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-407, Rule L8a and Profile PR-32.
 *
 * <p>This file leaves the system and is kept, so the assertions that matter most are the ones
 * about what must never be in it and about amounts being usable without cleaning.
 */
class StatementCsvWriterTest {

    private final StatementCsvWriter writer = new StatementCsvWriter();

    @Test
    @DisplayName("amounts are plain and summable — no symbol, no grouping, two places")
    void amountsArePlainAndSummable() throws Exception {
        // The trader opens this in a spreadsheet and sums the column. "₹1,23,456.00" needs
        // cleaning first, and the person doing the cleaning is someone filing their taxes.
        String csv = write(row("Funds added", StatementRow.CREDIT,
                Money.ofPaise(12_345_600L), Money.ofPaise(98_765_400L)));

        assertThat(csv).contains("123456.00");
        assertThat(csv).contains("987654.00");
        assertThat(csv).doesNotContain("₹").doesNotContain("INR");

        // The amount and balance fields specifically must carry no grouping. Checking the whole
        // document for a comma would match the CSV's own separators — an earlier version of this
        // assertion did exactly that and failed for the wrong reason.
        String[] fields = csv.lines().skip(1).findFirst().orElseThrow().split(",");
        assertThat(fields[4]).isEqualTo("123456.00");
        assertThat(fields[5]).isEqualTo("987654.00");
    }

    @Test
    @DisplayName("paise convert exactly, with no floating-point drift")
    void paiseConvertExactly() {
        // The one artifact a trader keeps is the wrong place to lose a paisa.
        assertThat(StatementCsvWriter.plainAmount(Money.ofPaise(1L))).isEqualTo("0.01");
        assertThat(StatementCsvWriter.plainAmount(Money.ofPaise(123_456L))).isEqualTo("1234.56");
        assertThat(StatementCsvWriter.plainAmount(Money.ofPaise(-500L))).isEqualTo("-5.00");
    }

    @Test
    @DisplayName("Rule L8a: the type column says Debit or Credit, not an internal kind")
    void typeColumnUsesBankStatementWords() {
        // The file is read against a bank statement, so it uses a bank statement's words.
        assertThatThrownBy(() -> new StatementRow(LocalDate.of(2026, 8, 21), "x",
                "SALE_PROCEEDS", "V1", Money.ZERO, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debit or Credit");
    }

    @Test
    @DisplayName("PR-32: an account number in a description fails the export")
    void unmaskedAccountNumberInDescriptionFailsTheExport() {
        // Throws rather than redacting. A redaction produces a plausible file built from a value
        // that should never have reached this layer, and the upstream defect goes unnoticed.
        //
        // Exercised through write() rather than escape(): the guard belongs to the free-text
        // fields, and asserting it on escape() is what let it be applied to the generated amount
        // columns too, where it refused every crore-plus balance.
        assertThatThrownBy(() -> write(row("Transfer to 501234567890", StatementRow.CREDIT,
                Money.ofPaise(100L), Money.ofPaise(100L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PR-32");
    }

    @Test
    @DisplayName("PR-32: an account number in a reference fails the export too")
    void unmaskedAccountNumberInReferenceFailsTheExport() {
        assertThatThrownBy(() -> write(new StatementRow(LocalDate.of(2026, 8, 21), "Payout",
                StatementRow.DEBIT, "501234567890", Money.ofPaise(100L), Money.ofPaise(100L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PR-32");
    }

    @Test
    @DisplayName("a crore-plus balance exports cleanly — the guard does not scan generated amounts")
    void largeBalanceExportsCleanly() throws Exception {
        // The F-12 regression. A balance of Rs 1,23,45,678.90 renders as "123456789.00", which is
        // nine consecutive digits. Scanning amount fields refused it, so any account holding about
        // a crore or more could not obtain the statement REQ-407 promises them.
        String csv = write(row("Sale proceeds", StatementRow.CREDIT,
                Money.ofPaise(100L), Money.ofPaise(12_345_678_900L)));

        assertThat(csv).contains("123456789.00");

        // And well past it, for an institutional-sized balance.
        assertThat(write(row("Sale proceeds", StatementRow.CREDIT,
                Money.ofPaise(100L), Money.ofPaise(1_234_567_890_000L))))
                .contains("12345678900.00");
    }

    @Test
    @DisplayName("a masked tail and a date pass the account-number guard")
    void maskedTailAndDatesAreNotFlagged() {
        // The guard must not fire on the values that legitimately appear, or every export fails.
        assertThat(StatementCsvWriter.assertSafe("Payout to ••••4471")).contains("4471");
        assertThat(StatementCsvWriter.assertSafe("2026-08-21")).isEqualTo("2026-08-21");
        assertThat(StatementCsvWriter.assertSafe("SETL-2026-0812")).isEqualTo("SETL-2026-0812");
        assertThat(StatementCsvWriter.assertSafe("VCH-12345678")).isEqualTo("VCH-12345678");
    }

    @Test
    @DisplayName("the guard is deliberately conservative and a 9-digit reference trips it")
    void nineDigitReferenceTripsTheGuardByDesign() {
        // Documented rather than accidental. A failed export is visible and fixable; an unmasked
        // account number in a saved file is not. If a real back office issues nine-digit voucher
        // numbers, the fix is to narrow the check with that knowledge, not to widen it blindly.
        assertThatThrownBy(() -> write(new StatementRow(LocalDate.of(2026, 8, 21), "Payout",
                StatementRow.DEBIT, "VOU123456789", Money.ofPaise(100L), Money.ofPaise(100L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a description that would execute as a spreadsheet formula is neutralised")
    void formulaInjectionIsNeutralised() {
        // Excel and Sheets execute a leading =, +, - or @ when the file is opened. The content
        // comes from a back office rather than a trader, which lowers the likelihood and not the
        // consequence.
        assertThat(StatementCsvWriter.escape("=cmd|'/c calc'!A1")).startsWith("'=");
        assertThat(StatementCsvWriter.escape("+1234")).startsWith("'+");
        assertThat(StatementCsvWriter.escape("@SUM(A1)")).startsWith("'@");
        assertThat(StatementCsvWriter.escape("Funds added")).isEqualTo("Funds added");
    }

    @Test
    @DisplayName("commas and quotes in a description are escaped per RFC 4180")
    void separatorsAreEscaped() throws Exception {
        String csv = write(row("Charges, including GST", StatementRow.DEBIT,
                Money.ofPaise(11_800L), Money.ofPaise(500_000L)));

        assertThat(csv).contains("\"Charges, including GST\"");
        assertThat(StatementCsvWriter.escape("He said \"hi\"")).isEqualTo("\"He said \"\"hi\"\"\"");
    }

    @Test
    @DisplayName("the header row names exactly REQ-407's six columns")
    void headerNamesTheRequiredColumns() throws Exception {
        String csv = write(row("x", StatementRow.CREDIT, Money.ZERO, Money.ZERO));

        assertThat(csv.lines().findFirst()).contains("Date,Description,Type,Reference,Amount,Balance");
    }

    @Test
    @DisplayName("an empty period still produces a header, not an empty file")
    void emptyPeriodStillHasAHeader() throws Exception {
        // A zero-byte download reads as a broken feature. A header with no rows reads as an
        // account with no entries in that period, which is what it is.
        StringWriter out = new StringWriter();
        writer.write(out, List.of());

        assertThat(out.toString().lines().count()).isEqualTo(1);
    }

    // ---- harness ----

    private String write(StatementRow... rows) throws Exception {
        StringWriter out = new StringWriter();
        this.writer.write(out, List.of(rows));
        return out.toString();
    }

    private static StatementRow row(String description, String type, Money amount, Money balance) {
        return new StatementRow(LocalDate.of(2026, 8, 21), description, type, "VCH-1", amount, balance);
    }
}
