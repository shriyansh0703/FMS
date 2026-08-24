package com.thinq.fms.platform.error;

/**
 * The trading and settlement calendar could not be established.
 *
 * <p>Its own type rather than a plain {@link VendorUnavailableException} because the
 * product behaviour differs: a missing calendar makes the withdrawable figure
 * <i>uncomputable</i> rather than merely stale, and no mandated settlement may execute on
 * an unverified date. The client renders this distinctly from a generic upstream outage
 * for exactly that reason.
 *
 * <p>Open at the time of writing: EB-9 has no nominated source.
 */
public class CalendarUnavailableException extends FmsUpstreamException {

    public CalendarUnavailableException(String message) {
        super("calendar_unavailable", "trading-calendar", message);
    }

    /**
     * Carries the underlying failure.
     *
     * <p>A calendar lookup that fails on an {@code IOException} or a parse error loses the only
     * evidence of why if the cause is dropped, and "calendar unavailable" with no cause is the
     * least actionable page an operator can receive.
     */
    public CalendarUnavailableException(String message, Throwable cause) {
        super("calendar_unavailable", "trading-calendar", message, cause);
    }
}
