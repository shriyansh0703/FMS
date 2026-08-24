package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.AccountRef;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The transaction list (REQ-401 to REQ-404, REQ-407).
 *
 * <h2>What this reads through rather than stores</h2>
 *
 * <p>Entries come from TechExcel on every request. FMS holds no copy, and — the decision that
 * matters most — <b>never accumulates a running balance</b>. TechExcel's {@code CLOSING_AMT}
 * carries it per entry (HLD §9.1b). Two systems computing one running balance is Rule B12's
 * failure mode, and the second one is always the one that is wrong.
 *
 * <h2>Reversal pairing</h2>
 *
 * <p>Rule L2 keeps both entries: a correction is a compensating entry, never a deletion. REQ-404
 * requires them paired, and the original flagged as reversed so a reader scanning the list does not
 * count it twice. Pairing is done here rather than by the client because the link lives in the
 * narration, which the client never sees.
 */
public final class TransactionQueryService {

    /**
     * A voucher reference inside a reversal's narration, e.g. "Reversal of receipt VCH-4471".
     *
     * <p>Text-matching is not a contract and this is the weakest link in the pairing. TechExcel
     * offers no structured "reverses" field — verified against the Ledger output parameters on
     * 21 Aug 2026 — so the alternative is not pairing at all.
     *
     * <p><b>The failure modes are asymmetric, and the pattern is built around that.</b> A missed
     * match costs nothing: the reversal is still returned as its own entry and Rule L8 keeps it in
     * the history either way. A <i>wrong</i> match flags an unrelated entry as reversed, and a
     * reader scanning the list discounts money that is still theirs — REQ-404's failure mode
     * reached from the opposite direction.
     *
     * <p>An earlier version claimed that guard and did not have it. It applied
     * {@code CASE_INSENSITIVE} to the whole pattern, which made {@code [A-Z0-9]} match lowercase
     * and reduced "must look like a reference" to "must be a word" — it extracted
     * {@code 'quarter'} from "Reversal of brokerage for the quarter" and {@code 'balance'} from
     * "Cancelled payout for insufficient balance". With a voucher that happened to be an English
     * word, that produced a genuine false pair.
     *
     * <p>Three conditions close it, and each rejects something the others do not:
     *
     * <ul>
     *   <li>The anchor words are case-insensitive through an inline flag; the reference group is
     *       <b>not</b>, so it must be genuinely upper-case. This rejects {@code 4a} and
     *       {@code 12b} — lowercase tokens that happen to carry a digit.
     *   <li>It must <b>start with a letter</b>. This rejects a bare number picked out of prose,
     *       such as the {@code 12} in "for slot 12b" — an earlier version extracted exactly that.
     *   <li>It must contain a digit or a hyphen. This rejects {@code MARCH}, {@code NSE} and
     *       {@code quarter} — every real voucher has one and an English word does not.
     * </ul>
     */
    private static final Pattern REVERSED_VOUCHER = Pattern.compile(
            "(?i:\\b(?:of|for|against))\\s+(?:[a-zA-Z ]+\\s+)?"
                    + "([A-Z][A-Z0-9/-]*[0-9-][A-Z0-9/-]*)");

    private final LedgerEntrySource ledger;
    private final InFlightMovementSource inFlight;
    private final EntryDescriptionMapper descriptions;
    private final StatementCopy statementCopy;
    private final Clock clock;

