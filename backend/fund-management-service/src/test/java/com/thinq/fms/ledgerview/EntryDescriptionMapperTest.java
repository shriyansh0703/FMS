package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-401, Rule L3 and Rule L4.
 *
 * <p>The PRD names illegible transaction history as one of four documented competitor defects, so
 * the two failure modes worth testing hardest are: a settlement identifier presented as though it
 * were a description, and a mandated return presented as something the trader asked for.
 */
class EntryDescriptionMapperTest {

    private final EntryDescriptionMapper mapper = ConfiguredEntryDescriptionMapper.withDefaults();

    @Test
    @DisplayName("a payout the trader requested is user-caused; a mandated return is not")
    void ruleL4SeparatesRequestedPayoutsFromMandatedReturns() {
        // Rule L4's whole point, and the reason userCaused belongs to the mapper: both of these
        // are TRANS_TYPE = P, and nothing but the mapping can tell them apart. Getting it wrong
        // tells a trader they asked for a return the calendar forced on them.
        var requested = mapper.describe(payment("Payout to bank", "UREF-9001"));
        var mandated = mapper.describe(payment("Quarterly settlement return", null));

        assertThat(requested.copyKey()).isEqualTo("ENTRY_PAYOUT");
        assertThat(requested.userCaused()).isTrue();

        assertThat(mandated.copyKey()).isEqualTo("ENTRY_MANDATED_RETURN");
        assertThat(mandated.userCaused()).isFalse();
    }

    @Test
    @DisplayName("a payout with no reference of ours is not attributed to the trader")
    void payoutWithoutOurReferenceIsNotUserCaused() {
        // USERREFNO is set by this system on every instruction it issues and by nothing else, so
        // its absence means no request of ours lies behind the entry.
        var o = mapper.describe(payment("Payout to bank", null));

        assertThat(o.copyKey()).isEqualTo("ENTRY_PAYOUT");
        assertThat(o.userCaused()).isFalse();
    }

    @Test
    @DisplayName("Rule L3: the reference is secondary detail, never the description")
    void referenceIsSecondaryDetailAndNeverTheDescription() {
        var o = mapper.describe(bill(true, "SETL-2026-0812"));

        assertThat(o.copyKey()).isEqualTo("ENTRY_SALE_PROCEEDS");
        assertThat(o.secondaryDetail()).isEqualTo("SETL-2026-0812");
        // The settlement identifier must not have become the description.
        assertThat(o.copyKey()).doesNotContain("SETL");
    }

    @Test
    @DisplayName("an unmapped entry says so explicitly and still shows its reference")
    void unmappedEntryIsExplicitRatherThanIllegible() {
        // Rule L3's edge case: the raw reference is shown WITH a statement that a plain
        // description is not available — never in place of one, and never as though the
        // reference were the description.
        var o = mapper.describe(new LedgerEntry("VCH-77", "NSE_CASH", LocalDate.of(2026, 8, 21),
                Money.ofPaise(100L), Money.ZERO, Money.ZERO, "something new", "ZZ",
                null, null, null, false, null, null));

        assertThat(o.isUnavailable()).isTrue();
        assertThat(o.copyKey()).isEqualTo(EntryDescriptionMapper.Description.UNAVAILABLE_KEY);
        assertThat(o.secondaryDetail()).isEqualTo("VCH-77");
        assertThat(o.userCaused()).isFalse();
    }

    @Test
    @DisplayName("unmapped combinations are counted so operations can extend the table")
    void unmappedCombinationsAreCounted() {
        // An entry type appearing in production that the table does not know is a requirement gap
        // a trader is looking at right now. It has to be visible to someone.
        var m = ConfiguredEntryDescriptionMapper.withDefaults();
        m.describe(new LedgerEntry("V1", null, LocalDate.of(2026, 8, 21), Money.ofPaise(1L),
                Money.ZERO, Money.ZERO, null, "ZZ", null, null, null, false, null, null));
        m.describe(new LedgerEntry("V2", null, LocalDate.of(2026, 8, 21), Money.ofPaise(1L),
                Money.ZERO, Money.ZERO, null, "ZZ", null, null, null, false, null, null));

        assertThat(m.unmappedCounts()).containsEntry("ZZ/DR", 2L);
    }

