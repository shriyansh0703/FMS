package com.thinq.fms.messaging;

/**
 * Why an intent was never sent.
 *
 * <p>REQ-623 requires suppressed messages logged as well as sent ones, and REQ-622 requires a
 * dropped ladder step recorded with its reason. A drop that leaves no trace looks identical to a
 * message the system forgot, and support cannot tell a working suppression from a bug.
 */
public enum DropReason {

    /**
     * The state this message asserted had resolved by the time it came due.
     *
     * <p>REQ-622's central case: a warning must stop arriving once the thing it warned about is
     * over. The alternative — sending and then retracting — is explicitly forbidden.
     */
    STATE_RESOLVED,

    /** The trader has not opted in to this channel for this message type (REQ-624). */
    NOT_OPTED_IN,

    /** The daily cap for this message type on this channel was already reached (REQ-601). */
    CAP_REACHED,

    /** No address on record for the channel. */
    NO_ADDRESS,

    /**
     * The channel is not granted to this system.
     *
     * <p>Recorded as a suppression rather than a failure so it is visible in the delivery log,
     * but it is a configuration error and alerts separately — OA-2's failure mode.
     */
    CHANNEL_NOT_PERMITTED
}
