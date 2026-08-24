package com.thinq.fms.platform.error;

/**
 * A request that conflicts with the account's current state. Maps to 409.
 *
 * <p>Distinct from {@link FmsUnprocessableException} on purpose: a 409 means "not now" and the
 * caller may succeed by retrying after something changes, while a 422 means "not with these
 * values". Collapsing them would leave the client unable to tell a wait from a correction.
 */
public abstract class FmsConflictException extends FmsClientException {

    protected FmsConflictException(String code, String message) {
        super(code, 409, message);
    }
}
