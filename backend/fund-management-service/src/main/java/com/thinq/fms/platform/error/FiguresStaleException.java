package com.thinq.fms.platform.error;

import java.time.Duration;
import java.time.Instant;

/**
 * The figures behind a decision are too old to act on.
 *
 * <p>Carries the age rather than only refusing, because REQ-107 requires the trader told how
 * current a figure is and the same obligation applies when it is refused for being stale.
 *
 * <p>A 409 rather than a 503: nothing is broken, the answer is simply out of date, and refetching
 * will very likely succeed.
 */
public class FiguresStaleException extends FmsConflictException {

    private final Instant computedAt;
    private final String computedBy;

    public FiguresStaleException(Instant computedAt, String computedBy, Duration age) {
        super("figures_stale",
                "figures from " + computedBy + " computed at " + computedAt + " are " + age.toSeconds() + "s old");
        this.computedAt = computedAt;
        this.computedBy = computedBy;
    }

    public Instant computedAt() {
        return this.computedAt;
    }

    public String computedBy() {
        return this.computedBy;
    }
}
