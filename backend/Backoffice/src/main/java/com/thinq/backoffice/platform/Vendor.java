package com.thinq.backoffice.platform;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE VENDOR'S VALIDATION VOCABULARY, IN ONE PLACE.
 *
 * <p>Every TechExcel endpoint validates the same way: a mandatory/optional split, a max length, a
 * character filter, a date in {@code DD/MM/YYYY}, and a rejection carrying one of a closed set of
 * error codes. Those rules are the vendor's, not ours, so they live here once rather than being
 * re-derived in each category package — a second copy of {@link #date} that accepted
 * {@code 31/02/2026} would be a bug nobody could see by reading either file alone.
 *
 * <p>THE FORMATTER IS STRICT ON PURPOSE. {@link #DMY} resolves with {@link ResolverStyle#STRICT},
 * so an impossible date is refused rather than quietly moved to the end of the month.
 *
 * <p>Nothing here touches HTTP, Spring or a clock. It is pure, and it is where a new category
 * package starts.
 */
public final class Vendor {

    private Vendor() {
    }

    /** Request dates. STRICT, so 31/02/2026 is refused rather than silently moved to the 28th. */
    public static final DateTimeFormatter DMY =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** SQL Server's timestamp, which is the shape most response dates come back in. */
    public static final DateTimeFormatter SQL_TS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    public static boolean blank(String s) {
        return s == null || s.isEmpty();
    }

    public static String optional(Map<String, Object> b, String key) {
        Object v = b.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    public static String required(Map<String, Object> b, String key, String label) {
        String v = optional(b, key);
        if (blank(v)) {
            throw ApiError.field(key, "The " + label + " field is required.");
        }
        return v;
    }

    public static void maxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw ApiError.field(field,
                    "The " + field + " may not be greater than " + max + " characters.");
        }
    }

    /**
     * The character filter. {@code forbidden} differs per document — the Ledger document forbids
     * {@code %!}, Virtual Debit {@code %@}, Client Active/Inactive {@code %&@!} — so each call site
     * passes the set ITS document lists rather than sharing one union. A union would reject a field
     * the real back office accepts, and the caller would have no way to know we invented the rule.
     */
    public static void noSpecials(String field, String value, String forbidden) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < forbidden.length(); i++) {
            if (value.indexOf(forbidden.charAt(i)) >= 0) {
                throw new ApiError("SYSTEM_Character_Filter",
                        "Invalid character '" + forbidden.charAt(i) + "' in " + field + ".");
            }
        }
    }

    public static void yesNo(String field, String value) {
        if (!blank(value) && !"Y".equals(value) && !"N".equals(value)) {
            throw new ApiError("Input_Value_Validation", field + " must be Y or N (case sensitive).");
        }
    }

    public static void oneOf(String field, String value, String... allowed) {
        if (blank(value)) {
            return;
        }
        for (String a : allowed) {
            if (a.equals(value)) {
                return;
            }
        }
        throw new ApiError("Input_Value_Validation",
                field + " must be one of " + String.join(", ", allowed) + " (case sensitive).");
    }

    /**
     * Documents say DD/MM/YYYY. New_Interest_Process's own sample uses DD-MM-YYYY, so both are
     * accepted — refusing the vendor's own example would be this service inventing a rule.
     */
    public static LocalDate date(String field, String value) {
        try {
            return LocalDate.parse(value.replace('-', '/'), DMY);
        } catch (DateTimeParseException e) {
            throw ApiError.field(field, "The " + field + " does not match the format DD/MM/YYYY.");
        }
    }

    public static void orderedRange(String fromField, String toField, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiError.field(toField,
                    "The " + toField + " must be a date after or equal to " + fromField + ".");
        }
    }

    /**
     * Ledger is financial-year scoped: 1 April to 31 March. Both dates must land in the same one.
     * The message reproduces the vendor's own failure sample, timestamp shape included.
     */
    public static void sameFinancialYear(LocalDate from, LocalDate to) {
        orderedRange("FromDate", "ToDate", from, to);
        LocalDate fyEnd = LocalDate.of(
                from.getMonthValue() >= 4 ? from.getYear() + 1 : from.getYear(), 3, 31);
        if (to.isAfter(fyEnd)) {
            throw ApiError.field("ToDate",
                    "The to date must be a date before " + fyEnd.atStartOfDay().format(SQL_TS) + ".");
        }
    }

    public static void year(String field, String value) {
        if (!value.matches("[0-9]{4}")) {
            throw ApiError.field(field, "The " + field + " must be a year in YYYY format.");
        }
    }

    /** An insertion-ordered response row, so JSON key order matches the vendor's samples. */
    public static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Split a comma-separated company code list, falling back when the caller sent none. */
    public static List<String> segments(String csv, String fallback) {
        List<String> out = new ArrayList<>();
        for (String s : (blank(csv) ? fallback : csv).split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
