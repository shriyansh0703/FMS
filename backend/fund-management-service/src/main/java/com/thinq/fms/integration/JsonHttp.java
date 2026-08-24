package com.thinq.fms.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * The JSON-over-HTTP transport the REST vendors share.
 *
 * <p>Three of this system's four vendors speak JSON over HTTP — TechExcel, Juspay, the
 * Communication Service — plus Profile. One small helper beats four near-identical copies, and
 * it is deliberately small: it does not retry, does not time out on its own, and does not
 * interpret status codes into domain meaning. Those belong to
 * {@link AbstractVendorGateway#call} and to each gateway's {@code translate}, and duplicating
 * them here would put the same decision in two places.
 *
 * <p>Built on the JDK's own {@code HttpClient} rather than a library. Java 21 has one, it
 * supports the timeout and connection reuse these calls need, and adding a client dependency to
 * a money service buys nothing but another thing to patch.
 *
 * <p><b>The fourth vendor is not here.</b> Kambala Noren is a C++ request/response protocol with
 * {@code Start}/{@code Response}/{@code End} envelopes, not REST (hld.md §7). It cannot use this.
 */
public final class JsonHttp {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI baseUri;

    public JsonHttp(URI baseUri, Duration connectTimeout, ObjectMapper mapper) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                // No follow-redirects: a payment endpoint that redirects is a misconfiguration
                // or an interception, and silently following it would re-send an instruction
                // to somewhere nobody reviewed.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * POST a JSON body and parse the JSON response.
     *
     * <p>No request timeout is set here on purpose: {@code AbstractVendorGateway} bounds the
     * wait, and two competing timeouts would make the effective one whichever is shorter,
     * decided by accident rather than by configuration.
     *
     * @throws VendorHttpException on any non-2xx status, carrying the status and body so the
     *     gateway's {@code translate} can map it. Deliberately not turned into a domain
     *     exception here — this class does not know what a 400 means to TechExcel.
     */
    public JsonNode post(String path, Object body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(this.baseUri.resolve(path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(this.mapper.writeValueAsString(body)));
        headers.forEach(b::header);
        return send(b.build(), path);
    }

    /** GET and parse the JSON response. Same contract as {@link #post}. */
    public JsonNode get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(this.baseUri.resolve(path))
                .header("Accept", "application/json")
                .GET();
        headers.forEach(b::header);
        return send(b.build(), path);
    }

    private JsonNode send(HttpRequest request, String path) throws Exception {
        HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new VendorHttpException(status, path, response.body());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            // An empty 200 from a money API is not success, it is an answer this system cannot
            // read. Treating it as an empty result would let a payout status of "unknown" be
            // read as "nothing was sent".
            throw new VendorHttpException(status, path, "empty body on a 2xx response");
        }
        return this.mapper.readTree(body);
    }

    public ObjectMapper mapper() {
        return this.mapper;
    }
}
