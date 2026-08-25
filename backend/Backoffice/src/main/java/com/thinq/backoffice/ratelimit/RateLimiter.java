package com.thinq.backoffice.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A token bucket per (caller, endpoint).
 *
 * <p><b>WHY A BUCKET AND NOT A COUNTER PER WINDOW.</b> A fixed-window counter lets a caller spend
 * its whole allowance in the last instant of one window and the whole of the next in the first
 * instant of the following one — twice the configured rate, back to back, which is exactly the
 * burst the limit exists to prevent. A bucket refills continuously, so the rate holds across the
 * boundary. It is about fifteen lines more and it is correct at the edge the counter gets wrong.
 *
 * <p><b>THE CEILING, STATED.</b> Buckets are in memory and per replica. Two instances behind a load
 * balancer therefore permit twice the configured rate in total, and a restart forgives everyone.
 * That is acceptable for protecting a vendor back office from one runaway consumer; it is NOT
 * acceptable as a security control, and will not stop a caller who can reach both replicas.
 * ponytail: per-replica buckets — the upgrade path is a shared counter in Redis keyed the same
 * way, and nothing above this class would change.
 *
 * <p>The map is bounded by {@code backoffice.ratelimit.max-tracked-callers}. When it is full the
 * least-recently-touched bucket is evicted, which is the right thing to lose: a caller not seen in
 * a while has a full bucket anyway.
 */
@Component
public class RateLimiter {

    /** One caller's allowance for one endpoint. */
    private static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefillNanos;
        long lastTouchedNanos;

        Bucket(RateLimitProperties.Bucket config, long nowNanos) {
            this.capacity = config.requests();
            this.refillPerNano = config.refillPerNano();
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
            this.lastTouchedNanos = nowNanos;
        }

        /** @return nanoseconds to wait, or 0 if the request is allowed and a token was spent. */
        synchronized long tryConsume(long nowNanos) {
            double refilled = (nowNanos - lastRefillNanos) * refillPerNano;
            if (refilled > 0) {
                tokens = Math.min(capacity, tokens + refilled);
                lastRefillNanos = nowNanos;
            }
            lastTouchedNanos = nowNanos;
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return 0L;
            }
            // How long until one whole token exists. Reported as Retry-After so a caller can back
            // off by the right amount instead of hammering and being refused again.
            return (long) Math.ceil((1.0d - tokens) / refillPerNano);
        }
    }

    private final RateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final AtomicLong rejected = new AtomicLong();

    // @Autowired because there are two constructors and Spring will not guess between them —
    // without it the context fails at startup looking for a no-arg one.
    @Autowired
    RateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    /**
     * Test seam. A rate limiter tested against the real clock either sleeps or is flaky, and both
     * are worse than injecting the one function that reads time.
     */
    RateLimiter(RateLimitProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** The verdict on one request. */
    public record Verdict(boolean allowed, Duration retryAfter, int limit, Duration window) {

        /** Seconds, rounded UP and never below 1 — a {@code Retry-After: 0} invites an instant retry. */
        public long retryAfterSeconds() {
            long seconds = retryAfter.toSeconds();
            return retryAfter.minusSeconds(seconds).isZero() ? Math.max(seconds, 1) : seconds + 1;
        }
    }

    private static final Verdict ALLOWED_WHEN_DISABLED =
            new Verdict(true, Duration.ZERO, 0, Duration.ZERO);

    /**
     * Spend one token for this caller on this endpoint.
     *
     * @param caller   who is asking — the bearer token's digest when there is one, the remote
     *                 address when there is not. See {@link RateLimitInterceptor} for why.
     * @param endpoint the endpoint's last path segment, so the bare and prefixed paths share one
     *                 bucket rather than granting a caller double the allowance by alternating.
     */
    public Verdict check(String caller, String endpoint) {
        if (!properties.enabled()) {
            return ALLOWED_WHEN_DISABLED;
        }
        RateLimitProperties.Bucket config = properties.forEndpoint(endpoint);
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(caller + " " + endpoint,
                key -> {
                    evictIfFull(now);
                    return new Bucket(config, now);
                });

        long waitNanos = bucket.tryConsume(now);
        if (waitNanos == 0L) {
            return new Verdict(true, Duration.ZERO, config.requests(), config.window());
        }
        rejected.incrementAndGet();
        return new Verdict(false, Duration.ofNanos(waitNanos), config.requests(), config.window());
    }

    /** How many requests have been refused. Read by tests; a metric would read it too. */
    public long rejectedCount() {
        return rejected.get();
    }

    private void evictIfFull(long nowNanos) {
        if (buckets.size() < properties.maxTrackedCallers()) {
            return;
        }
        // O(n) and deliberately so: it runs only when the map is at its ceiling, and a heap
        // maintained on every request to speed up the rare case would cost more than it saves.
        // ponytail: linear scan on eviction, revisit if the map ever needs to be large.
        String oldest = null;
        long oldestTouched = Long.MAX_VALUE;
        for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
            long touched = entry.getValue().lastTouchedNanos;
            if (touched < oldestTouched) {
                oldestTouched = touched;
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            buckets.remove(oldest);
        }
    }
}
