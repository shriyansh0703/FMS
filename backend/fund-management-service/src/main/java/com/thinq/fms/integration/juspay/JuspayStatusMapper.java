package com.thinq.fms.integration.juspay;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps Juspay's order status onto {@link PayinOutcome}.
 *
 * <p><b>Configured rather than hard-coded, because the vocabulary is not in the contract.</b> The
 * supplied reference documents {@code status} as a string and points at external documentation
 * for its values rather than enumerating them. Writing a fixed switch from memory would put an
 * unverified vocabulary on the payment path, where being wrong means telling a trader their money
 * failed when it did not.
 *
 * <p>So the table below is a starting set that operations can correct without a deploy, and
 * anything unmatched maps to {@link PayinOutcome#UNKNOWN} — which Rule A9b already defines
 * behaviour for. The safe direction is built into the default: an unrecognised status becomes
 * "awaiting", never "failed".
 */
public final class JuspayStatusMapper {

    private final Map<String, PayinOutcome> statuses;

    public JuspayStatusMapper(Map<String, PayinOutcome> statuses) {
        this.statuses = new LinkedHashMap<>(Objects.requireNonNull(statuses, "statuses"));
    }

    /**
     * The starting table. Every entry here must be confirmed against Juspay's own status
     * documentation before this reaches production — it is a default, not a contract.
     */
    public static JuspayStatusMapper withDefaults() {
        Map<String, PayinOutcome> m = new LinkedHashMap<>();
        m.put("CHARGED", PayinOutcome.CONFIRMED);

        m.put("AUTHORIZATION_FAILED", PayinOutcome.BANK_DECLINED);
        m.put("AUTHENTICATION_FAILED", PayinOutcome.BANK_DECLINED);
        m.put("JUSPAY_DECLINED", PayinOutcome.SERVICE_UNREACHABLE);

        // Every in-progress status is "awaiting", not "failed". Rule A9b.
        m.put("NEW", PayinOutcome.AWAITING_BANK);
        m.put("STARTED", PayinOutcome.AWAITING_BANK);
        m.put("PENDING_VBV", PayinOutcome.AWAITING_BANK);
        m.put("AUTHORIZING", PayinOutcome.AWAITING_BANK);

        return new JuspayStatusMapper(m);
    }

    /**
     * The outcome for a raw status.
     *
     * @return {@link PayinOutcome#UNKNOWN} for anything unmapped, including null. The caller
     *     treats that as Rule A9b's wait-and-do-not-retry state and raises an operational alert
     *     so the table can be corrected
     */
    public PayinOutcome map(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PayinOutcome.UNKNOWN;
        }
        return this.statuses.getOrDefault(rawStatus.trim().toUpperCase(Locale.ROOT), PayinOutcome.UNKNOWN);
    }

    /** Whether a status would go unmapped, so the caller can alert and get the table extended. */
    public boolean isUnmapped(String rawStatus) {
        return map(rawStatus) == PayinOutcome.UNKNOWN;
    }
}
