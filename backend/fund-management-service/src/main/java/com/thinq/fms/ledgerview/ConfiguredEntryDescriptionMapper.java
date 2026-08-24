package com.thinq.fms.ledgerview;

import com.thinq.fms.platform.money.Money;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The configuration-driven implementation of {@link EntryDescriptionMapper}.
 *
 * <h2>How an entry is classified</h2>
 *
 * <p>TechExcel gives four usable signals and no explicit type: {@code TRANS_TYPE} (R, P, J, SJ),
 * {@code voctype} (the margin credit/debit flag), {@code OPENINGBALANCE}, and — for a transaction
 * bill — {@code SETL_PAYINDATE} and {@code MKT_TYPE}. Classification reads them in a fixed order,
 * most specific first, because several combinations overlap.
 *
 * <p>The narration is used <b>only</b> to separate cases the structured fields cannot: a payout the
 * holder requested from a mandated settlement return, since both are {@code TRANS_TYPE = P}. It is
 * never used as the description. Rule L3.
 *
 * <h2>Unmapped entries are counted, not swallowed</h2>
 *
 * <p>Every combination the table does not know increments a counter and is retained for alerting.
 * An entry type appearing in production that this table does not know about is a requirement gap a
 * user is currently looking at, so it must be visible to operations rather than only to the person
 * who happens to read the code.
 */
public final class ConfiguredEntryDescriptionMapper implements EntryDescriptionMapper {

    /** Copy keys, one per kind. The client resolves wording from these; no English lives here. */
    private static final Map<EntryKind, String> COPY_KEYS = Map.of(
            EntryKind.PAYIN, "ENTRY_PAYIN",
            EntryKind.PAYOUT, "ENTRY_PAYOUT",
            EntryKind.MANDATED_RETURN, "ENTRY_MANDATED_RETURN",
            EntryKind.SALE_PROCEEDS, "ENTRY_SALE_PROCEEDS",
            EntryKind.PURCHASE_COST, "ENTRY_PURCHASE_COST",
            EntryKind.CHARGES, "ENTRY_CHARGES",
            EntryKind.MARGIN_MOVEMENT, "ENTRY_MARGIN_MOVEMENT",
            EntryKind.ACCOUNT_ACCRUAL, "ENTRY_ACCOUNT_ACCRUAL",
            EntryKind.OPENING_BALANCE, "ENTRY_OPENING_BALANCE",
            EntryKind.REVERSAL, "ENTRY_REVERSAL");

    /**
     * Narration phrases that identify a mandated return, lowercase.
     *
     * <p>Configuration rather than constants for the same reason
     * {@code SettlementReasonMapper}'s table is: the back office's wording changes without notice,
     * and getting this wrong tells a trader they asked for a return the calendar forced on them.
     */
    private final Set<String> mandatedReturnPhrases;

    /** Narration phrases that identify a reversal. */
    private final Set<String> reversalPhrases;

    private final Map<String, AtomicLong> unmappedCounts = new ConcurrentHashMap<>();

    public ConfiguredEntryDescriptionMapper(Set<String> mandatedReturnPhrases, Set<String> reversalPhrases) {
        this.mandatedReturnPhrases = Set.copyOf(Objects.requireNonNull(mandatedReturnPhrases, "mandatedReturnPhrases"));
        this.reversalPhrases = Set.copyOf(Objects.requireNonNull(reversalPhrases, "reversalPhrases"));
    }

    public static ConfiguredEntryDescriptionMapper withDefaults() {
        return new ConfiguredEntryDescriptionMapper(
                new LinkedHashSet<>(Set.of("settlement return", "mandated", "quarterly settlement",
                        "running account settlement")),
                new LinkedHashSet<>(Set.of("reversal", "reversed", "cancelled", "chargeback")));
    }

    @Override
    public Description describe(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry");

        EntryKind kind = classify(entry);
        if (kind == null) {
            countUnmapped(entry);
            return Description.unavailable(secondaryDetail(entry));
        }
        return new Description(
                kind,
                COPY_KEYS.get(kind),
                parameters(entry, kind),
                secondaryDetail(entry),
                isUserCaused(entry, kind));
    }

