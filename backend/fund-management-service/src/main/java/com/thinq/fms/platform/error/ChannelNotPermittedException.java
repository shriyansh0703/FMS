package com.thinq.fms.platform.error;

/**
 * This system attempted a channel it has not been granted.
 *
 * <p>An invariant failure rather than an outage: the Communication Service answers
 * {@code 403 channel_not_permitted} for a channel outside the caller's permitted list, and that is
 * a configuration error nobody will notice if it is counted as a vendor being down (OA-2).
 *
 * <p><b>A type rather than a code string.</b> The messaging module has to recognise this case to
 * record it as a suppression rather than a failure, and it previously did so by comparing
 * {@code e.code()} to a literal across a module boundary. A typed exception lets the compiler
 * enforce what a string comparison could only hope for.
 */
public class ChannelNotPermittedException extends FmsInvariantException {

    private final String channel;

    public ChannelNotPermittedException(String channel, String message) {
        super("channel_not_permitted", message);
        this.channel = channel;
    }

    public String channel() {
        return this.channel;
    }
}
