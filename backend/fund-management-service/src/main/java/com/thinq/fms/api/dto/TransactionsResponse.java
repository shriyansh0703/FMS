package com.thinq.fms.api.dto;

import com.thinq.fms.ledgerview.TransactionEntry;
import com.thinq.fms.ledgerview.TransactionPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A period of transactions (REQ-402, REQ-403, Rule L7).
 *
 * <p><b>The period is echoed back deliberately.</b> A client that asked for a default gets to
 * render what it actually received rather than what it guessed, and Rule L7 requires an empty
 * period to state which period was empty — "no transactions" alone is indistinguishable from a
 * failure to load.
 */
@Schema(description = "Transactions for a period, in one of Rule L5's two views.")
public record TransactionsResponse(
        @Schema(example = "MOVEMENTS", description = "MOVEMENTS or ALL_ENTRIES") String view,
        @Schema(description = "The period actually covered, echoed back.") PeriodDto period,
        List<EntryDto> entries,
        @Schema(description = "A wider period worth offering. Present only when entries is empty.")
        PeriodDto suggestedWiderPeriod) {

    @Schema(description = "An inclusive date range.")
    public record PeriodDto(LocalDate from, LocalDate to) {
        public static PeriodDto of(com.thinq.fms.ledgerview.TransactionPeriod p) {
            return p == null ? null : new PeriodDto(p.from(), p.to());
        }
    }

    /**
     * @param descriptionKey   a copy key. No English crosses this boundary, so wording changes
     *                         without a client release
     * @param secondaryDetail  the back-office reference. Shown beside the description, never as it
     *                         (Rule L3)
     * @param userCaused       Rule L4: visible without opening the entry, because a deposit the
     *                         trader made and an automatic return are not the same kind of event
     * @param runningBalance   TechExcel's, never accumulated here
     * @param reversedBy       set on an entry a later one reverses, so a reader scanning the list
     *                         does not count it twice (REQ-404)
     */
    @Schema(description = "One entry as the trader sees it.")
    public record EntryDto(
            @Schema(example = "VCH-4471") String reference,
            LocalDate date,
            @Schema(example = "PAYIN") String kind,
            @Schema(example = "ENTRY_PAYIN") String descriptionKey,
            Map<String, String> descriptionParameters,
            String secondaryDetail,
            MoneyDto amount,
            @Schema(description = "IN or OUT") String direction,
            MoneyDto runningBalance,
            @Schema(example = "NSE_CASH") String segment,
            boolean userCaused,
            String reversedBy,
            String reverses) {

        public static EntryDto of(TransactionEntry e) {
            return new EntryDto(
                    e.voucherNo(), e.date(), e.kind().name(),
                    e.description().copyKey(), e.description().parameters(),
                    e.description().secondaryDetail(),
                    MoneyDto.of(e.amount()), e.credit() ? "IN" : "OUT",
                    MoneyDto.of(e.runningBalance()), e.segment(),
                    e.userCaused(), e.reversedBy(), e.reverses());
        }
    }

    public static TransactionsResponse of(TransactionPage page) {
        return new TransactionsResponse(
                page.view().name(),
                PeriodDto.of(page.period()),
                page.entries().stream().map(EntryDto::of).toList(),
                PeriodDto.of(page.widerPeriod()));
    }
}
