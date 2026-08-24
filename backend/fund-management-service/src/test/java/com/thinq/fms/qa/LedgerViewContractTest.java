package com.thinq.fms.qa;

import com.thinq.fms.ledgerview.EntryDescriptionMapper;
import com.thinq.fms.ledgerview.EntryKind;
import com.thinq.fms.ledgerview.LedgerEntry;
import com.thinq.fms.ledgerview.StatementRow;
import com.thinq.fms.ledgerview.TransactionEntry;
import com.thinq.fms.ledgerview.TransactionPage;
import com.thinq.fms.ledgerview.TransactionPeriod;
import com.thinq.fms.ledgerview.TransactionView;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalogue section TC-TXN, value contracts — {@code docs/qa/test-cases.md}.
 *
 * <p>The query service and the CSV writer are tested end to end. The shapes between them were not:
 * {@code StatementRow} sat at 33% branch with the guard that keeps an internal kind out of the
 * Debit/Credit column unexercised, and {@code TransactionPage} carried Rule L7's "a wider period is
 * offered only when this one is empty" invariant with nothing asserting it.
 */
class LedgerViewContractTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    // ------------------------------------------------------------------- LedgerEntry (Rule L1/L2)

    @Test
    @DisplayName("TC-TXN-036 — an entry that is both a debit and a credit is refused")
    void anEntryCannotBeBothADebitAndACredit() {
        assertThatThrownBy(() -> entry(Money.ofPaise(100L), Money.ofPaise(100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }

    @Test
    @DisplayName("TC-TXN-037 — a negative debit or credit is refused, because a reversal is its own entry")
    void aNegativeDebitOrCreditIsRefused() {
        // Rule L2: a correction is a compensating entry, never a sign flip on the original. A
        // negative debit would make the running balance disagree with the entries explaining it.
        assertThatThrownBy(() -> entry(Money.ofPaise(-1L), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rule L2");
        assertThatThrownBy(() -> entry(Money.ZERO, Money.ofPaise(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-TXN-038 — an entry with neither a debit nor a credit is accepted as a zero movement")
    void aZeroEntryIsAccepted() {
        assertThatCode(() -> entry(Money.ZERO, Money.ZERO)).doesNotThrowAnyException();
        assertThat(entry(Money.ZERO, Money.ZERO).isCredit()).isFalse();
    }

    @Test
    @DisplayName("TC-TXN-039 — the signed effect on the balance is the credit less the debit")
    void theSignedEffectIsCreditLessDebit() {
        assertThat(entry(Money.ZERO, Money.ofPaise(5_000L)).signedAmount())
                .isEqualTo(Money.ofPaise(5_000L));
        assertThat(entry(Money.ofPaise(5_000L), Money.ZERO).signedAmount())
                .isEqualTo(Money.ofPaise(-5_000L));
    }

    @Test
    @DisplayName("TC-TXN-040 — a settlement pay-in date is what marks an entry a trade contract note")
    void aSettlementPayinDateMarksATransactionBill() {
        // The unsettled-proceeds deduction in Rule B4 is measured in settlement days, and those
        // dates come from entries like this one. Without the field there is no way to tell a
        // contract note from a cash movement.
        assertThat(entry(Money.ZERO, Money.ofPaise(1L)).isTransactionBill()).isFalse();

        LedgerEntry bill = new LedgerEntry(
                "V-2", "NSE_CASH", TODAY, Money.ZERO, Money.ofPaise(1L), Money.ofPaise(1L),
                "SALE", "J", "SETL-9", TODAY.plusDays(1), "M-T+1 Normal", false, null, null);

        assertThat(bill.isTransactionBill()).isTrue();
    }

    @Test
    @DisplayName("TC-TXN-041 — an entry without its identifier, date or amounts is refused")
    void anEntryWithoutItsRequiredFieldsIsRefused() {
        assertThatThrownBy(() -> new LedgerEntry(
                null, "NSE_CASH", TODAY, Money.ZERO, Money.ZERO, Money.ZERO,
                "n", "R", null, null, null, false, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LedgerEntry(
                "V-1", "NSE_CASH", null, Money.ZERO, Money.ZERO, Money.ZERO,
                "n", "R", null, null, null, false, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --------------------------------------------------------------------- StatementRow (Rule L8a)

    @Test
    @DisplayName("TC-TXN-042 — a statement row is Debit or Credit, never an internal kind")
    void aStatementRowIsDebitOrCredit() {
        // Rule L8a names these two words because the file is read against a bank statement. An
        // internal kind leaking into the column is the exact defect the rule was written against.
        assertThatThrownBy(() -> new StatementRow(
                TODAY, "Funds added", "PAYIN", "V-1", Money.ofPaise(1L), Money.ofPaise(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debit or Credit");
    }

    @Test
    @DisplayName("TC-TXN-043 — the Debit/Credit column is case-sensitive and rejects a lowercase spelling")
    void theColumnIsCaseSensitive() {
        assertThatThrownBy(() -> new StatementRow(
                TODAY, "Funds added", "credit", "V-1", Money.ofPaise(1L), Money.ofPaise(1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatementRow(
                TODAY, "Funds added", null, "V-1", Money.ofPaise(1L), Money.ofPaise(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-TXN-044 — a credit entry becomes a Credit row carrying the credit amount")
    void aCreditEntryBecomesACreditRow() {
        StatementRow row = StatementRow.of(
                entry(Money.ZERO, Money.ofPaise(25_000L)), "Funds added");

        assertThat(row.debitOrCredit()).isEqualTo(StatementRow.CREDIT);
        assertThat(row.amount()).isEqualTo(Money.ofPaise(25_000L));
        assertThat(row.description()).isEqualTo("Funds added");
    }

    @Test
    @DisplayName("TC-TXN-045 — a debit entry becomes a Debit row carrying the debit amount")
    void aDebitEntryBecomesADebitRow() {
        StatementRow row = StatementRow.of(
                entry(Money.ofPaise(25_000L), Money.ZERO), "Withdrawal");

        assertThat(row.debitOrCredit()).isEqualTo(StatementRow.DEBIT);
        assertThat(row.amount()).isEqualTo(Money.ofPaise(25_000L));
    }

    @Test
    @DisplayName("TC-TXN-046 — the settlement number is preferred as the reference, and the voucher backs it")
    void theSettlementNumberIsPreferredAsTheReference() {
        LedgerEntry withSettlement = new LedgerEntry(
                "V-1", "NSE_CASH", TODAY, Money.ZERO, Money.ofPaise(1L), Money.ofPaise(1L),
                "n", "R", "SETL-9", null, null, false, null, null);
        LedgerEntry blankSettlement = new LedgerEntry(
                "V-2", "NSE_CASH", TODAY, Money.ZERO, Money.ofPaise(1L), Money.ofPaise(1L),
                "n", "R", "   ", null, null, false, null, null);

        assertThat(StatementRow.of(withSettlement, "x").reference()).isEqualTo("SETL-9");
        assertThat(StatementRow.of(blankSettlement, "x").reference())
                .as("a blank settlement number is not a reference")
                .isEqualTo("V-2");
        assertThat(StatementRow.of(entry(Money.ZERO, Money.ofPaise(1L)), "x").reference())
                .isEqualTo("V-1");
    }

    @Test
    @DisplayName("TC-TXN-047 — the running balance on a row is the back office's, carried through unchanged")
    void theRunningBalanceIsCarriedThrough() {
        // hld.md §9.1b: TechExcel supplies CLOSING_AMT and this system never accumulates one.
        // Two systems computing one running balance is Rule B12's failure mode.
        LedgerEntry source = new LedgerEntry(
                "V-1", "NSE_CASH", TODAY, Money.ZERO, Money.ofPaise(1_000L), Money.ofPaise(77_777L),
                "n", "R", null, null, null, false, null, null);

        assertThat(StatementRow.of(source, "x").resultingBalance()).isEqualTo(Money.ofPaise(77_777L));
    }

    // -------------------------------------------------------------------- TransactionPage (L5/L7)

    @Test
    @DisplayName("TC-TXN-048 — a wider period is offered only when the current one is empty")
    void aWiderPeriodIsOfferedOnlyWhenEmpty() {
        // Offering one alongside results would suggest the results are incomplete, which is the
        // opposite of what Rule L7 is for.
        TransactionPeriod period = TransactionPeriod.lastThirtyDays(TODAY);

        assertThatThrownBy(() -> new TransactionPage(
                TransactionView.MOVEMENTS, period, List.of(transactionEntry()), period.widened()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only when this one is empty");
    }

    @Test
    @DisplayName("TC-TXN-049 — an empty page states it is empty and offers the wider period")
    void anEmptyPageOffersTheWiderPeriod() {
        TransactionPeriod period = TransactionPeriod.lastThirtyDays(TODAY);
        TransactionPage page = new TransactionPage(
                TransactionView.MOVEMENTS, period, List.of(), period.widened());

        assertThat(page.isEmpty()).isTrue();
        assertThat(page.widerPeriodIfEmpty()).contains(period.widened());
    }

    @Test
    @DisplayName("TC-TXN-050 — a populated page offers no wider period")
    void aPopulatedPageOffersNoWiderPeriod() {
        TransactionPage page = new TransactionPage(
                TransactionView.ALL_ENTRIES, TransactionPeriod.lastThirtyDays(TODAY),
                List.of(transactionEntry()), null);

        assertThat(page.isEmpty()).isFalse();
        assertThat(page.widerPeriodIfEmpty()).isEmpty();
    }

    @Test
    @DisplayName("TC-TXN-051 — a page's entries cannot be mutated after construction")
    void aPagesEntriesCannotBeMutated() {
        List<TransactionEntry> mutable = new ArrayList<>(List.of(transactionEntry()));
        TransactionPage page = new TransactionPage(
                TransactionView.MOVEMENTS, TransactionPeriod.lastThirtyDays(TODAY), mutable, null);

        mutable.clear();

        assertThat(page.entries()).hasSize(1);
    }

    @Test
    @DisplayName("TC-TXN-052 — a page without its view or period is refused")
    void aPageWithoutItsViewOrPeriodIsRefused() {
        assertThatThrownBy(() -> new TransactionPage(
                null, TransactionPeriod.lastThirtyDays(TODAY), List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionPage(
                TransactionView.MOVEMENTS, null, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------ TransactionPeriod (L6/L7)

    @Test
    @DisplayName("TC-TXN-053 — the default period is thirty days, inclusive of today")
    void theDefaultPeriodIsThirtyInclusiveDays() {
        TransactionPeriod period = TransactionPeriod.lastThirtyDays(TODAY);

        assertThat(period.to()).isEqualTo(TODAY);
        assertThat(period.from()).isEqualTo(TODAY.minusDays(29));
        assertThat(period.days()).isEqualTo(30);
    }

    @Test
    @DisplayName("TC-TXN-054 — a period that ends before it starts is refused")
    void anInvertedPeriodIsRefused() {
        assertThatThrownBy(() -> new TransactionPeriod(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ends before it starts");
    }

    @Test
    @DisplayName("TC-TXN-055 — a single-day period is legitimate and one day long")
    void aSingleDayPeriodIsOneDay() {
        assertThat(new TransactionPeriod(TODAY, TODAY).days()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-TXN-056 — a period wider than the back office can answer in one call is refused")
    void aTooWidePeriodIsRefused() {
        // TechExcel's ledger endpoint has no pagination at all, so the window is the only bound on
        // response size. An unbounded response on a money path fails on the busiest account first.
        assertThatCode(() -> new TransactionPeriod(
                TODAY.minusDays(TransactionPeriod.MAX_DAYS), TODAY))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new TransactionPeriod(
                TODAY.minusDays(TransactionPeriod.MAX_DAYS + 1L), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    @DisplayName("TC-TXN-057 — either bound missing means the caller chose none, so the default applies")
    void eitherBoundMissingMeansTheDefault() {
        // Filling only the missing half would produce a window the caller never asked for and
        // cannot predict.
        TransactionPeriod expected = TransactionPeriod.lastThirtyDays(TODAY);

        assertThat(TransactionPeriod.orDefault(null, null, TODAY)).isEqualTo(expected);
        assertThat(TransactionPeriod.orDefault(TODAY.minusDays(5), null, TODAY)).isEqualTo(expected);
        assertThat(TransactionPeriod.orDefault(null, TODAY, TODAY)).isEqualTo(expected);
        assertThat(TransactionPeriod.orDefault(TODAY.minusDays(5), TODAY, TODAY))
                .isEqualTo(new TransactionPeriod(TODAY.minusDays(5), TODAY));
    }

    @Test
    @DisplayName("TC-TXN-058 — the widened period stays inside the maximum window")
    void theWidenedPeriodStaysInsideTheMaximum() {
        // Rule L7 offers a wider period; a wider period the gateway would refuse is not an offer.
        for (int days : new int[] {1, 7, 30, 45, TransactionPeriod.MAX_DAYS - 1}) {
            TransactionPeriod period = new TransactionPeriod(TODAY.minusDays(days - 1L), TODAY);
            TransactionPeriod widened = period.widened();

            assertThat(widened.days())
                    .as("widened from %d days", days)
                    .isGreaterThanOrEqualTo(period.days())
                    .isLessThanOrEqualTo((long) TransactionPeriod.MAX_DAYS + 1L);
            assertThat(widened.to()).isEqualTo(period.to());
        }
    }

    @Test
    @DisplayName("TC-TXN-059 — the maximum window matches the back office gateway's own bound")
    void theMaximumWindowMatchesTheGateway() {
        assertThat(TransactionPeriod.MAX_DAYS)
                .isEqualTo(com.thinq.fms.ledgerview.TechExcelWindow.MAX_WINDOW_DAYS);
    }

    @Test
    @DisplayName("TC-TXN-060 — both of Rule L5's views exist, and only those two")
    void bothViewsExistAndOnlyThose() {
        assertThat(TransactionView.values())
                .containsExactly(TransactionView.MOVEMENTS, TransactionView.ALL_ENTRIES);
    }

    // ------------------------------------------------------------------------------------ helpers

    private static LedgerEntry entry(Money debit, Money credit) {
        return new LedgerEntry(
                "V-1", "NSE_CASH", TODAY, debit, credit, Money.ofPaise(1_000L),
                "NARRATION", "R", null, null, null, false, null, null);
    }

    private static TransactionEntry transactionEntry() {
        return new TransactionEntry(
                "V-1", TODAY, EntryKind.PAYIN,
                new EntryDescriptionMapper.Description(
                        EntryKind.PAYIN, "ENTRY_PAYIN", Map.of(), "V-1", true),
                Money.ofPaise(1_000L), true, Money.ofPaise(1_000L), "NSE_CASH", null, null, null);
    }
}
