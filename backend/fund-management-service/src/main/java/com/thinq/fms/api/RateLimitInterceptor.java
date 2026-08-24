package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import com.thinq.fms.platform.money.AccountRef;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;
import java.util.Objects;

/**
 * Applies {@link PerAccountRateLimit} to every API request (Stage 11, HIGH-1).
 *
 * <p><b>An interceptor rather than an annotation on each method.</b> A per-method guard protects the
 * endpoints somebody remembered to annotate; this protects everything under {@code /api/**},
 * including endpoints not yet written. Six of the thirteen planned endpoints do not exist yet, and
 * the point of putting the control here is that they arrive protected.
 *
 * <p>The classification is by path and method rather than by a marker on the handler, for the same
 * reason: a new write endpoint is treated as money movement by default, and has to be deliberately
 * reclassified downward rather than deliberately protected upward.
 */
public final class RateLimitInterceptor implements HandlerInterceptor {

    private final PerAccountRateLimit limits;
    private final ObjectMapper json;

    public RateLimitInterceptor(PerAccountRateLimit limits, ObjectMapper json) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Count the request once, not once per dispatch. StreamingResponseBody puts the statement
        // export through an ASYNC dispatch, and preHandle runs again on it — which spent two permits
        // per call and gave that endpoint half its configured budget. Found by running the service
        // and counting: the export refused after 3 requests against a budget of 6, while a plain GET
        // consumed one permit each. Nothing in the test suite exercised an async dispatch.
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            return true;
        }

        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            // Unauthenticated requests never reach here — the filter chain refuses them first. If
            // one does, it is not this component's job to invent an identity to meter.
            return true;
        }

        AccountRef account = AuthenticatedAccount.of(principal);
        if (this.limits.permit(account, classify(request))) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // No Retry-After with a precise figure: it would tell a caller exactly how to pace an
        // attack against the limit. The status already says to slow down.
        this.json.writeValue(response.getWriter(), ErrorResponse.of(
                "rate_limited", "too many requests; please retry shortly"));
        return false;
    }

    /**
     * Which budget this request spends.
     *
     * <p>Anything that is not a plain GET is money movement. That is the safe default: a write
     * endpoint added later is metered tightly until somebody decides otherwise, rather than
     * unmetered until somebody notices.
     */
    static PerAccountRateLimit.Operation classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith(".csv")) {
            return PerAccountRateLimit.Operation.EXPORT;
        }
        return "GET".equalsIgnoreCase(request.getMethod())
                ? PerAccountRateLimit.Operation.READ
                : PerAccountRateLimit.Operation.MOVEMENT;
    }
}
