package com.thinq.backoffice.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rejection in TechExcel's own vocabulary.
 *
 * <p>The codes are the closed set enumerated in every one of the vendor's API documents under
 * "Flow of checking API Call and it's error message": {@code Input_Validation},
 * {@code SYSTEM_Character_Filter}, {@code Input_Value_Validation}, {@code Database_Exception},
 * {@code Token Missing}, {@code Token Expired}. A caller written against the real back office
 * knows those strings and nothing else, so this service must not invent a further kind of
 * failure.
 *
 * <p>There is deliberately NO HTTP STATUS here. TechExcel answers 200 for a rejected call and puts
 * the verdict in the body.
 */
public class ApiError extends RuntimeException {

    private final String code;
    private final Object description;

    public ApiError(String code, Object description) {
        super(code + ": " + description);
        this.code = code;
        this.description = description;
    }

    /**
     * {@code Input_Validation} reports field -&gt; list of messages, the shape the vendor's Ledger
     * and Client Active/Inactive samples show. Every other code carries a plain string.
     */
    public static ApiError field(String field, String message) {
        Map<String, Object> byField = new LinkedHashMap<>();
        byField.put(field, List.of(message));
        return new ApiError("Input_Validation", byField);
    }

    public Map<String, Object> envelope() {
        return envelope(code, description);
    }

    /** Insertion-ordered so the JSON key order matches the vendor's samples. */
    public static Map<String, Object> envelope(String code, Object description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Success", "false");
        m.put("Success Description", "");
        m.put("Error Code", code);
        m.put("Error Description", description);
        return m;
    }

    /** The documented success envelope. {@code description} is a string on writes, rows on reads. */
    public static Map<String, Object> ok(Object description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Success", "True");
        m.put("Success Description", description);
        m.put("Error Code", "");
        m.put("Error Description", "");
        return m;
    }
}
