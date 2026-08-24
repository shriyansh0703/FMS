package com.thinq.fms.messaging;

/**
 * Re-checks, at dispatch time, whether the state an intent asserts is still true (REQ-622).
 *
 * <p>This is the reason a message intent cannot be a plain delayed job. A delayed job fires on a
 * schedule and sends whatever it was given; a ladder step written on day 0 for day 7 must be able
 * to look again on day 7 and find the shortfall gone.
 *
 * <p>Implementations read the state directly rather than trusting anything captured when the
 * intent was written. The whole point is that the captured value may now be wrong.
 */
public interface StateAssertionChecker {

    /**
     * Whether the asserted state still holds.
     *
     * @return false when it has resolved, which drops the message rather than sending it.
     *     REQ-622 forbids sending and then retracting, so this returning a wrong true is
     *     unrecoverable — a message about a shortfall the trader already cleared cannot be taken
     *     back
     */
    boolean stillHolds(MessageIntent intent);
}