    public TransactionQueryService(LedgerEntrySource ledger,
                                   InFlightMovementSource inFlight,
                                   EntryDescriptionMapper descriptions,
                                   StatementCopy statementCopy,
                                   Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.inFlight = Objects.requireNonNull(inFlight, "inFlight");
        this.descriptions = Objects.requireNonNull(descriptions, "descriptions");
        this.statementCopy = Objects.requireNonNull(statementCopy, "statementCopy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * One period of entries in one view.
     *
     * <p>Both views are built from the same ledger read and the same running balance, so switching
     * between them cannot show a different balance for the same entry — which is what Rule L5's
     * "reachable from the other without losing the period" would otherwise expose.
     */
    public TransactionPage list(AccountRef account, TransactionView view, TransactionPeriod period) {
        Objects.requireNonNull(account, "account");
        return listFrom(this.ledger.read(account, period.from(), period.to()),
                this.inFlight.read(account, period.from(), period.to()), view, period);
    }

    /**
     * The whole of the list logic, over entries already read.
     *
     * <p>Separated from the source call so that describing, pairing, filtering and Rule L7's empty
     * handling are exercised as written, rather than reimplemented in a test double.
     */
    private TransactionPage listFrom(List<LedgerEntry> raw, List<TransactionEntry> unposted,
                                     TransactionView view, TransactionPeriod period) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(period, "period");

        List<TransactionEntry> all = describe(raw);
        pairReversals(all);

        // Unposted movements join AFTER pairing. They have no ledger voucher for a reversal to
        // name, and including them in the pairing pass would let a reversal's narration match an
        // attempt reference by coincidence.
        all.addAll(unposted);

        List<TransactionEntry> inView = all.stream()
                .filter(e -> view.includes(e.kind()))
                // Newest first: the entry a trader is looking for is almost always a recent one.
                .sorted(Comparator.comparing(TransactionEntry::date).reversed()
                        .thenComparing(TransactionEntry::voucherNo, Comparator.reverseOrder()))
                .toList();

        // Rule L7: an empty period says it is empty and offers a wider one.
        return new TransactionPage(view, period, inView,
                inView.isEmpty() ? period.widened() : null);
    }

    /** One entry by its voucher, scoped to the account that owns it. */
    public Optional<TransactionEntry> detail(AccountRef account, TransactionPeriod period, String voucherNo) {
        Objects.requireNonNull(voucherNo, "voucherNo");

        // ALL_ENTRIES, because a detail lookup must find an entry the MOVEMENTS view filters out.
        return list(account, TransactionView.ALL_ENTRIES, period).entries().stream()
                .filter(e -> voucherNo.equals(e.voucherNo()))
                .findFirst();
    }

    /** The rows a statement export renders, in the same view and period as the list (Rule L8a). */
    public List<StatementRow> statementRows(AccountRef account, TransactionView view, TransactionPeriod period) {
        return list(account, view, period).entries().stream()
                // Language, not the copy key. REQ-407 requires a plain-language description, and
                // this is a file the server writes — the client is not there to resolve a key.
                .map(e -> new StatementRow(e.date(), this.statementCopy.describe(e.description()),
                        e.credit() ? StatementRow.CREDIT : StatementRow.DEBIT,
                        e.voucherNo(), e.amount(), e.runningBalance()))
                .toList();
    }

    public TransactionPeriod defaultPeriod() {
        return TransactionPeriod.lastThirtyDays(LocalDate.now(this.clock));
    }

    public LocalDate today() {
        return LocalDate.now(this.clock);
    }

    private List<TransactionEntry> describe(List<LedgerEntry> raw) {
        List<TransactionEntry> out = new ArrayList<>(raw.size());
        for (LedgerEntry entry : raw) {
            EntryDescriptionMapper.Description description = this.descriptions.describe(entry);
            out.add(TransactionEntry.posted(
                    entry.voucherNo(),
                    entry.voucherDate(),
                    description.kind(),
                    description,
                    entry.isCredit() ? entry.credit() : entry.debit(),
                    entry.isCredit(),
                    entry.closingBalance(),
                    entry.segment(),
                    null,
                    reversedVoucher(entry)));
        }
        return out;
    }

    /**
     * Link each reversal to the entry it reverses, and flag that entry.
     *
     * <p>Mutates by replacing list elements rather than rebuilding, because the flag has to be set
     * on an entry that was already constructed and records are immutable.
     */
    private static void pairReversals(List<TransactionEntry> entries) {
        Map<String, Integer> byVoucher = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            byVoucher.put(entries.get(i).voucherNo(), i);
        }

        for (TransactionEntry entry : List.copyOf(entries)) {
            if (entry.reverses() == null) {
                continue;
            }
            if (entry.reverses().equals(entry.voucherNo())) {
                // An entry naming its own voucher. Pairing it with itself would report an entry as
                // reversing and reversed by itself, which renders as nonsense and would let a
                // reader discount it.
                continue;
            }
            Integer originalIndex = byVoucher.get(entry.reverses());
            if (originalIndex == null) {
                // The original is outside this period. The reversal still stands on its own —
                // Rule L8 keeps it in the history either way.
                continue;
            }
            TransactionEntry original = entries.get(originalIndex);
            entries.set(originalIndex, new TransactionEntry(
                    original.voucherNo(), original.date(), original.kind(), original.description(),
                    original.amount(), original.credit(), original.runningBalance(),
                    original.segment(), entry.voucherNo(), original.reverses(), original.status()));
        }
    }

    /** The voucher a reversal names in its narration, or null when this is not a reversal. */
    private static String reversedVoucher(LedgerEntry entry) {
        String narration = entry.narration();
        if (narration == null) {
            return null;
        }
        String lower = narration.toLowerCase(Locale.ROOT);
        if (!lower.contains("revers") && !lower.contains("cancel") && !lower.contains("chargeback")) {
            return null;
        }
        Matcher m = REVERSED_VOUCHER.matcher(narration);
        return m.find() ? m.group(1) : null;
    }

}