    @Test
    @DisplayName("Rule L5a: sale proceeds are not a payin")
    void saleProceedsAreNotAPayin() {
        // Money arriving from trading is not money the trader moved from their own bank.
        // Collapsing them would put trading outcomes in the "where is my money" view.
        var proceeds = mapper.describe(bill(true, "SETL-1"));
        var payin = mapper.describe(receipt());

        assertThat(proceeds.copyKey()).isEqualTo("ENTRY_SALE_PROCEEDS");
        assertThat(EntryKind.SALE_PROCEEDS.isMoneyMovement()).isFalse();

        assertThat(payin.copyKey()).isEqualTo("ENTRY_PAYIN");
        assertThat(payin.userCaused()).isTrue();
        assertThat(EntryKind.PAYIN.isMoneyMovement()).isTrue();
    }

    @Test
    @DisplayName("a reversal is classified as one rather than as an opposite payin")
    void reversalIsItsOwnKind() {
        // Rule L2: a correction is a compensating entry paired with its original, not a second
        // payin pointing the other way.
        var o = mapper.describe(new LedgerEntry("VCH-9", "NSE_CASH", LocalDate.of(2026, 8, 21),
                Money.ofPaise(50_000L), Money.ZERO, Money.ZERO, "Reversal of receipt VCH-4",
                "R", null, null, null, false, null, null));

        assertThat(o.copyKey()).isEqualTo("ENTRY_REVERSAL");
    }

    @Test
    @DisplayName("amounts reach the description already in paise")
    void amountsAreCarriedAsPaise() {
        // The interface promises paise, so no consumer of a description converts money — which is
        // what keeps the single conversion point single.
        var o = mapper.describe(receipt());

        assertThat(o.parameters()).containsEntry("amountPaise", "2500000");
        assertThat(o.parameters()).containsEntry("direction", "IN");
    }

    @Test
    @DisplayName("an opening balance is recognised before anything else")
    void openingBalanceWins() {
        var o = mapper.describe(new LedgerEntry("VCH-0", "NSE_CASH", LocalDate.of(2026, 4, 1),
                Money.ZERO, Money.ofPaise(10L), Money.ofPaise(10L), "Opening", "J",
                null, null, null, true, null, null));

        assertThat(o.copyKey()).isEqualTo("ENTRY_OPENING_BALANCE");
    }

    // ---- fixtures ----

    private static LedgerEntry receipt() {
        return new LedgerEntry("VCH-1", "NSE_CASH", LocalDate.of(2026, 8, 21),
                Money.ZERO, Money.ofPaise(2_500_000L), Money.ofPaise(2_500_000L),
                "Fund transfer received", "R", null, null, null, false, null, "GW-77");
    }

    private static LedgerEntry payment(String narration, String userRefNo) {
        return new LedgerEntry("VCH-2", "NSE_CASH", LocalDate.of(2026, 8, 21),
                Money.ofPaise(1_000_000L), Money.ZERO, Money.ZERO,
                narration, "P", null, null, null, false, userRefNo, null);
    }

    private static LedgerEntry bill(boolean credit, String settlementNo) {
        return new LedgerEntry("VCH-3", "NSE_CASH", LocalDate.of(2026, 8, 21),
                credit ? Money.ZERO : Money.ofPaise(700_000L),
                credit ? Money.ofPaise(700_000L) : Money.ZERO,
                Money.ZERO, "Contract note", "J", settlementNo,
                LocalDate.of(2026, 8, 22), "M-T+1 Normal", false, null, null);
    }
}
