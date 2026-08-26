package com.thinq.backoffice.platform;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.thinq.backoffice.scheduler.TokenRefresher;

/**
 * THE ONE PLACE A CALL LEAVES THIS PROCESS, and the one place that decides whether it should.
 *
 * <p>Every category package — trading, funds, brokerage, accounting, positions — asks the same two
 * questions of every request: <em>are we live?</em> and, if so, <em>relay this upstream
 * unchanged</em>. Both answers live here rather than in each controller, because the alternative is
 * one copy of {@code if (props.live())} per endpoint and the certainty that the next one forgets
 * it. An endpoint that quietly answered from the mock while the banner said LIVE is the worst
 * failure this service has: generated rows, presented as a broker's real books.
 *
 * <p>THE PREFIX IS STRIPPED BEFORE FORWARDING. TechExcel serves at {@code /TechBoRest/api/...} and
 * this gateway answers on both that and the bare {@code /api/...}, so the prefix is removed here
 * and re-added by the configured base URL — otherwise a call arriving on the bare path would reach
 * upstream without it.
 *
 * <p>SWITCHING TO THE REAL BACK OFFICE IS TWO PROPERTIES, NOT A CODE CHANGE:
 * {@code techexcel.live=true} and {@code techexcel.base-url}. Nothing above this class knows which
 * mode it is in.
 */
@Component
public class VendorGateway {

    /** TechExcel's own path prefix, which this gateway answers on as well as on the bare path. */
    public static final String PREFIX = "/TechBoRest";

    private final GatewayProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    /** Present only in managed auth mode; empty in the default pass-through. */
    private final Optional<TokenRefresher> tokens;

    VendorGateway(GatewayProperties props, RestClient.Builder restClientBuilder,
                  ObjectMapper objectMapper, Optional<TokenRefresher> tokens) {
        // LIVE MODE REQUIRES MANAGED AUTH, and this is where that becomes an invariant rather
        // than a convention. Callers cannot supply a TechExcel token any more — there is no login
        // route for them to get one from — so if this process is not holding a managed token
        // either, every upstream call goes out unauthenticated and comes back Token Missing. That
        // is a broken deployment reporting itself as a back-office fault, so refuse to start.
        if (props.live() && tokens.isEmpty()) {
            throw new IllegalStateException(
                    "techexcel.live=true requires techexcel.auth.mode=managed. This service "
                            + "authenticates to TechExcel on its own behalf; callers have no way to "
                            + "present a token, so a live gateway with no managed credential can "
                            + "only produce Token Missing on every call.");
        }
        this.props = props;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tokens = tokens;
    }

    /** Whether calls reach the real back office. Read per request, never cached by a caller. */
    public boolean live() {
        return props.live();
    }

    public URI baseUrl() {
        return props.baseUrl();
    }

    /**
     * Relay the call upstream unchanged, and the answer back unchanged.
     *
     * <p>Live mode forwards anything under {@code /api}, not only the endpoints this service holds
     * documents for — TechExcel serves far more, and a pass-through that quietly drops the rest is
     * a worse lie than a 404. That wider catch-all lives in {@link UnmappedApiController}.
     */
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, String body) {
        String path = request.getRequestURI();
        if (path.startsWith(PREFIX)) {
            path = path.substring(PREFIX.length());
        }
        String query = request.getQueryString();
        URI target = URI.create(props.baseUrl() + path + (query == null ? "" : "?" + query));

        try {
            RestClient.RequestBodySpec spec = restClient
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(target);
            for (String header : List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                    HttpHeaders.AUTHORIZATION)) {
                String value = request.getHeader(header);
                if (value != null) {
                    spec = spec.header(header, value);
                }
            }
            // A caller's own token always wins. The managed token fills in only where there was
            // none — so turning managed mode on never silently changes who an authenticated caller
            // is acting as.
            if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                Optional<String> managed = tokens.flatMap(TokenRefresher::current);
                if (managed.isPresent()) {
                    spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + managed.get());
                }
            }
            return spec.body(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> { })   // relay any status verbatim
                    .toEntity(byte[].class);
        } catch (ResourceAccessException e) {
            // 502, not 200. An unreachable back office is OUR failure, not TechExcel's verdict, and
            // a caller must be able to tell them apart. The message names the class and host only,
            // never a token.
            return jsonError(ApiError.envelope("Database_Exception",
                    "Upstream TechExcel unreachable at " + props.baseUrl()
                            + " (" + e.getClass().getSimpleName() + ")"), 502);
        }
    }

    public ResponseEntity<byte[]> jsonError(Map<String, Object> envelope, int status) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsBytes(envelope));
    }

    /**
     * The request body, as an object.
     *
     * <p>All three rejections are the vendor's envelope with HTTP 200, not Spring's 400 — a caller
     * that branches on {@code Error Code} everywhere else in this API cannot suddenly be handed an
     * error page here.
     */
    public Map<String, Object> asObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiError("Input_Validation", "Request body must not be empty.");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (JacksonException e) {
            // Jackson 3 throws unchecked; the caller still gets TechExcel's envelope.
            throw new ApiError("Input_Validation", "Malformed JSON: " + e.getMessage());
        }
        if (!node.isObject()) {
            throw new ApiError("Input_Validation", "Request body must be a JSON object.");
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() { });
    }
}
