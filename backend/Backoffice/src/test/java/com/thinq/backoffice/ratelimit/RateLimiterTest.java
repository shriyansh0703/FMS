package com.thinq.backoffice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * The bucket, against a fake clock.
 *
 * <p>Time is injected rather than slept through: a rate limiter tested against the real clock is
 * either slow or flaky, and both are worse than passing in the one function that reads time.
 *
 * <p>The case that matters is the LAST one. A fixed-window counter passes every other test here and
 * fails that one, which is the whole reason this is a bucket.
 */
class RateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final AtomicLong now = new AtomicLong();

    private RateLimiter limiterOf(int requests) {
        RateLimitProperties props = new RateLimitProperties(true,
                new RateLimitProperties.Bucket(requests, MINUTE), Map.of(), 100);
        return new RateLimiter(props, now::get);
    }

    private void advance(Duration d) {
        now.addAndGet(d.toNanos());
    }

    @Test
    void spendsTheAllowanceThenRefuses() {
        RateLimiter limiter = limiterOf(3);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.check("t:abc", "ledger").allowed())
                    .as("request %d of the allowance", i + 1).isTrue();
        }

        RateLimiter.Verdict refused = limiter.check("t:abc", "ledger");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.limit()).isEqualTo(3);
        // Never zero: a Retry-After of 0 invites an instant retry that is refused again.
        assertThat(refused.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(limiter.rejectedCount()).isEqualTo(1);
    }

    @Test
    void callersAndEndpointsGetTheirOwnBuckets() {
        RateLimiter limiter = limiterOf(1);

        assertThat(limiter.check("t:abc", "ledger").allowed()).isTrue();
        assertThat(limiter.check("t:abc", "ledger").allowed()).isFalse();
        // A different caller, and the same caller on a different endpoint, are both unaffected.
        assertThat(limiter.check("t:xyz", "ledger").allowed()).isTrue();
        assertThat(limiter.check("t:abc", "brk_remeshire_view").allowed()).isTrue();
    }

    @Test
    void refillsContinuously() {
        RateLimiter limiter = limiterOf(60);   // one token per second

        for (int i = 0; i < 60; i++) {
            limiter.check("t:abc", "ledger");
        }
        assertThat(limiter.check("t:abc", "ledger").allowed()).isFalse();

        advance(Duration.ofSeconds(1));
        assertThat(limiter.check("t:abc", "ledger").allowed()).isTrue();
        assertThat(limiter.check("t:abc", "ledger").allowed()).isFalse();
    }

    @Test
    void doesNotPermitDoubleTheRateAcrossAWindowBoundary() {
        // THE CASE A FIXED-WINDOW COUNTER GETS WRONG. Spend the whole allowance at the end of one
        // window, step just past the boundary, and try to spend it all again. A counter that resets
        // on the boundary would allow 20 requests in a hair over a second; the bucket allows the
        // 10 it has refilled and no more.
        RateLimiter limiter = limiterOf(10);

        advance(MINUTE.minusMillis(1));
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.check("t:abc", "ledger").allowed()).isTrue();
        }

        advance(Duration.ofMillis(2));   // now just inside the next window
        assertThat(limiter.check("t:abc", "ledger").allowed())
                .as("a fixed-window counter would have reset here").isFalse();
    }

    @Test
    void theBucketMapIsBoundedAndEvictsTheLeastRecentlyUsed() {
        RateLimitProperties props = new RateLimitProperties(true,
                new RateLimitProperties.Bucket(1, MINUTE), Map.of(), 2);
        RateLimiter limiter = new RateLimiter(props, now::get);

        limiter.check("t:oldest", "ledger");     // spends its only token
        advance(Duration.ofSeconds(1));
        limiter.check("t:middle", "ledger");
        advance(Duration.ofSeconds(1));
        limiter.check("t:newest", "ledger");     // map is full, oldest is evicted here

        // The evicted caller starts again with a full bucket. That is the right thing to lose: a
        // caller not seen in a while would have refilled anyway. The one still tracked does not.
        assertThat(limiter.check("t:oldest", "ledger").allowed()).isTrue();
        assertThat(limiter.check("t:newest", "ledger").allowed()).isFalse();
    }

    @Test
    void aBucketConfiguredToZeroRefusesToStart() {
        // Zero reads like "no limit" and behaves like "refuse everything" — the most expensive way
        // for a control to be misconfigured, because the service stays up and healthy.
        assertThatThrownBy(() -> new RateLimitProperties.Bucket(0, MINUTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled=false");
        assertThatThrownBy(() -> new RateLimitProperties.Bucket(10, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive duration");
    }

    @Test
    void absentConfigurationFallsBackToSafeDefaults() {
        RateLimitProperties props = new RateLimitProperties(true, null, null, 0);

        assertThat(props.defaults().requests()).isEqualTo(60);
        assertThat(props.defaults().window()).isEqualTo(MINUTE);
        assertThat(props.maxTrackedCallers()).isEqualTo(10_000);
        assertThat(props.forEndpoint("anything")).isEqualTo(props.defaults());
    }

    @Test
    void aNamedEndpointOverridesTheDefaultBucket() {
        RateLimitProperties.Bucket tight = new RateLimitProperties.Bucket(2, MINUTE);
        RateLimitProperties props = new RateLimitProperties(true,
                new RateLimitProperties.Bucket(60, MINUTE), Map.of("ledger", tight), 100);

        assertThat(props.forEndpoint("ledger")).isEqualTo(tight);
        assertThat(props.forEndpoint("virtual_debit_report")).isEqualTo(props.defaults());
    }

    @Test
    void disabledMeansEveryRequestPasses() {
        RateLimitProperties off = new RateLimitProperties(false,
                new RateLimitProperties.Bucket(1, MINUTE), Map.of(), 100);
        RateLimiter limiter = new RateLimiter(off, now::get);

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.check("t:abc", "ledger").allowed()).isTrue();
        }
        assertThat(limiter.rejectedCount()).isZero();
    }
}
