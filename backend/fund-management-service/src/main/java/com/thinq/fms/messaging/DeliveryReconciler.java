package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.DeliveryStatus;
import com.thinq.fms.integration.communication.NotificationStatus;
import com.thinq.fms.integration.communication.MessageChannel;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Decides what to do about a submitted notification (lld-backend.md §7.9).
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>The Communication Service claims a notification and commits that claim before contacting the
 * provider, so a crash mid-hand-off leaves it unsent rather than sent twice. {@code failed} is
 * terminal and <b>nothing retries</b>. For a margin shortfall intimation — mandatory, same-day,
 * with a live deadline — an unsent message that nobody notices is the worst available outcome.
 *
 * <h2>Pure, so the policy is testable</h2>
 *
 * <p>This class decides; it does not poll, submit or alert. The decision is the part with rules in
 * it, and keeping the I/O outside means every branch can be exercised without a clock, a network
 * or a database.
 */
public final class DeliveryReconciler {

    private final Duration pollWindow;

    /**
     * @param pollWindow how long a non-terminal status is tolerated before a human is told. Not a
     *     retry interval — reaching it produces an alert, never a resubmission
     */
    public DeliveryReconciler(Duration pollWindow) {
        this.pollWindow = Objects.requireNonNull(pollWindow, "pollWindow");
        if (pollWindow.isNegative() || pollWindow.isZero()) {
            throw new IllegalArgumentException("the poll window must be positive; got " + pollWindow);
        }
    }

    /**
     * What to do about a notification in this status.
     *
     * @param status      what the service last reported
     * @param submittedAt when it was submitted, for the window check
     * @param now         the current instant
     */
    public ReconciliationAction actionFor(DeliveryStatus status, Instant submittedAt, Instant now) {
        return actionFor(new NotificationStatus(status, false, true, null), submittedAt, now);
    }

    /**
     * What to do about a notification, given everything the service reported.
     *
     * <p>Prefer this over the status-only form: the platform now reports {@code stuck} directly,
     * and inferring it from a poll window guesses at something the supplier already knows.
     */
    public ReconciliationAction actionFor(NotificationStatus reported, Instant submittedAt, Instant now) {
        Objects.requireNonNull(reported, "reported");
        Objects.requireNonNull(submittedAt, "submittedAt");

        DeliveryStatus status = reported.status();

        // "Nothing further is owed" is not the same question as "the service will say no more".
        // DELIVERED is terminal and settled; SENT is non-terminal and settled; FAILED is terminal
        // and needs action. DeliveryStatus names both notions so this branch does not have to.
        if (status.needsNoFurtherAction()) {
            return ReconciliationAction.SETTLED;
        }
        if (status.isTerminal()) {
            return ReconciliationAction.RESUBMIT;
        }

        // The platform's own answer, checked before the window. §7: "the hand-off started and never
        // finished. A human needs to look." Waiting out a poll window on a notification the
        // service has already flagged wastes the part of the deadline that was still usable.
        if (reported.stuck()) {
            return ReconciliationAction.ALERT;
        }

        boolean pastWindow = Duration.between(submittedAt, now).compareTo(this.pollWindow) > 0;
        return pastWindow ? ReconciliationAction.ALERT : ReconciliationAction.KEEP_POLLING;
    }

    /**
     * Whether an intimation has been made, given the outcomes on each channel.
     *
     * <p>Rule C1's two channels are two submissions that fail independently. §7.9 settles the
     * question they raise: <b>the intimation is made when at least one reaches a
     * non-terminal-failure status.</b> Requiring both would declare a failure while the trader has
     * in fact been told; requiring neither would declare success while they have not.
     */
    public boolean intimationMade(DeliveryStatus sms, DeliveryStatus email) {
        return isNotFailure(sms) || isNotFailure(email);
    }

    /**
     * Whether a channel's outcome should be recorded and alerted even though the other succeeded.
     *
     * <p>REQ-627: a trader reachable on only one channel is a fact support needs. Suppressing the
     * failed channel because the other worked would hide a trader drifting toward being
     * unreachable entirely.
     */
    public boolean recordChannelFailure(DeliveryStatus status) {
        return status != null && status.isTerminal() && status != DeliveryStatus.DELIVERED;
    }

    /**
     * The one case this design cannot resolve by retrying: both channels failed terminally while
     * the state the message asserts still stands.
     *
     * <p>The account is in an action state, the deadline is live, and no channel has carried the
     * message. <b>Alerting is not a fallback for delivery — it is an admission that delivery
     * failed while it still mattered.</b>
     */
    public boolean pageAHuman(DeliveryStatus sms, DeliveryStatus email, boolean stateStillStands) {
        return stateStillStands && !intimationMade(sms, email);
    }

    /**
     * Whether a status is evidence the trader actually received the message.
     *
     * <p>Delegates to the status, which needs the channel: on SMS, {@code delivered} means the
     * aggregator accepted it, not that a handset received it. No decision may rest on it there,
     * including whether a regulatory intimation obligation was met.
     */
    public boolean provesReceipt(DeliveryStatus status, MessageChannel channel) {
        return status != null && status.provesReceipt(channel);
    }

    private static boolean isNotFailure(DeliveryStatus status) {
        if (status == null) {
            return false;
        }
        return !status.isTerminal() || status == DeliveryStatus.DELIVERED;
    }
}
