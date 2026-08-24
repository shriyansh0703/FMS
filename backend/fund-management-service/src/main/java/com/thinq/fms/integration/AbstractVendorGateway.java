package com.thinq.fms.integration;

import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.FmsUpstreamException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.Money;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The anti-corruption layer every vendor call goes through.
 *
 * <p>Four vendors speak four different error vocabularies, and this system integrates with
 * all of them. Without a single boundary, TechExcel's {@code Input_Value_Validation},
 * Juspay's reason codes and Noren's envelope shapes would leak into orchestrators that have
 * no business knowing which back office is on the other end.
 *
 * <p>This class owns four things, and deliberately no more:
 *
 * <ol>
 *   <li><b>Timeout</b> — no vendor call blocks a request thread indefinitely.
 *   <li><b>Circuit breaker</b> — a vendor that is failing is not hammered while it recovers.
 *   <li><b>Paise conversion, in both directions</b> — decimal rupees become {@link Money} on
 *       ingest and go back out as a two-place decimal, exactly once, here. No service and no
 *       renderer converts money.
 *   <li><b>Error translation</b> — a vendor's exception becomes this system's, so nothing
 *       above this layer catches a vendor type.
 * </ol>
 *
 * <p>It does not own retries. A retry policy differs per call — re-reading a ledger is safe,
 * re-issuing a payout instruction is not — so it belongs with the caller that knows which
 * kind it is making. A blanket retry here would silently re-instruct payments.
 */
public abstract class AbstractVendorGateway {

    /** Lowercase words and underscores, up to 48 characters. No digits, so no interpolated id. */
    private static final java.util.regex.Pattern OPERATION_NAME =
            java.util.regex.Pattern.compile("[a-z][a-z_]{0,47}");

    private final String vendorName;
    private final Duration callTimeout;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meters;

    protected AbstractVendorGateway(String vendorName,
                                    Duration callTimeout,
                                    CircuitBreaker circuitBreaker,
                                    MeterRegistry meters) {
        this.vendorName = Objects.requireNonNull(vendorName, "vendorName");
        this.callTimeout = Objects.requireNonNull(callTimeout, "callTimeout");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.meters = Objects.requireNonNull(meters, "meters");
    }

    /**
     * Run one vendor call with this gateway's timeout, breaker, metrics and error
     * translation.
     *
     * @param operation a short, stable name for the call — used as a metric tag, so it must
     *     not carry an account identifier or any other high-cardinality value. Enforced by
     *     {@link #requireLowCardinality}, because a documented rule on a metric tag is one
     *     that gets broken by a caller who never read the Javadoc
     * @throws VendorUnavailableException if the call times out, the breaker is open, or the
     *     transport fails
     * @throws FmsException unchanged, if the subclass's translation already produced one —
     *     a translated domain error must not be re-wrapped as an outage
     */
    protected <T> T call(String operation, Callable<T> vendorCall) {
        requireLowCardinality(operation);
        Timer.Sample sample = Timer.start(this.meters);
        String outcome = "success";

        try {
            return this.circuitBreaker.executeCallable(() -> withTimeout(operation, vendorCall));
        } catch (CallNotPermittedException e) {
            outcome = "circuit_open";
            throw new VendorUnavailableException(this.vendorName,
                    this.vendorName + " circuit is open for " + operation, e);
        } catch (FmsUpstreamException e) {
            // An outage that already reached this layer in our vocabulary — the timeout
            // below throws one. It must be counted as an outage: filing it under the
            // FmsException branch tagged it `domain_error`, which inverted the one signal
            // `fms.vendor.call{outcome}` exists to produce. A vendor going dark showed up
            // as a rise in business errors.
            outcome = "upstream_unavailable";
            throw e;
        } catch (FmsException e) {
            // Already ours — a subclass translated it. Re-wrapping would turn a business
            // outcome into an outage and lose the reason the caller needs.
            outcome = "domain_error";
            throw e;
        } catch (Exception e) {
            outcome = "failure";
            throw translate(operation, e);
        } finally {
            sample.stop(Timer.builder("fms.vendor.call")
                    .tag("vendor", this.vendorName)
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(this.meters));
        }
    }

