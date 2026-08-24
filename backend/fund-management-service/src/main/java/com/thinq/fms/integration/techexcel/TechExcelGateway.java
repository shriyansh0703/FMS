package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.AbstractVendorGateway;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.VendorHttpException;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The shared TechExcel call path: session token, one retry on an expired token, and TechExcel's
 * error vocabulary translated into this system's.
 *
 * <p><b>The token retry is not a retry policy.</b> {@link AbstractVendorGateway} deliberately
 * owns no retries, because re-reading a ledger is safe and re-issuing a payout is not. What
 * happens here is narrower and safe on both: TechExcel reports an expired session on the call
 * that discovers it, so a single re-attempt after logging in is the difference between a
 * working integration and one that fails once per token lifetime. It re-attempts <b>only</b> on
 * {@code Token Validation Expired} or {@code Token Validation Missing}, and at most once.
 *
 * <p>Any other failure propagates. In particular {@code Input_Value_Validation} never triggers a
 * re-attempt, because on the payout endpoint that code also means "duplicate" and re-sending
 * would be the double-payout this design exists to prevent.
 */
public abstract class TechExcelGateway extends AbstractVendorGateway {

    protected static final String VENDOR = "techexcel";

    private final JsonHttp http;
    private final TechExcelSession session;
    private final String companyCode;

    protected TechExcelGateway(JsonHttp http,
                               TechExcelSession session,
                               String companyCode,
                               Duration callTimeout,
                               CircuitBreaker circuitBreaker,
                               MeterRegistry meters) {
        super(VENDOR, callTimeout, circuitBreaker, meters);
        this.http = Objects.requireNonNull(http, "http");
        this.session = Objects.requireNonNull(session, "session");
        this.companyCode = Objects.requireNonNull(companyCode, "companyCode");
    }

    /**
     * POST to a TechExcel endpoint with a valid session, re-attempting once if the session had
     * expired.
     *
     * @throws FmsException translated from TechExcel's vocabulary. Never returns a response that
     *     carried an error code.
     */
    protected JsonNode postAuthenticated(String path, Map<String, Object> body) throws Exception {
        String tokenUsed = this.session.token();
        JsonNode response;
        try {
            response = this.http.post(path, body, tokenHeader(tokenUsed));
        } catch (VendorHttpException e) {
            // A 401/403 is the transport saying the same thing the body would have said.
            if (e.status() == 401 || e.status() == 403) {
                // No invalidate() here. refreshIfStale already handles a stale token, and it
                // coalesces concurrent callers onto one login. Nulling the field first defeats
                // that: a thread arriving after another has published a fresh token would discard
                // it and log in again — turning one login into one per in-flight call, at the
                // moment TechExcel is already refusing them.
                String fresh = this.session.refreshIfStale(tokenUsed);
                response = this.http.post(path, body, tokenHeader(fresh));
                return checkForError(path, response);
            }
            throw e;
        }

        // Null means the response carried no error code at all, which is what success looks like.
        // Testing isSessionProblem() first threw a NullPointerException on EVERY successful call,
        // and AbstractVendorGateway then reported it as a vendor outage — so the integration
        // failed completely while the metrics and logs blamed the back office.
        TechExcelErrorCode code = errorCodeOf(response);
        if (code != null && code.isSessionProblem()) {
            String fresh = this.session.refreshIfStale(tokenUsed);
            // A session error on this second response is a real failure rather than another
            // chance: looping would hammer login while TechExcel refuses the credentials.
            // checkForError translates it like any other error code, which is what should
            // happen — the caller learns the session could not be established.
            response = this.http.post(path, body, tokenHeader(fresh));
            return checkForError(path, response);
        }
        return checkForError(path, response);
    }

    private static Map<String, String> tokenHeader(String token) {
        return Map.of("Token", token);
    }

    /**
     * The company code every entry endpoint requires, folded into a request body.
     *
     * <p>A {@link LinkedHashMap} rather than {@code Map.of} because TechExcel's optional fields
     * are frequently null and {@code Map.of} rejects null values — and a body that silently
     * dropped a null field would send a different request than the caller wrote.
     */
    protected Map<String, Object> body(Map<String, Object> fields) {
        Map<String, Object> b = new LinkedHashMap<>(fields);
        b.putIfAbsent("company_code", this.companyCode);
        return b;
    }

    /** The error code a response carries, or null when it carries none. */
    protected static TechExcelErrorCode errorCodeOf(JsonNode response) {
        JsonNode node = response.get("ErrorCode");
        if (node == null) {
            node = response.get("Error_Code");
        }
        if (node == null || node.isNull()) {
            return null;
        }
        return TechExcelErrorCode.fromWire(node.asString());
    }

    /**
     * Translate any error code the response carries, or return the response unchanged.
     *
     * <p>Called after the session re-attempt has already happened, so a session error reaching
     * here is terminal and is translated like any other — the caller learns the session could
     * not be established rather than the call silently looping.
     */
    private JsonNode checkForError(String path, JsonNode response) {
        TechExcelErrorCode code = errorCodeOf(response);
        if (code == null) {
            return response;
        }
        throw translateErrorCode(path, code, response);
    }

    /**
     * Map a TechExcel error code onto this system's vocabulary.
     *
     * <p>Overridden by the payout rail, which must give {@code Input_Value_Validation} a
     * different treatment from every other endpoint — there it is ambiguous between a rejection
     * and a duplicate, and that ambiguity is load-bearing.
     */
    protected FmsException translateErrorCode(String path, TechExcelErrorCode code, JsonNode response) {
        return switch (code) {
            case DATABASE_EXCEPTION, TOKEN_EXPIRED, TOKEN_MISSING, UNRECOGNISED ->
                    new VendorUnavailableException(VENDOR,
                            "TechExcel " + path + " failed with " + code);
            case INPUT_VALIDATION, INPUT_VALUE_VALIDATION, SYSTEM_CHARACTER_FILTER ->
                    new FmsInvariantException("techexcel_request_rejected",
                            "TechExcel rejected a request this system constructed: " + path + " -> " + code);
        };
    }

    @Override
    protected FmsException translate(String operation, Exception e) {
        if (e instanceof VendorHttpException http) {
            return new VendorUnavailableException(VENDOR,
                    "TechExcel " + operation + " returned HTTP " + http.status(), http);
        }
        return super.translate(operation, e);
    }

    protected JsonHttp http() {
        return this.http;
    }

    protected TechExcelSession session() {
        return this.session;
    }

    protected String companyCode() {
        return this.companyCode;
    }
}
