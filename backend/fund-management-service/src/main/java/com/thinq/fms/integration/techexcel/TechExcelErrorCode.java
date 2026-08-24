package com.thinq.fms.integration.techexcel;

/**
 * TechExcel's error vocabulary, as the contract states it.
 *
 * <p>Taken from the Error Codes sheet of {@code TechExcel_API_Master.xlsx}, verified 21 Aug 2026.
 *
 * <p><b>The important entry is the one that is missing.</b> TechExcel documents eight validations
 * per endpoint, of which two — "Input Value Validation" and "Duplication Validation" — return the
 * <i>same</i> code, {@code Input_Value_Validation}, with no distinguishing description. On some
 * endpoints the duplication row's code is literally {@code *}.
 *
 * <p>That is why {@code PayoutRail.statusOf} exists and why §6.3 reads before it reissues: a
 * refusal cannot be read as "already paid", because it is indistinguishable from "your input was
 * wrong". Inferring duplication from this code would be the single most expensive mistake
 * available on the payout path — it would let a genuine input error be recorded as a completed
 * payment (OA-7).
 */
public enum TechExcelErrorCode {

    /** A required input was absent or malformed. */
    INPUT_VALIDATION("Input_Validation"),

    /**
     * An input value was rejected — <b>or</b> the request duplicated an existing one. TechExcel
     * does not distinguish the two. Never interpret this as duplication.
     */
    INPUT_VALUE_VALIDATION("Input_Value_Validation"),

    /** A field contained characters TechExcel's own filter rejects. */
    SYSTEM_CHARACTER_FILTER("SYSTEM_Character_Filter"),

    /** TechExcel's database raised an error. Retryable at the caller's discretion. */
    DATABASE_EXCEPTION("Database_Exception"),

    /** No session token was presented. */
    TOKEN_MISSING("Token Validation Missing"),

    /** The session token has expired and must be re-obtained via {@code /TechBoRest/api/login}. */
    TOKEN_EXPIRED("Token Validation Expired"),

    /** A code this system does not recognise. Treated as an outage, never guessed at. */
    UNRECOGNISED("");

    private final String wireValue;

    TechExcelErrorCode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return this.wireValue;
    }

    /**
     * Map a wire value onto this vocabulary.
     *
     * <p>Two spellings exist in the contract for the token errors — {@code Token Missing} and
     * {@code Token Validation Missing} appear on different endpoints — so both are accepted
     * rather than one being assumed canonical.
     *
     * <p>Anything unmatched becomes {@link #UNRECOGNISED}, which callers treat as an outage. An
     * unrecognised response is one this system does not understand, and guessing what it meant
     * is how a rejection gets read as a success.
     */
    public static TechExcelErrorCode fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNRECOGNISED;
        }
        String v = raw.trim();
        for (TechExcelErrorCode c : values()) {
            if (c != UNRECOGNISED && c.wireValue.equalsIgnoreCase(v)) {
                return c;
            }
        }
        if (v.toLowerCase().startsWith("token") && v.toLowerCase().contains("expire")) {
            return TOKEN_EXPIRED;
        }
        if (v.toLowerCase().startsWith("token")) {
            return TOKEN_MISSING;
        }
        return UNRECOGNISED;
    }

    /** Whether re-obtaining a session token and retrying the call could succeed. */
    public boolean isSessionProblem() {
        return this == TOKEN_MISSING || this == TOKEN_EXPIRED;
    }
}
