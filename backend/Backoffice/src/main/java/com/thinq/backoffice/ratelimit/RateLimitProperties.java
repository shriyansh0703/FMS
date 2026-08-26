package com.thinq.backoffice.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many requests one caller may make, in what window, bound from {@code backoffice.ratelimit.*}.
 *
 * <p>EVERY NUMBER HERE IS CONFIGURATION, NOT A CONSTANT. The right limit for this API is genuinely
 * unknown today — nobody has measured what a real consumer does, and TechExcel documents no quota
 * of its own — so the first person who needs a different number must not need a release to get it.
 * Change them per environment, and change them at 3am if that is when it matters:
 *
 * <pre>
 *   backoffice.ratelimit.defaults.requests=120
 *   backoffice.ratelimit.defaults.window=1m
 *   backoffice.ratelimit.per-endpoint.ledger.requests=10
 *   backoffice.ratelimit.per-endpoint.ledger.window=1m
 * </pre>
 *
 * <p>THE PER-ENDPOINT MAP IS THE POINT. A ledger over a full financial year and a segment enable
 * cost the back office wildly different amounts, and one bucket for both means the cheap call is
 * throttled to protect against the expensive one. Anything not named falls back to
 * {@link #defaults()}.
 *
 * @param enabled           false disables the limiter entirely. It is a control, so turning it off
 *                          is a configuration change that shows up in a diff, not a code path.
 * @param defaults          the bucket every endpoint gets unless it is named below.
 * @param perEndpoint       keyed on the LAST PATH SEGMENT — {@code ledger}, {@code brk_remeshire_view},
 *                          {@code login} — so one entry covers both the bare path and TechExcel's
 *                          {@code /TechBoRest} prefix.
 * @param maxTrackedCallers ceiling on the in-memory bucket map, so an unbounded set of callers
 *                          cannot exhaust the heap. Oldest-touched buckets are evicted first.
 */
@ConfigurationProperties(prefix = "backoffice.ratelimit")
public record RateLimitProperties(boolean enabled, Bucket defaults,
                                  Map<String, Bucket> perEndpoint, int maxTrackedCallers) {

    /**
     * One allowance.
     *
     * @param requests how many requests are permitted in {@code window}.
     * @param window   the period {@code requests} is counted over.
     */
    public record Bucket(int requests, Duration window) {

        public Bucket {
            // Zero would refuse every request while reading like "no limit", which is the most
            // expensive way for a control to be misconfigured: the service is up, healthy, and
            // answering 429 to everything.
            if (requests <= 0) {
                throw new IllegalStateException(
                        "backoffice.ratelimit requests must be greater than zero, not " + requests
                                + ". To turn the limiter off set backoffice.ratelimit.enabled=false,"
                                + " which says so out loud.");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalStateException(
                        "backoffice.ratelimit window must be a positive duration, not " + window + ".");
            }
        }

        /** Tokens added per nanosecond. Kept as a rate so refill is continuous, not stepped. */
        double refillPerNano() {
            return (double) requests / window.toNanos();
        }
    }

    public RateLimitProperties {
        defaults = defaults == null ? new Bucket(60, Duration.ofMinutes(1)) : defaults;
        perEndpoint = perEndpoint == null ? Map.of() : Map.copyOf(perEndpoint);
        maxTrackedCallers = maxTrackedCallers <= 0 ? 10_000 : maxTrackedCallers;
    }

    /** The allowance for one endpoint, by its last path segment. */
    public Bucket forEndpoint(String endpoint) {
        return perEndpoint.getOrDefault(endpoint, defaults);
    }
}
