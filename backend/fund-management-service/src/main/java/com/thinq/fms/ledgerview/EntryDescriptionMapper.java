package com.thinq.fms.ledgerview;

import java.util.Map;

/**
 * Turns a back-office ledger entry into language the account holder understands (REQ-401).
 *
 * <p>The PRD names illegible transaction history as one of four documented competitor defects, and
 * Rule L3 is blunt about why it matters: the history is exactly where an anxious user goes to find
 * out where their money went, and it is the wrong place to be illegible. So this is a contract
 * rather than a helper.
 *
 * <p><b>Pure, and configuration-driven.</b> No I/O and no transaction, so it can be exhaustively
 * tested against constructed entries. The mapping table is configuration loaded at startup, which
 * makes a newly appearing entry type a settings change rather than a release — the alternative
 * leaves users looking at an unmapped entry until the next deploy.
 */
public interface EntryDescriptionMapper {

    /**
     * Describe one entry.
     *
     * <p><b>Never returns null and never throws.</b> An unmapped combination is a product state,
     * not an exception: it returns {@link Description#unavailable} , which says explicitly that a
     * plain description is not yet available and shows the raw reference <i>alongside</i> that
     * statement. Rule L3 forbids presenting a settlement identifier as though it were the
     * description, so the fallback must not silently become one.
     *
     * @param entry non-null, carrying at least a transaction type
     */
    Description describe(LedgerEntry entry);

    /**
     * The copy key for an entry, its parameters, and the two facts the list needs without
     * opening it.
     *
     * @param kind            what sort of event this is. Carried rather than recoverable from the
     *                        copy key: an earlier version of the query service parsed the kind back
     *                        out of the key string, which made the key format load-bearing for
     *                        something it was never meant to encode
     * @param copyKey         what the client resolves wording from. No English here
     * @param parameters      template values. Amounts are already in paise, so no consumer of a
     *                        description converts money
     * @param secondaryDetail the back-office reference — voucher, settlement or bill number.
     *                        Retained and shown as secondary detail, never as the description
     * @param userCaused      Rule L4: whether the account holder caused this. Part of the mapping
     *                        rather than a separate concern because only the mapper knows that a
     *                        mandated settlement return and a user-requested payout are both
     *                        {@code TRANS_TYPE = P}
     */
    record Description(EntryKind kind,
                       String copyKey,
                       Map<String, String> parameters,
                       String secondaryDetail,
                       boolean userCaused) {

        /** The copy key the client renders as "a plain description is not available yet". */
        public static final String UNAVAILABLE_KEY = "ENTRY_DESCRIPTION_UNAVAILABLE";

        public Description {
            parameters = Map.copyOf(parameters);
        }

        /**
         * The fallback for a combination the table does not know.
         *
         * <p>Every one of these is counted and alerted by the mapper, because an entry type
         * appearing in production that the table does not know about is a requirement gap that a
         * user is looking at right now.
         */
        public static Description unavailable(String secondaryDetail) {
            // ACCOUNT_ACCRUAL rather than a null or a dedicated UNKNOWN kind: an unmapped entry is
            // still money that moved, so it belongs in the full ledger view. Putting it in the
            // money-in-and-out view would show a trader an entry nothing can describe, in the one
            // view they use to answer a simple question.
            return new Description(EntryKind.ACCOUNT_ACCRUAL, UNAVAILABLE_KEY, Map.of(),
                    secondaryDetail, false);
        }

        public boolean isUnavailable() {
            return UNAVAILABLE_KEY.equals(this.copyKey);
        }
    }
}
