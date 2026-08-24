package com.thinq.fms.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared vendor transport.
 *
 * <p>Two of its decisions are deliberate and would otherwise look like omissions to someone
 * tidying up: it refuses to follow redirects, and it treats an empty body on a 2xx as a failure.
 * Both are tested here so the reasoning survives the next person who reads the class.
 */
class JsonHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    @Test
    @DisplayName("a redirect is refused rather than followed")
    void redirectIsNotFollowed() throws Exception {
        // A payment endpoint that redirects is a misconfiguration or an interception. Following
        // it would re-send an instruction to a host nobody reviewed, carrying the auth header.
        AtomicInteger elsewhereHits = new AtomicInteger();
        serve(exchange -> {
            if (exchange.getRequestURI().getPath().startsWith("/elsewhere")) {
                elsewhereHits.incrementAndGet();
                respond(exchange, 200, "{\"ok\":true}");
                return;
            }
            exchange.getResponseHeaders().add("Location", "/elsewhere");
            respond(exchange, 302, "");
        });

        assertThatThrownBy(() -> client().post("/orders", Map.of("a", 1), Map.of()))
                .isInstanceOf(VendorHttpException.class)
                .satisfies(e -> assertThat(((VendorHttpException) e).status()).isEqualTo(302));

        assertThat(elsewhereHits).hasValue(0);
    }

    @Test
    @DisplayName("an empty body on a 2xx is a failure, not an empty result")
    void emptyBodyOnSuccessIsAFailure() throws Exception {
        // An empty 200 from a money API is not success, it is an answer this system cannot read.
        // Returning an empty node would let a payout status of "unknown" be read as "nothing was
        // sent", which is the reading that double-pays a trader.
        serve(exchange -> respond(exchange, 200, ""));

        assertThatThrownBy(() -> client().get("/status", Map.of()))
                .isInstanceOf(VendorHttpException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("a non-2xx carries its status and body for the gateway to translate")
    void nonSuccessCarriesStatusAndBody() throws Exception {
        serve(exchange -> respond(exchange, 422, "{\"error_code\":\"invalid_amount\"}"));

        assertThatThrownBy(() -> client().post("/orders", Map.of(), Map.of()))
                .isInstanceOf(VendorHttpException.class)
                .satisfies(e -> {
                    VendorHttpException v = (VendorHttpException) e;
                    assertThat(v.status()).isEqualTo(422);
                    // Retained for error mapping and logging. Never rendered to a trader.
                    assertThat(v.body()).contains("invalid_amount");
                    assertThat(v.isTransient()).as("422 is not worth retrying").isFalse();
                });
    }

    @Test
    @DisplayName("429 and 5xx are marked transient; 4xx is not")
    void transienceIsClassifiedByStatus() {
        assertThat(new VendorHttpException(429, "/x", "").isTransient()).isTrue();
        assertThat(new VendorHttpException(503, "/x", "").isTransient()).isTrue();
        assertThat(new VendorHttpException(400, "/x", "").isTransient()).isFalse();
        assertThat(new VendorHttpException(403, "/x", "").isTransient()).isFalse();
    }

    @Test
    @DisplayName("headers are sent and the JSON body round-trips")
    void headersAndBodyAreSent() throws Exception {
        StringBuilder seenHeader = new StringBuilder();
        StringBuilder seenBody = new StringBuilder();
        serve(exchange -> {
            seenHeader.append(String.valueOf(exchange.getRequestHeaders().getFirst("Token")));
            seenBody.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"echo\":true}");
        });

        JsonNode response = client().post("/x", Map.of("UserRefNo", 424200021L), Map.of("Token", "T-1"));

        assertThat(seenHeader.toString()).isEqualTo("T-1");
        assertThat(seenBody.toString()).contains("\"UserRefNo\":424200021");
        assertThat(response.path("echo").asBoolean()).isTrue();
    }

    // ---- harness ----

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException;
    }

    private void serve(Handler handler) throws Exception {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", handler::handle);
        this.server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private JsonHttp client() {
        return new JsonHttp(URI.create("http://127.0.0.1:" + this.server.getAddress().getPort()),
                Duration.ofSeconds(2), JSON);
    }
}
