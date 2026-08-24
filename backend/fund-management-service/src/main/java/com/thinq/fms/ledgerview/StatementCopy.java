package com.thinq.fms.ledgerview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Plain-language wording for the statement export.
 *
 * <h2>Why this exists, given that no English lives in the mapper</h2>
 *
 * <p>{@link EntryDescriptionMapper} deliberately produces copy <i>keys</i>: the client resolves
 * wording from them, so copy changes without a client release and no English is baked into the
 * domain. That works for a screen the client renders.
 *
 * <p>It does not work for a file the <b>server</b> streams. REQ-407 requires the statement to carry
 * "its plain-language description", and the server cannot ask the client to resolve a key into a
 * CSV cell it is writing. Shipping the key instead put {@code ENTRY_CHARGES} in the one artifact a
 * trader keeps, saves and gives to someone else — which is exactly the illegible-history defect
 * Rule L3 and the PRD's competitor analysis exist to prevent.
 *
 * <h2>This is a second copy source, and that is a real cost</h2>
 *
 * <p>The client holds one set of wording and this holds another, and they can drift. Two things
 * bound the damage: the set is small and closed — one entry per {@link EntryKind} — and
 * {@code StatementCopyTest} fails the build if any kind lacks wording, so a new kind cannot ship as
 * a bare key. Drift in tone is possible; drift into a machine key is not.
 *
 * <p>The alternative was putting English on {@code Description}, which would have made every domain
 * object carry presentation. This keeps that boundary and pays for it with one small table.
 */
public final class StatementCopy {

    private final Map<EntryKind, String> wording;
    private final String unavailable;

    public StatementCopy(Map<EntryKind, String> wording, String unavailable) {
        this.wording = new LinkedHashMap<>(Objects.requireNonNull(wording, "wording"));
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
    }

    /**
     * The wording this system ships with.
     *
     * <p>Written for someone reading the file against a bank statement, which is what Rule L8a says
     * this document is checked against — so the phrasing is a bank's, not a broker's.
     */
    public static StatementCopy withDefaults() {
        Map<EntryKind, String> m = new LinkedHashMap<>();
        m.put(EntryKind.PAYIN, "Funds added");
        m.put(EntryKind.PAYOUT, "Withdrawal to bank account");
        m.put(EntryKind.MANDATED_RETURN, "Automatic return of unused funds");
        m.put(EntryKind.SALE_PROCEEDS, "Proceeds from sale");
        m.put(EntryKind.PURCHASE_COST, "Cost of purchase");
        m.put(EntryKind.CHARGES, "Charges");
        m.put(EntryKind.MARGIN_MOVEMENT, "Margin adjustment");
        m.put(EntryKind.ACCOUNT_ACCRUAL, "Account adjustment");
        m.put(EntryKind.OPENING_BALANCE, "Opening balance");
        m.put(EntryKind.REVERSAL, "Reversal of an earlier entry");

        // Rule L3's edge case, in the export's voice. The raw reference is already its own column,
        // so this states that a description is unavailable rather than substituting the reference
        // for one — which is precisely what Rule L3 forbids.
        return new StatementCopy(m, "Description not available");
    }

    /**
     * Wording for one entry.
     *
     * <p>Never returns a copy key, and never returns null. A kind with no wording is a build
     * failure rather than a runtime surprise — see {@code StatementCopyTest}.
     */
    public String describe(EntryDescriptionMapper.Description description) {
        Objects.requireNonNull(description, "description");
        if (description.isUnavailable()) {
            return this.unavailable;
        }
        String text = this.wording.get(description.kind());
        return text == null ? this.unavailable : text;
    }

    /** Whether every kind has wording. Used by the test that stops a new kind shipping as a key. */
    public boolean covers(EntryKind kind) {
        return this.wording.containsKey(kind);
    }
}
