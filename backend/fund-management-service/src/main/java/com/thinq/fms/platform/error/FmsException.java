package com.thinq.fms.platform.error;

/**
 * Root of the Fund Management System's exception hierarchy (lld-backend.md §7.7).
 *
 * <p>The three branches below are not a taxonomy for its own sake — each maps to a
 * different response class and a different operational reaction:
 *
 * <ul>
 *   <li>{@link FmsClientException} — 4xx. The caller can do something about it.
 *   <li>{@link FmsUpstreamException} — 503. A vendor is unavailable; nobody is at fault
 *       and retrying later may work.
 *   <li>{@link FmsInvariantException} — 500, <b>and it pages someone</b>. The system
 *       reached a state its own rules say is impossible. The correct response is to stop,
 *       not to degrade.
 * </ul>
 *
 * <p>Every subclass carries a stable {@code code} because the client renders a copy key
 * from it rather than the message. Internal detail must never reach a client-visible
 * response.
 */
public abstract class FmsException extends RuntimeException {

    private final String code;

    protected FmsException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected FmsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Stable, machine-readable, safe to send to a client. */
    public String code() {
        return this.code;
    }

    /**
     * Whether this failure should page a human rather than be counted.
     *
     * <p>Only {@link FmsInvariantException} overrides this. A client error is not an
     * incident and an upstream outage is already visible in its own metrics.
     */
    public boolean pagesOnCall() {
        return false;
    }
}
