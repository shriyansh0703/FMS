package com.thinq.fms.api;

import com.thinq.fms.platform.money.AccountRef;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The rate limit itself (Stage 11, HIGH-1). */
class PerAccountRateLimitTest {

    private static final AccountRef JYOTHI = AccountRef.of("JYOTHI01");
    private static final AccountRef ARUN = AccountRef.of("ARUN0002");

    private static RateLimiterConfig allowing(int permits) {
        return RateLimiterConfig.custom()
                .limitForPeriod(permits)
                .limitRefreshPeriod(Duration.ofMinutes(5))
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    private PerAccountRateLimit limitOf(int permits) {
        return new PerAccountRateLimit(allowing(permits), allowing(permits), allowing(permits));
    }

    @Test
    @DisplayName("a caller is refused once the budget is spent")
    void aCallerIsRefusedOnceTheBudgetIsSpent() {
        PerAccountRateLimit limit = limitOf(3);

        for (int i = 0; i < 3; i++) {
            assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ))
                    .as("permit %d of 3", i + 1).isTrue();
        }
        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ)).isFalse();
    }

    @Test
    @DisplayName("one account exhausting its budget does not affect another")
    void oneAccountDoesNotAffectAnother() {
        // The reason the limit is per account rather than global. A global limit on a multi-tenant
        // money API is a denial-of-service primitive: one trader locks out everyone else.
        PerAccountRateLimit limit = limitOf(2);

        limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ);
        limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ);
        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ)).isFalse();

        assertThat(limit.permit(ARUN, PerAccountRateLimit.Operation.READ))
                .as("a different trader has their own budget").isTrue();
    }

    @Test
    @DisplayName("the budgets are separate, so reads cannot exhaust the money-movement allowance")
    void theBudgetsAreSeparate() {
        PerAccountRateLimit limit = limitOf(1);

        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ)).isTrue();
        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.READ)).isFalse();

        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.MOVEMENT))
                .as("spending the read budget must not close the withdrawal path").isTrue();
        assertThat(limit.permit(JYOTHI, PerAccountRateLimit.Operation.EXPORT)).isTrue();
    }

    @Test
    @DisplayName("the shipped budgets are tighter for money movement than for reads")
    void theShippedBudgetsAreOrdered() {
        // The ordering is the policy: reads are generous because they are cheap and legitimate;
        // exports reach a vendor; movement is tight because Rule W4 permits only one open
        // withdrawal anyway, so a caller repeating it is confused or probing.
        assertThat(PerAccountRateLimit.READS_PER_INSTANCE.getLimitForPeriod())
                .isGreaterThan(PerAccountRateLimit.EXPORTS_PER_INSTANCE.getLimitForPeriod());
        assertThat(PerAccountRateLimit.MOVEMENTS_PER_INSTANCE.getLimitForPeriod())
                .isLessThan(PerAccountRateLimit.READS_PER_INSTANCE.getLimitForPeriod());
    }

    @Test
    @DisplayName("no budget blocks the caller, because a blocking limiter holds request threads")
    void noBudgetBlocks() {
        // A rate limiter that waits converts a burst into held threads, which is the resource
        // exhaustion it was added to prevent.
        for (RateLimiterConfig config : new RateLimiterConfig[]{
                PerAccountRateLimit.READS_PER_INSTANCE, PerAccountRateLimit.EXPORTS_PER_INSTANCE, PerAccountRateLimit.MOVEMENTS_PER_INSTANCE}) {
            assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ZERO);
        }
    }

    @Test
    @DisplayName("a re-dispatch of the same request does not spend a second permit")
    void aReDispatchDoesNotSpendASecondPermit() throws Exception {
        // The statement export returns a StreamingResponseBody, which Spring completes on an ASYNC
        // dispatch — and preHandle runs again on it. That spent two permits per call and halved the
        // export budget. Found by running the service and counting, not by any test, so this is the
        // test that would have found it.
        var limits = new PerAccountRateLimit(allowing(1), allowing(1), allowing(1));
        var interceptor = new RateLimitInterceptor(limits, new tools.jackson.databind.ObjectMapper());

        var first = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/funds/statement.csv");
        first.setUserPrincipal(() -> "JYOTHI01");
        assertThat(interceptor.preHandle(first, new org.springframework.mock.web.MockHttpServletResponse(), null))
                .as("the initial dispatch spends the only permit").isTrue();

        var async = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/funds/statement.csv");
        async.setUserPrincipal(() -> "JYOTHI01");
        async.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        assertThat(interceptor.preHandle(async, new org.springframework.mock.web.MockHttpServletResponse(), null))
                .as("the async completion of the same request must not be charged again").isTrue();
    }

    @Test
    @DisplayName("a write is classified as money movement by default")
    void aWriteIsMoneyMovementByDefault() {
        // A new write endpoint is metered tightly until somebody decides otherwise, rather than
        // unmetered until somebody notices.
        var post = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/funds/anything");
        assertThat(RateLimitInterceptor.classify(post))
                .isEqualTo(PerAccountRateLimit.Operation.MOVEMENT);

        var get = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/funds/transactions");
        assertThat(RateLimitInterceptor.classify(get)).isEqualTo(PerAccountRateLimit.Operation.READ);

        var csv = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/funds/statement.csv");
        assertThat(RateLimitInterceptor.classify(csv)).isEqualTo(PerAccountRateLimit.Operation.EXPORT);
    }
}
