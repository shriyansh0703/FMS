package com.thinq.fms.api;

import com.thinq.fms.platform.money.AccountRef;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import java.time.Duration;
import java.util.Objects;

/**
 * A per-account request budget (Stage 11, HIGH-1).
 *
 * <p><b>Per account, not per instance.</b> A global limit on a multi-tenant money API is a
 * denial-of-service primitive: one trader exhausting it locks out everyone else. Keying on the
 * authenticated account means a caller can only spend their own budget.
 *
 * <p><b>Why the statement export gets its own, smaller budget.</b> It fans out to TechExcel and
 * streams up to a 92-day window. The window caps any single response, but nothing capped the rate,
 * so a trader could drive vendor load without ever exceeding a per-request bound. The circuit
 * breaker in {@code AbstractVendorGateway} protects this system from a failing vendor; it does not
 * protect the vendor from us.
 *
 * <p>In-memory and therefore per-instance. That is a deliberate floor rather than a complete answer:
 * behind N replicas the effective limit is N times these numbers. A distributed limiter belongs at
 * the gateway, which sees all traffic; this exists so the control is present in the service even
 * when the gateway's is not.
 */
public final class PerAccountRateLimit {

    /**
     * Ordinary reads. Generous — this is an abuse ceiling, not a usage quota.
     *
     * <p><b>Per instance, as the name says.</b> Behind N replicas a caller gets N times this figure,
     * so it is a floor rather than a system limit. The names carry the qualifier because the numbers
     * get quoted in reports and read as the ceiling otherwise.
     */
    static final RateLimiterConfig READS_PER_INSTANCE = RateLimiterConfig.custom()
            .limitForPeriod(120)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    /** The statement export, which reaches a vendor and streams a large response. */
    static final RateLimiterConfig EXPORTS_PER_INSTANCE = RateLimiterConfig.custom()
            .limitForPeriod(6)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    /**
     * Money movement.
     *
     * <p>Deliberately tight. Rule W4's partial index already permits only one open withdrawal per
     * account, so a legitimate trader has no reason to submit repeatedly; a caller who is doing so
     * is either confused or probing.
     */
    static final RateLimiterConfig MOVEMENTS_PER_INSTANCE = RateLimiterConfig.custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    private final RateLimiterRegistry reads;
    private final RateLimiterRegistry exports;
    private final RateLimiterRegistry movements;

    public PerAccountRateLimit() {
        this(READS_PER_INSTANCE, EXPORTS_PER_INSTANCE, MOVEMENTS_PER_INSTANCE);
    }

    /** Visible for tests and for a deployment that needs different budgets. */
    public PerAccountRateLimit(RateLimiterConfig reads, RateLimiterConfig exports,
                               RateLimiterConfig movements) {
        this.reads = RateLimiterRegistry.of(reads);
        this.exports = RateLimiterRegistry.of(exports);
        this.movements = RateLimiterRegistry.of(movements);
    }

    /** What the caller was trying to do, which decides which budget applies. */
    public enum Operation {
        READ,
        EXPORT,
        MOVEMENT
    }

    /**
     * Consume one permit, or refuse.
     *
     * <p>Returns rather than throws so the caller decides the response shape. The timeout is zero on
     * every budget: a rate limiter that blocks converts a burst into held request threads, which is
     * the resource exhaustion it was added to prevent.
     */
    public boolean permit(AccountRef account, Operation operation) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(operation, "operation");

        RateLimiterRegistry registry = switch (operation) {
            case READ -> this.reads;
            case EXPORT -> this.exports;
            case MOVEMENT -> this.movements;
        };
        // Keyed by account and operation, so one budget cannot be spent by the other.
        RateLimiter limiter = registry.rateLimiter(operation.name() + ":" + account.ucc());
        return limiter.acquirePermission();
    }
}
