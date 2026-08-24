package com.thinq.fms.integration.techexcel;

import com.thinq.fms.movement.payout.SettlementReasonCode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps TechExcel's free-text {@code Reject_Reason} onto a code the client can render.
 *
 * <p>OA-4 records that {@code Reject_Reason} is free text. That is not a contract this system can
 * rely on, so the mapping is a configured phrase table rather than a switch: extending it must be
 * a configuration change, because the phrases will change without notice and a code deploy per
 * new phrase would mean the trader sees the generic message until the next release.
 *
 * <p><b>An unmapped phrase is never shown to a trader.</b> It is stored verbatim in
 * {@code settlement_reason_text} and raises an operational alert so the table can be extended;
 * the trader sees the generic partial-settlement copy. That is how OA-4 degrades without either
 * lying to them or losing the information.
 */
public final class SettlementReasonMapper {

    private final Map<String, SettlementReasonCode> phrases;

    /**
     * @param phrases lowercase substrings mapped to codes, in priority order. Iteration order is
     *     significant — the first match wins — so a {@link LinkedHashMap} is required and a
     *     {@code HashMap} would make the result depend on hash ordering.
     */
    public SettlementReasonMapper(Map<String, SettlementReasonCode> phrases) {
        this.phrases = new LinkedHashMap<>(Objects.requireNonNull(phrases, "phrases"));
    }

    /** The table this system ships with. Operations extends it without a deploy. */
    public static SettlementReasonMapper withDefaults() {
        Map<String, SettlementReasonCode> m = new LinkedHashMap<>();
        m.put("margin", SettlementReasonCode.MARGIN_BLOCKED);
        m.put("rms", SettlementReasonCode.MARGIN_BLOCKED);
        m.put("insufficient", SettlementReasonCode.INSUFFICIENT_BALANCE);
        m.put("balance not available", SettlementReasonCode.INSUFFICIENT_BALANCE);
        m.put("ifsc", SettlementReasonCode.DESTINATION_REJECTED);
        m.put("bank account", SettlementReasonCode.DESTINATION_REJECTED);
        m.put("account closed", SettlementReasonCode.DESTINATION_REJECTED);
        m.put("blocked", SettlementReasonCode.ACCOUNT_BLOCKED);
        m.put("suspend", SettlementReasonCode.ACCOUNT_BLOCKED);
        return new SettlementReasonMapper(m);
    }

    /**
     * The code for a reason phrase.
     *
     * @return {@link SettlementReasonCode#NONE} when there is no reason text at all,
     *     {@link SettlementReasonCode#UNSPECIFIED} when there is one and nothing matched
     */
    public SettlementReasonCode map(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            return SettlementReasonCode.NONE;
        }
        String lower = rejectReason.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, SettlementReasonCode> e : this.phrases.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return SettlementReasonCode.UNSPECIFIED;
    }

    /**
     * Whether a phrase would go unmapped, so the caller can raise the operational alert that
     * gets the table extended. Separate from {@link #map} because an alert is a side effect and
     * a mapper that alerted on its own would fire once per render of a stored outcome.
     */
    public boolean isUnmapped(String rejectReason) {
        return map(rejectReason) == SettlementReasonCode.UNSPECIFIED;
    }
}
