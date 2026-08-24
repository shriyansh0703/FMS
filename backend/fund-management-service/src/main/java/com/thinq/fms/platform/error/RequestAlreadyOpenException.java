package com.thinq.fms.platform.error;

/**
 * Rule W4: an account may hold only one open withdrawal request.
 *
 * <p><b>Always translated from the database constraint, never from a prior read.</b> The service
 * does not check for an existing request before inserting — a read-then-write is a race dressed
 * as validation, and two requests arriving together would both pass it. V21's partial unique index
 * is the actual guarantee, and this exception is what its violation becomes.
 */
public class RequestAlreadyOpenException extends FmsConflictException {

    public RequestAlreadyOpenException(String message) {
        super("request_already_open", message);
    }
}
