package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rules L5, L5a, L6, L7, L8 and REQ-404's reversal pairing.
 *
 * <p>The two easiest things to get wrong here are both about what a view <i>excludes</i>: putting
 * trading outcomes in the money-in-and-out view, and letting a reversed entry be counted twice.
 * Both are silent — the list renders, the numbers are individually right, and the answer is wrong.
 */
class TransactionQueryServiceTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneOffset.UTC);

    private final List<LedgerEntry> ledger = new ArrayList<>();
    private final List<TransactionEntry> inFlight = new ArrayList<>();
    private TransactionQueryService query;

    @BeforeEach
    void setUp() {
        this.ledger.clear();
        this.inFlight.clear();
        this.query = new TransactionQueryService(
                (account, from, to) -> List.copyOf(this.ledger),
                (account, from, to) -> List.copyOf(this.inFlight),
                ConfiguredEntryDescriptionMapper.withDefaults(), StatementCopy.withDefaults(), CLOCK);
    }

    @Test
    @DisplayName("Rule L5a: the movements view excludes sale proceeds and charges")
    void movementsViewExcludesTradingOutcomes() {
        this.ledger.add(payin("V1", 500_000L));
        this.ledger.add(saleProceeds("V2", 700_000L));
        this.ledger.add(charges("V3", 5_000L));
        this.ledger.add(payout("V4", 100_000L));

        var movements = this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period());
        var everything = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period());

        // A payin is money the trader moved from their own bank. Sale proceeds are not, and
        // including them answers a question nobody asked while hiding the one they did.
        assertThat(movements.entries()).extracting(TransactionEntry::voucherNo)
                .containsExactlyInAnyOrder("V1", "V4");
        assertThat(everything.entries()).hasSize(4);
    }

    @Test
    @DisplayName("REQ-402 and Rule L8: in-flight and failed payins appear in the movements view")
    void inFlightAndFailedMovementsAppear() {
        // The gap this closes. The view was built from ledger entries only, and a failed payin
        // never reaches the ledger because no money moved — so the entry a trader "most often
        // needs to discuss" (Rule L8) was the one entry the list could not show.
        this.ledger.add(payin("V1", 500_000L));
        this.inFlight.add(attemptEntry("PAYIN-7", "AT_GATEWAY", 250_000L));
        this.inFlight.add(attemptEntry("PAYIN-8", "FAILED", 100_000L));

        var entries = this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period()).entries();

        assertThat(entries).extracting(TransactionEntry::voucherNo)
                .containsExactlyInAnyOrder("V1", "PAYIN-7", "PAYIN-8");
        assertThat(byVoucher(entries, "PAYIN-7").isInFlight())
                .as("REQ-402: an item not yet complete is shown as such").isTrue();
        assertThat(byVoucher(entries, "PAYIN-8").isInFlight())
                .as("a failed attempt is finished, not in flight").isFalse();
        // A posted ledger entry has already happened and has no status to report.
        assertThat(byVoucher(entries, "V1").statusIfAny()).isEmpty();
    }

    @Test
    @DisplayName("an unposted movement carries no running balance, because it moved none")
    void unpostedMovementsCarryNoBalance() {
        this.inFlight.add(attemptEntry("PAYIN-7", "AWAITING_BANK", 250_000L));

        assertThat(this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period())
                .entries().get(0).runningBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("both views report the same running balance for the same entry")
    void bothViewsAgreeOnTheRunningBalance() {
        // REQ-402 requires each view reachable from the other without losing the period. If the
        // two disagreed on a balance, switching would look like the account had changed.
        this.ledger.add(payin("V1", 500_000L));
        this.ledger.add(saleProceeds("V2", 700_000L));

        Money inMovements = this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period())
                .entries().get(0).runningBalance();
        Money inAll = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period())
                .entries().stream().filter(e -> e.voucherNo().equals("V1"))
                .findFirst().orElseThrow().runningBalance();

        assertThat(inMovements).isEqualTo(inAll);
    }

    @Test
    @DisplayName("the running balance is TechExcel's, never accumulated here")
    void runningBalanceComesFromTheBackOffice() {
        // HLD §9.1b. Two systems computing one running balance is Rule B12's failure mode, and
        // the second one is always the one that is wrong.
        this.ledger.add(entry("V1", "R", 0L, 100_000L, 999_999L, "Fund transfer received", null, null));

        assertThat(this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period())
                .entries().get(0).runningBalance().paise())
                .as("the closing amount is carried through, not recomputed from the entries")
                .isEqualTo(999_999L);
    }

    @Test
    @DisplayName("REQ-404: a reversal is paired with its original, and the original is flagged")
    void reversalIsPairedAndOriginalFlagged() {
        this.ledger.add(payin("V1", 500_000L));
        this.ledger.add(entry("V2", "R", 500_000L, 0L, 0L, "Reversal of receipt V1", null, null));

        var entries = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries();
        var original = byVoucher(entries, "V1");
        var reversal = byVoucher(entries, "V2");

        // Rule L2 keeps both. REQ-404 needs the original flagged so a reader scanning the list
        // does not count it twice — which is exactly what happens without the flag.
        assertThat(original.isReversed()).isTrue();
        assertThat(original.reversedBy()).isEqualTo("V2");
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.reverses()).isEqualTo("V1");
    }

    @Test
    @DisplayName("a reversal whose original is outside the period still stands on its own")
    void unmatchedReversalIsStillReturned() {
        // Rule L8 keeps failed and cancelled movements in the history. Dropping a reversal because
        // its original is not in this window would remove a real event from the record.
        this.ledger.add(entry("V9", "R", 500_000L, 0L, 0L, "Reversal of receipt V-EARLIER", null, null));

        var entries = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).reverses()).isEqualTo("V-EARLIER");
        assertThat(entries.get(0).isReversed()).isFalse();
    }

    @Test
    @DisplayName("an English word in a narration never becomes a reference")
    void ordinaryWordsAreNotTreatedAsReferences() {
        // The pairing reads a voucher out of free text because TechExcel has no structured
        // "reverses" field. An earlier pattern applied CASE_INSENSITIVE to the whole expression,
        // which made [A-Z0-9] match lowercase and reduced "must look like a reference" to "must be
        // a word" — it extracted 'quarter', 'balance' and 'transaction'.
        for (String narration : new String[]{
                "Reversal of brokerage for the quarter",
                "Cancelled payout for insufficient balance",
                "Chargeback for transaction",
                "Reversal of charges for MARCH",
                "Cancelled order for NSE",
                "Reversal of entry"}) {
            this.ledger.clear();
            this.ledger.add(entry("VCH-9", "J", 5_000L, 0L, 0L, narration, null, null));

            var e = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries().get(0);
            assertThat(e.reverses())
                    .as("\"%s\" must yield no reference", narration)
                    .isNull();
        }
    }

    @Test
    @DisplayName("a lowercase token containing a digit is not a reference either")
    void lowercaseTokensWithDigitsAreNotReferences() {
        // Two guards stand between free text and a false reference: the candidate must contain a
        // digit or hyphen, and it must be genuinely upper-case. The digit rule alone rejects every
        // plain English word, so it does all the visible work — this is the case that separates
        // the two, and without it the case-sensitivity change would be untested and get "tidied
        // away" as redundant.
        for (String narration : new String[]{
                "Reversal of txn 4a",
                "Cancelled order for slot 12b",
                "Reversal of batch 2026x",
                // A lowercase token that has both a leading letter and a digit — the only shape
                // the case-sensitivity guard catches on its own. Matching it case-insensitively
                // would gain nothing anyway: the extracted "txn-4471" is compared to voucher
                // numbers by exact string, so it could never pair with "VCH-4471" regardless.
                "Reversal of txn-4471",
                "Cancelled payout for ref-12"}) {
            this.ledger.clear();
            this.ledger.add(entry("VCH-9", "J", 100L, 0L, 0L, narration, null, null));

            assertThat(this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period())
                    .entries().get(0).reverses())
                    .as("\"%s\" must yield no reference", narration)
                    .isNull();
        }
    }

    @Test
    @DisplayName("a false pair cannot flag an unrelated entry as reversed")
    void aFalsePairCannotFlagAnUnrelatedEntry() {
        // The consequence that makes this worth guarding: a reader scanning the list discounts an
        // entry that was never reversed — money that is still theirs, presented as cancelled.
        // Reachable when a voucher happens to be an English word.
        this.ledger.add(entry("MARCH", "J", 5_000L, 0L, 0L,
                "Brokerage and statutory charges", null, null));
        this.ledger.add(entry("VCH-9", "J", 5_000L, 0L, 0L,
                "Reversal of charges for MARCH", null, null));

        var entries = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries();

        assertThat(byVoucher(entries, "MARCH").isReversed())
                .as("an unrelated charge must not be flagged as reversed")
                .isFalse();
        assertThat(byVoucher(entries, "VCH-9").reverses()).isNull();
    }

    @Test
    @DisplayName("real references still pair, including hyphenated settlement numbers")
    void realReferencesStillPair() {
        // The other half: tightening the pattern must not stop it doing its job.
        this.ledger.add(entry("SETL-2026-0812", "R", 0L, 700_000L, 700_000L,
                "Fund transfer received", null, null));
        this.ledger.add(entry("VCH-9", "R", 700_000L, 0L, 0L,
                "Reversal of SETL-2026-0812", null, null));

        var entries = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries();

        assertThat(byVoucher(entries, "SETL-2026-0812").reversedBy()).isEqualTo("VCH-9");
        assertThat(byVoucher(entries, "VCH-9").reverses()).isEqualTo("SETL-2026-0812");
    }

    @Test
    @DisplayName("an entry naming its own voucher is not paired with itself")
    void selfReferenceIsNotPaired() {
        // Reachable, and it rendered as an entry that both reverses and is reversed by itself.
        this.ledger.add(entry("VCH-1", "R", 0L, 100L, 100L, "Reversal of receipt VCH-1", null, null));

        var e = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries().get(0);

        assertThat(e.reversedBy()).as("nothing may reverse itself").isNull();
        assertThat(e.isReversed()).isFalse();
    }

    @Test
    @DisplayName("Rule L7: an empty period says so and offers a wider one")
    void emptyPeriodOffersAWiderOne() {
        var page = this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period());

        // Blank space is indistinguishable from a failure to load, and a trader who cannot tell
        // them apart assumes the second.
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.period()).isEqualTo(period());
        assertThat(page.widerPeriodIfEmpty()).isPresent();
        assertThat(page.widerPeriodIfEmpty().orElseThrow().days())
                .isGreaterThan(page.period().days());
    }

    @Test
    @DisplayName("a non-empty period offers no wider one")
    void nonEmptyPeriodOffersNothing() {
        this.ledger.add(payin("V1", 500_000L));

        assertThat(this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period())
                .widerPeriodIfEmpty()).isEmpty();
    }

    @Test
    @DisplayName("Rule L6: the default period is thirty days, not seven")
    void defaultPeriodIsThirtyDays() {
        // The mandated return of unused funds runs monthly or quarterly and is among the
        // most-queried entries, so a seven-day default shows an empty table for a transaction the
        // trader knows happened.
        TransactionPeriod p = TransactionPeriod.lastThirtyDays(TODAY);

        assertThat(p.days()).isEqualTo(30);
        assertThat(p.to()).isEqualTo(TODAY);
        assertThat(TransactionPeriod.orDefault(null, null, TODAY)).isEqualTo(p);
        // Only one bound supplied is not a period the caller chose.
        assertThat(TransactionPeriod.orDefault(TODAY.minusDays(3), null, TODAY)).isEqualTo(p);
    }

    @Test
    @DisplayName("a period wider than the ledger can answer is refused, not truncated")
    void tooWideAPeriodIsRefused() {
        // TechExcel's Ledger has no pagination, so the window is the only bound on response size.
        // Truncating would silently drop a trader's transactions.
        assertThatThrownBy(() -> new TransactionPeriod(TODAY.minusDays(400), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pagination");

        assertThatThrownBy(() -> new TransactionPeriod(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ends before it starts");
    }

    @Test
    @DisplayName("the window bound matches the gateway's, so neither can drift")
    void windowBoundMatchesTheGateway() {
        assertThat(TechExcelWindow.MAX_WINDOW_DAYS)
                .as("TransactionPeriod and TechExcelLedgerGateway must agree on the ledger window")
                .isEqualTo(com.thinq.fms.integration.techexcel.TechExcelLedgerGateway.MAX_WINDOW_DAYS);
    }

    @Test
    @DisplayName("entries come back newest first")
    void newestFirst() {
        this.ledger.add(entry("V1", "R", 0L, 100L, 100L, "Fund transfer received",
                LocalDate.of(2026, 8, 1), null));
        this.ledger.add(entry("V2", "R", 0L, 200L, 300L, "Fund transfer received",
                LocalDate.of(2026, 8, 20), null));

        assertThat(this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period()).entries())
                .extracting(TransactionEntry::voucherNo).containsExactly("V2", "V1");
    }

    @Test
    @DisplayName("detail finds an entry the movements view filters out")
    void detailReachesAnEntryOutsideTheMovementsView() {
        this.ledger.add(saleProceeds("V2", 700_000L));

        assertThat(this.query.list(ACCOUNT, TransactionView.MOVEMENTS, period()).entries()).isEmpty();
        assertThat(this.query.detail(ACCOUNT, period(), "V2")).isPresent();
        assertThat(this.query.detail(ACCOUNT, period(), "NOPE")).isEmpty();
    }

    @Test
    @DisplayName("statement rows carry the same view, period and balance as the list (Rule L8a)")
    void statementRowsMatchTheList() {
        this.ledger.add(payin("V1", 500_000L));
        this.ledger.add(saleProceeds("V2", 700_000L));

        var listed = this.query.list(ACCOUNT, TransactionView.ALL_ENTRIES, period()).entries();
        var rows = this.query.statementRows(ACCOUNT, TransactionView.ALL_ENTRIES, period());

        assertThat(rows).hasSameSizeAs(listed);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).resultingBalance()).isEqualTo(listed.get(i).runningBalance());
            assertThat(rows.get(i).debitOrCredit())
                    .isEqualTo(listed.get(i).credit() ? StatementRow.CREDIT : StatementRow.DEBIT);
        }
    }

    // ---- harness ----

    private static TransactionPeriod period() {
        return TransactionPeriod.lastThirtyDays(TODAY);
    }

    private static TransactionEntry byVoucher(List<TransactionEntry> entries, String voucher) {
        return entries.stream().filter(e -> e.voucherNo().equals(voucher)).findFirst()
                .orElseThrow(() -> new AssertionError("entry absent: " + voucher));
    }

    /** A movement the ledger does not carry, as PayinMovementSource would produce it. */
    private static TransactionEntry attemptEntry(String reference, String status, long paise) {
        return new TransactionEntry(reference, TODAY.minusDays(1), EntryKind.PAYIN,
                new EntryDescriptionMapper.Description(EntryKind.PAYIN, "PAYIN_" + status,
                        java.util.Map.of(), reference, true),
                Money.ofPaise(paise), true, Money.ZERO, null, null, null, status);
    }

    private static LedgerEntry payin(String v, long paise) {
        return entry(v, "R", 0L, paise, paise, "Fund transfer received", null, null);
    }

    private static LedgerEntry payout(String v, long paise) {
        return entry(v, "P", paise, 0L, 0L, "Payout to bank", null, "UREF-1");
    }

    private static LedgerEntry saleProceeds(String v, long paise) {
        return new LedgerEntry(v, "NSE_CASH", TODAY.minusDays(2), Money.ZERO, Money.ofPaise(paise),
                Money.ofPaise(paise), "Contract note", "J", "SETL-1",
                TODAY.plusDays(1), "M-T+1 Normal", false, null, null);
    }

    private static LedgerEntry charges(String v, long paise) {
        return entry(v, "J", paise, 0L, 0L, "Brokerage and statutory charges", null, null);
    }

    private static LedgerEntry entry(String voucher, String transType, long debit, long credit,
                                     long closing, String narration, LocalDate date, String userRef) {
        return new LedgerEntry(voucher, "NSE_CASH", date == null ? TODAY.minusDays(1) : date,
                Money.ofPaise(debit), Money.ofPaise(credit), Money.ofPaise(closing),
                narration, transType, null, null, null, false, userRef, null);
    }

}
