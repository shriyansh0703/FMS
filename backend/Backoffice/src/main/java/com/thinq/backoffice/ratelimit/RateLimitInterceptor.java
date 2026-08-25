package com.thinq.backoffice.ratelimit;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.databind.ObjectMapper;

import com.thinq.backoffice.platform.ApiError;
import com.thinq.backoffice.platform.VendorGateway;

/**
 * Applies {@link RateLimiter} to every gateway route, and refuses with <b>429</b>.
 *
 * <p><b>WHY 429 AND NOT THE VENDOR'S HTTP 200 ENVELOPE.</b> Everywhere else this gateway answers
 * 200 even for a rejection, because that is TechExcel's contract and a caller must not learn to
 * trust the status line. This is not TechExcel's verdict — it is ours, about our own capacity,
 * exactly like the 404 for an unknown route and the 502 for an unreachable upstream. Dressing it up
 * as a vendor rejection would tell a caller the back office refused their data, which is false and
 * sends them to debug the wrong system. The body still carries the familiar envelope shape so
 * nothing has to parse two formats.
 *
 * <p><b>WHO THE CALLER IS.</b> In practice, the remote address. This API issues no credential and
 * requires none, so most callers arrive with no identity of their own and the address is all there
 * is. A bearer token is still honoured as the key when one is present, so that an internal caller
 * acting as itself is bucketed separately if caller identity is ever introduced.
 *
 * <p>THE CEILING THAT FOLLOWS FROM THAT: address-keyed buckets treat everything behind one NAT or
 * one ingress as a single caller. That is the correct conservative direction — stricter, never
 * more permissive — but it means the limiter protects the back office rather than fairly
 * apportioning capacity between callers. Per-caller fairness needs caller identity first.
 *
 * <p><b>THE TOKEN IS NEVER STORED OR LOGGED.</b> Only its SHA-256 prefix is used as a map key, so a
 * heap dump of the bucket map is not a list of live credentials.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    RateLimitInterceptor(RateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**", VendorGateway.PREFIX + "/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        RateLimiter.Verdict verdict = limiter.check(caller(request), endpoint(request));
        if (verdict.allowed()) {
            return true;
        }

        long retryAfter = verdict.retryAfterSeconds();
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        // The limit a caller is being held to, so they can size their own pacing rather than
        // discovering it by being refused repeatedly.
        response.setHeader("X-RateLimit-Limit", String.valueOf(verdict.limit()));
        response.setHeader("X-RateLimit-Window", verdict.window().toString());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        byte[] body = objectMapper.writeValueAsBytes(ApiError.envelope("Rate_Limited",
                "Too many requests for this endpoint. Retry after " + retryAfter
                        + "s. This is THIS GATEWAY's limit, not TechExcel's — the back office was"
                        + " not called. Configure it with backoffice.ratelimit.*"));
        try {
            response.getOutputStream().write(body);
        } catch (java.io.IOException e) {
            // The client hung up mid-refusal. Nothing to recover, and nothing to say to them.
            return false;
        }
        return false;
    }

    /** The bearer token's digest when present, else the remote address. Never the token itself. */
    static String caller(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = auth.substring(7).trim();
            if (!token.isEmpty()) {
                return "t:" + sha256(token);
            }
        }
        String remote = request.getRemoteAddr();
        return "ip:" + (remote == null ? "unknown" : remote);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM. If it is genuinely absent, bucketing every caller
            // together is the safe direction to fail: stricter, never more permissive.
            return "no-digest";
        }
    }

    /**
     * The endpoint's last path segment, so {@code /api/entry/ledger} and
     * {@code /TechBoRest/api/entry/ledger} share one bucket. Alternating between the two prefixes
     * would otherwise hand a caller double the configured allowance.
     */
    static String endpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        int slash = path.lastIndexOf('/');
        return slash < 0 || slash == path.length() - 1 ? path : path.substring(slash + 1);
    }
}
