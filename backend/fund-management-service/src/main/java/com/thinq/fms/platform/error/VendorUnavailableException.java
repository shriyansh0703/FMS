package com.thinq.fms.platform.error;

/** A vendor call timed out, tripped its circuit breaker, or failed at the transport. */
public class VendorUnavailableException extends FmsUpstreamException {

    public VendorUnavailableException(String upstream, String message) {
        super("upstream_unavailable", upstream, message);
    }

    public VendorUnavailableException(String upstream, String message, Throwable cause) {
        super("upstream_unavailable", upstream, message, cause);
    }
}