    /**
     * Which kind of event this entry is, or null when the signals do not identify one.
     *
     * <p>Order is deliberate and each step is more specific than the next.
     */
    private EntryKind classify(LedgerEntry entry) {
        if (entry.openingBalance()) {
            return EntryKind.OPENING_BALANCE;
        }
        String narration = lower(entry.narration());
        if (matchesAny(narration, this.reversalPhrases)) {
            // Checked before everything else that follows, because a reversal of a payin is a
            // reversal first — Rule L2 pairs it with its original rather than presenting it as a
            // second, opposite payin.
            return EntryKind.REVERSAL;
        }
        // A transaction bill: SETL_PAYINDATE is populated only on one, and its direction says
        // whether the holder bought or sold.
        if (entry.isTransactionBill()) {
            return entry.isCredit() ? EntryKind.SALE_PROCEEDS : EntryKind.PURCHASE_COST;
        }

        String transType = upper(entry.transType());
        if (transType == null) {
            return null;
        }
        return switch (transType) {
            // A receipt is money in. Without a settlement date behind it, it came from the
            // holder's own bank.
            case "R" -> EntryKind.PAYIN;
            // The Rule L4 case: both are payments out, and only the narration separates them.
            case "P" -> matchesAny(narration, this.mandatedReturnPhrases)
                    ? EntryKind.MANDATED_RETURN
                    : EntryKind.PAYOUT;
            // Journals carry charges, accruals and margin movements. voctype flags the last.
            case "J", "SJ" -> journalKind(entry);
            default -> null;
        };
    }

    /**
     * A journal is a charge, an accrual or a margin movement.
     *
     * <p>A system journal (SJ) that debits is a charge; one that credits is an accrual such as
     * interest. A margin movement is identified by the caller having set the margin flag, which
     * this system reads from {@code voctype} at the gateway.
     */
    private static EntryKind journalKind(LedgerEntry entry) {
        String narration = lower(entry.narration());
        if (narration != null && (narration.contains("margin") || narration.contains("span")
                || narration.contains("exposure"))) {
            return EntryKind.MARGIN_MOVEMENT;
        }
        return entry.isCredit() ? EntryKind.ACCOUNT_ACCRUAL : EntryKind.CHARGES;
    }

    /**
     * Rule L4: did the account holder cause this?
     *
     * <p>{@link EntryKind#PAYIN} always. {@link EntryKind#PAYOUT} only when a request of ours lies
     * behind it — which {@code USERREFNO} evidences, since this system sets that field on every
     * instruction it issues and nothing else does. A mandated return has no request behind it and
     * is never user-caused, whatever its transaction type says.
     */
    private static boolean isUserCaused(LedgerEntry entry, EntryKind kind) {
        if (kind.isAlwaysUserCaused()) {
            return true;
        }
        if (kind == EntryKind.PAYOUT) {
            return entry.userRefNo() != null && !entry.userRefNo().isBlank();
        }
        return false;
    }

    private static Map<String, String> parameters(LedgerEntry entry, EntryKind kind) {
        Map<String, String> p = new LinkedHashMap<>();
        // Paise, as the interface promises, so no consumer of a description converts money.
        Money amount = entry.isCredit() ? entry.credit() : entry.debit();
        p.put("amountPaise", Long.toString(amount.paise()));
        p.put("direction", entry.isCredit() ? "IN" : "OUT");
        if (entry.segment() != null) {
            p.put("segment", entry.segment());
        }
        if (kind == EntryKind.SALE_PROCEEDS || kind == EntryKind.PURCHASE_COST) {
            if (entry.settlementPayinDate() != null) {
                p.put("settlementDate", entry.settlementPayinDate().toString());
            }
            if (entry.marketType() != null) {
                p.put("marketType", entry.marketType());
            }
        }
        return p;
    }

    /**
     * The back-office reference, most specific first.
     *
     * <p>Shown as secondary detail and never as the description. This is what Rule L3 means by
     * retaining settlement identifiers rather than displaying them in place of language.
     */
    private static String secondaryDetail(LedgerEntry entry) {
        if (entry.settlementNo() != null && !entry.settlementNo().isBlank()) {
            return entry.settlementNo();
        }
        return entry.voucherNo();
    }

    private void countUnmapped(LedgerEntry entry) {
        String signature = upper(entry.transType()) + "/" + (entry.isCredit() ? "CR" : "DR");
        this.unmappedCounts.computeIfAbsent(signature, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Unmapped combinations seen, by signature, for the alert that gets the table extended.
     *
     * <p>Exposed rather than logged so a metrics binding can publish it as a gauge. A log line per
     * unmapped entry would be one line per user viewing the same broken entry type.
     */
    public Map<String, Long> unmappedCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        this.unmappedCounts.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }

    private static boolean matchesAny(String narration, Set<String> phrases) {
        if (narration == null) {
            return false;
        }
        return phrases.stream().anyMatch(narration::contains);
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }
}
