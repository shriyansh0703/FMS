package com.thinq.fms.platform.error;

/**
 * The trader has no verified bank account to fund from.
 *
 * <p>REQ-203: money is accepted only from an account the trader has proven they hold, and Profile
 * PR-28 requires that checked live rather than against a cached list.
 *
 * <p>Distinct from {@code destination_not_verified}, which is the withdrawal side. The two read
 * alike and mean opposite directions of travel, and a trader told the wrong one goes looking in the
 * wrong place — REQ-505 requires the blocker named, not merely reported.
 */
public class NoVerifiedSourceException extends FmsUnprocessableException {

    public NoVerifiedSourceException(String message) {
        super("no_verified_source", message);
    }
}
