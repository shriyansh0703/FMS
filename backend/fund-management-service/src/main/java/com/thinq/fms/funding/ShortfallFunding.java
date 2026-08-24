package com.thinq.fms.funding;

import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.platform.money.Money;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Funding while a margin shortfall is outstanding (REQ-207, Rule A11).
 *
 * <p>A trader funding under a shortfall is working against a deadline, so the defaults change: the
 * amount is pre-filled with what is short, the route offered is the fastest available rather than
 * the cheapest, and the time remaining before positions may be closed is stated. Making them
 * compute the figure and pick a route under that pressure is how the wrong amount gets sent.
 *
 * @param suggestedAmount   the shortfall, so the trader is not re-entering a figure under a deadline
 * @param fastestRoute      the quickest route that can carry it, absent where none can
 * @param timeRemaining     until positions may be closed, absent where the deadline is not known
 */
public record ShortfallFunding(Money suggestedAmount,
                               Optional<PaymentRoute> fastestRoute,
                               Optional<Duration> timeRemaining) {

    public ShortfallFunding {
        Objects.requireNonNull(suggestedAmount, "suggestedAmount");
        Objects.requireNonNull(fastestRoute, "fastestRoute");
        Objects.requireNonNull(timeRemaining, "timeRemaining");

        if (!suggestedAmount.isPositive()) {
            throw new IllegalArgumentException(
                    "there is no shortfall funding prompt without a shortfall; got " + suggestedAmount);
        }
    }

    /**
     * Whether the deadline can be stated.
     *
     * <p>REQ-506 requires the time remaining shown while the shortfall can still be fixed. Where it
     * is unknown the prompt still appears — the shortfall is real either way — but no countdown is
     * invented, because a deadline that proves wrong is worse than none.
     */
    public boolean deadlineKnown() {
        return this.timeRemaining.isPresent();
    }

    /** Whether any route can carry the amount today (Rule A9d, Rule A12). */
    public boolean canBeFunded() {
        return this.fastestRoute.isPresent();
    }
}
