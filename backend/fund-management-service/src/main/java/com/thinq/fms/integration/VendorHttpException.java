package com.thinq.fms.integration;

/**
 * A non-2xx response, or a 2xx this system could not read.
 *
 * <p>Carries the raw status, path and body so a gateway's {@code translate} can map it into the
 * domain. Deliberately not an {@code FmsException}: it has no meaning until a gateway that knows
 * the vendor's vocabulary gives it one, and letting it reach a caller untranslated would leak
 * one vendor's status codes into code that must not know which vendor answered.
 *
 * <p>The body is retained for logging and error mapping. It must not be rendered to a trader —
 * a vendor's raw error text is not user-facing copy.
 */
public class VendorHttpException extends Exception {

    private final int status;
    private final String path;
    private final String body;

    public VendorHttpException(int status, String path, String body) {
        super("HTTP " + status + " from " + path);
        this.status = status;
        this.path = path;
        this.body = body;
    }

    public int status() {
        return this.status;
    }

    public String path() {
        return this.path;
    }

    /** Internal use only — logging and error mapping. Never a trader-facing message. */
    public String body() {
        return this.body;
    }

    /** Whether the status suggests retrying could succeed. Transport-level only. */
    public boolean isTransient() {
        return this.status == 429 || this.status >= 500;
    }
}
