package com.thinq.fms.platform.error;

/**
 * A vendor or downstream dependency could not be reached or did not answer. Maps to 503.
 *
 * <p>Names <i>which</i> upstream internally so an operator can act, and never names it to
 * the client — a trader does not need to know which back office is down, only that a
 * figure or an action is unavailable.
 */
public class FmsUpstreamException extends FmsException {

    private final String upstream;

    public FmsUpstreamException(String code, String upstream, String message) {
        super(code, message);
        this.upstream = upstream;
    }

    public FmsUpstreamException(String code, String upstream, String message, Throwable cause) {
        super(code, message, cause);
        this.upstream = upstream;
    }

    /** Internal use only — logging, metrics, alerting. Not for a client response. */
    public String upstream() {
        return this.upstream;
    }
}
