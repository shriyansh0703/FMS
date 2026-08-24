package com.thinq.fms.platform.error;

import com.thinq.fms.platform.money.Money;

/**
 * The request cannot be cancelled, and the message says why (REQ-305).
 *
 * <p>Carries the reason rather than a bare refusal, because the two cases mean opposite things to
 * a trader: money already instructed to a rail is on its way and will arrive, while an already
 * cancelled request needs no action at all.
 */
public class RequestNotCancellableException extends FmsConflictException {

    private final String reasonCode;

    public RequestNotCancellableException(String reasonCode, String message) {
        super("not_cancellable", message);
        this.reasonCode = reasonCode;
    }

    /** {@code ALREADY_INSTRUCTED} | {@code ALREADY_TERMINAL} | {@code NOT_FOUND}. */
    public String reasonCode() {
        return this.reasonCode;
    }

    public static RequestNotCancellableException alreadyInstructed(long id) {
        return new RequestNotCancellableException("ALREADY_INSTRUCTED",
                "request " + id + " has been instructed to the payout rail and can no longer be stopped");
    }

    public static RequestNotCancellableException alreadyTerminal(long id, Object state) {
        return new RequestNotCancellableException("ALREADY_TERMINAL",
                "request " + id + " is already " + state);
    }
}
