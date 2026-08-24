package com.thinq.fms.derivation;

/**
 * Which system answered a margin question.
 *
 * <p>Not an implementation detail. REQ-107 renders this next to the figure's timestamp, so a
 * value stepping at the market-open boundary reads as a scheduled handover between two
 * systems rather than as a data error. The HLD's hard cutover means the same question has a
 * different authority depending on the clock, and the trader is told which one answered.
 */
public enum MarginSourceKind {
    /** Kambala Noren's RMS. Authoritative while the market is open. */
    FRONT_OFFICE,
    /** TechExcel, the system of record. Authoritative after the end-of-day cutover. */
    BACK_OFFICE
}