    /**
     * Run one call, giving up the <i>wait</i> after {@code callTimeout}.
     *
     * <p><b>A timeout abandons the wait, not the call.</b> The worker is interrupted, but
     * interruption only stops work that polls the flag or blocks on an interruptible
     * operation — a socket read on a classic blocking {@code Socket}, which several common
     * HTTP clients use, ignores it and runs to completion. So after this method throws, the
     * vendor may still receive, accept and act on the request.
     *
     * <p>That is not a hole; it is the reason two other decisions exist, and removing either
     * would open one. The end-of-day run reads payment status before reissuing an
     * instruction (lld-backend.md §6.3), and this class owns no retries. <b>A timeout is
     * never sufficient grounds to reissue a payout.</b>
     *
     * <p>The worker is a virtual thread because these calls are I/O-bound and Java 21 removes
     * the pool-sizing tradeoff. One may briefly outlive this method; it completes a future
     * nobody reads and then exits. To bound that, configure the HTTP client's own socket and
     * response timeouts below {@code callTimeout} rather than relying on interruption.
     */
    private <T> T withTimeout(String operation, Callable<T> vendorCall) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().name("vendor-" + this.vendorName + "-" + operation).start(() -> {
            try {
                future.complete(vendorCall.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(this.callTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            worker.interrupt();
            throw new VendorUnavailableException(this.vendorName,
                    this.vendorName + " did not answer " + operation + " within " + this.callTimeout, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException("vendor call failed with a non-Exception throwable", cause);
        }
    }

    /**
     * Translate a vendor-specific failure into this system's vocabulary.
     *
     * <p>Subclasses override this to map their own error codes — TechExcel's
     * {@code Input_Value_Validation}, Juspay's reason codes — onto domain exceptions. The
     * default treats anything unrecognised as an outage, which is the safe reading: an
     * unrecognised response is one this system does not understand, and guessing what it
     * meant is how a rejection gets read as a success.
     */
    protected FmsException translate(String operation, Exception e) {
        return new VendorUnavailableException(this.vendorName,
                this.vendorName + " failed " + operation, e);
    }

    // ---- Paise conversion. Both directions, exactly here. ----

    /**
     * A vendor's decimal rupees, as paise.
     *
     * <p>Takes a {@code String} because {@code new BigDecimal(0.1)} captures binary
     * floating-point imprecision and {@code new BigDecimal("0.1")} does not. There is no
     * overload taking a {@code double}, and there must never be one.
     */
    protected Money toPaise(String vendorDecimalRupees) {
        return Money.ofVendorDecimal(vendorDecimalRupees);
    }

    /** A vendor's numeric rupees, as paise. */
    protected Money toPaise(BigDecimal vendorDecimalRupees) {
        return Money.ofVendorDecimal(vendorDecimalRupees);
    }

    /**
     * Paise as the two-place decimal a vendor field expects — TechExcel's {@code Amount} is
     * typed {@code String} with precision 20,2.
     */
    protected String toVendorAmount(Money amount) {
        return amount.toVendorDecimal().toPlainString();
    }

    /**
     * Refuse an operation name that would create one time series per account.
     *
     * <p>A high-cardinality metric tag is a slow, expensive failure: it does not throw, it does
     * not appear in a test, and it surfaces weeks later as a metrics bill and a backend that has
     * stopped ingesting. Rejecting the shape here turns it into a failure at the first call.
     *
     * <p>Deliberately a shape check rather than an allow-list. An allow-list would have to be
     * updated for every new call and would be the thing a hurried caller edits, whereas the rule
     * "short, lowercase, no digits" admits every legitimate operation name in this codebase and
     * excludes anything with an identifier interpolated into it.
     */
    static void requireLowCardinality(String operation) {
        Objects.requireNonNull(operation, "operation");
        if (!OPERATION_NAME.matcher(operation).matches()) {
            throw new IllegalArgumentException(
                    "vendor operation names are metric tags and must be short, stable and free of "
                            + "identifiers; got '" + operation + "'");
        }
    }

    protected String vendorName() {
        return this.vendorName;
    }
}
