package com.thinq.fms.messaging;

/**
 * What the reconciler decided about one notification (lld-backend.md §7.9).
 *
 * <p>The Communication Service never retries and nothing calls back to say a message never left.
 * That trade is right for a one-time password and wrong for a margin shortfall intimation, which
 * is mandatory and same-day — so this system notices, because nothing else will.
 */
public enum ReconciliationAction {

    /** A non-failure status. Record it and stop polling. */
    SETTLED,

    /**
     * Terminal non-delivery. Resubmit under a <b>new</b> request id.
     *
     * <p>Replaying the old one returns the original result and sends nothing, which is the
     * failure mode that looks most like success — the call returns 200 and the trader still has
     * no message.
     */
    RESUBMIT,

    /**
     * Still non-terminal past the poll window. Alert a human; do not retry.
     *
     * <p>A notification can sit at {@code dispatched} indefinitely on a channel with no delivery
     * reporting, so a timeout here is a signal rather than a failure. Resubmitting would risk
     * sending twice for a message that is merely unreported.
     */
    ALERT,

    /** Not yet terminal and still inside the window. Poll again later. */
    KEEP_POLLING
}
