package com.thinq.fms.platform.error;

/**
 * The destination is not an account Profile has verified the trader holds, <b>at this instant</b>.
 *
 * <p>Profile PR-28 requires the check against a live read rather than a cached list. An account
 * whose verification was withdrawn must stop being a legal destination immediately, not at the end
 * of the trader's session.
 */
public class DestinationNotVerifiedException extends FmsUnprocessableException {

    public DestinationNotVerifiedException(String message) {
        super("destination_not_verified", message);
    }
}
