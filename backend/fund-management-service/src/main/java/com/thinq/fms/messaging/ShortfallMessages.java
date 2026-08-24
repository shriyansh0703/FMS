package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.Money;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parameters a shortfall message carries (REQ-602, REQ-603).
 *
 * <p>Pure, and resolved at dispatch rather than when the intent was queued. REQ-621 requires the
 * figures to come from the same {@code derive()} call the funds screen uses — a shortfall captured
 * two hours earlier, when the ladder's third step was scheduled, is not the shortfall the trader has
 * now, and quoting it would send them to fund an amount that no longer clears it.
 *
 * <h2>Rule C16 and why the channels differ</h2>
 *
 * <p><b>SMS carries no link, ever.</b> Rule C16 is unconditional, and the consequence is that SMS
 * has to be actionable from its text alone — so the amount and the deadline are stated rather than
 * delegated to a control. Email carries the breakdown because it is the only channel that can, and
 * WhatsApp carries the action control.
 */
public final class ShortfallMessages {

    /** What produced the shortfall, which Rule B8 requires distinguished. */
    public enum Cause {
        /** The trader's own trade created it. */
        USER_ACTION,
        /** A market move against existing positions created it. */
        MARKET_MOVE
    }

    /**
     * Parameters for one step of the ladder on one channel.
     *
     * @param requirement       the margin required
     * @param availableMargin   what the account has against it
     * @param shortfall         the difference, which is what the trader must fund
     * @param timeRemaining     until positions may be closed, absent where not known
     * @param cause             Rule B8's distinction, so the trader knows whether they did this
     */
    public static MessageSpec forStep(String templateKey,
                                      MessageChannel channel,
                                      Money requirement,
                                      Money availableMargin,
                                      Money shortfall,
                                      Optional<Duration> timeRemaining,
                                      Cause cause) {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(timeRemaining, "timeRemaining");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(availableMargin, "availableMargin");
        Objects.requireNonNull(shortfall, "shortfall");

        if (!shortfall.isPositive()) {
            throw new IllegalArgumentException(
                    "there is no shortfall message without a shortfall; got " + shortfall);
        }
        // REQ-603 requires the three figures presented as an arithmetic the trader can follow. If
        // they do not reconcile, the email shows a subtraction that does not work.
        if (!availableMargin.plus(shortfall).equals(requirement)) {
            throw new IllegalArgumentException(
                    "the shortfall must be the requirement less the available margin; "
                            + availableMargin + " + " + shortfall + " != " + requirement);
        }

        Map<String, String> p = new LinkedHashMap<>();
        // REQ-602: the exact amount, carried into whatever surface the message opens, so the trader
        // is not retyping a figure under a deadline.
        p.put("shortfall", PayinMessages.rupees(shortfall));
        p.put("cause", cause.name());

        // Rule H7: every step states the amount short and the deadline. Where the deadline is not
        // known it is said to be unknown rather than omitted, which would read as no deadline.
        p.put("deadlineKnown", Boolean.toString(timeRemaining.isPresent()));
        p.put("minutesRemaining", timeRemaining.map(d -> Long.toString(d.toMinutes())).orElse(""));

        if (channel == MessageChannel.EMAIL) {
            // REQ-603: email is the only channel that can carry a breakdown, so it is the only one
            // that gets the separate named figures.
            p.put("requirement", PayinMessages.rupees(requirement));
            p.put("availableMargin", PayinMessages.rupees(availableMargin));
            // The account state in the subject, so the message is identifiable unopened.
            p.put("subjectState", "MARGIN_SHORTFALL");
        }

        // Rule C16, stated as a parameter so a template cannot quietly add one.
        p.put("actionControl", channel == MessageChannel.SMS ? "NONE" : "FUND_EXACT_AMOUNT");

        return new MessageSpec(templateKey, channel, Map.copyOf(p));
    }

    private ShortfallMessages() {
    }
}
