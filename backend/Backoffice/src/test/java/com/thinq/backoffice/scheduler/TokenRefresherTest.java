package com.thinq.backoffice.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import com.thinq.backoffice.platform.AuthProperties;
import com.thinq.backoffice.platform.GatewayProperties;

/**
 * The managed token, against a real socket.
 *
 * <p><b>WHY A REAL HTTP SERVER AND NOT A MOCK.</b> What this class does that is worth testing is
 * almost entirely about the call actually going out and the answer actually coming back — a bare
 * JSON string rather than an object, an error envelope arriving with HTTP 200, an upstream that has
 * stopped answering. A mocked {@code RestClient} would assert that we call the method we call.
 * {@link HttpServer} is in the JDK, starts in about a millisecond, and needs no dependency.
 *
 * <p>Each test builds its own refresher, so {@code refreshAfter} can differ per case. That is the
 * knob the whole class turns on: a token younger than it must NOT trigger a login, and a token
 * older than it must.
 */
class TokenRefresherTest {

    private HttpServer upstream;
    private final AtomicInteger logins = new AtomicInteger();
    /** What the stub answers next. Swapped mid-test to simulate a rejection. */
    private final AtomicReference<String> loginResponse = new AtomicReference<>("\"10225|firstToken\"");

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress(0), 0);
        upstream.createContext("/api/login", exchange -> {
            logins.incrementAndGet();
            respond(exchange, loginResponse.get());
        });
        upstream.start();
    }

    @AfterEach
    void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private TokenRefresher refresher(Duration refreshAfter) {
        GatewayProperties gateway = new GatewayProperties(true,
                URI.create("http://localhost:" + upstream.getAddress().getPort()),
                Duration.ofSeconds(5));
        AuthProperties auth = new AuthProperties(AuthProperties.MANAGED, "api", "Api@123456",
                "0 0 * * * *", refreshAfter);
        return new TokenRefresher(gateway, auth, RestClient.builder(), JsonMapper.builder().build());
    }

    @Test
    void holdsNothingUntilTheFirstLogin() {
        assertThat(refresher(Duration.ofHours(20)).current()).isEmpty();
    }

    @Test
    void primingOnStartupObtainsAToken() {
        TokenRefresher tokens = refresher(Duration.ofHours(20));

        tokens.primeOnStartup();

        assertThat(tokens.current()).contains("10225|firstToken");
        assertThat(logins).hasValue(1);
    }

    @Test
    void aTokenYoungerThanRefreshAfterIsLeftAlone() {
        TokenRefresher tokens = refresher(Duration.ofHours(20));
        tokens.primeOnStartup();

        // The scheduled check runs far more often than the token needs replacing. Every one of
        // those ticks re-logging in would be a login storm against the back office.
        tokens.scheduledCheck();
        tokens.scheduledCheck();

        assertThat(logins).as("cron decides how often to LOOK, age decides whether to ACT")
                .hasValue(1);
    }

    @Test
    void aTokenOlderThanRefreshAfterIsReplaced() {
        // Anything already issued is older than this, so every check refreshes.
        TokenRefresher tokens = refresher(Duration.ofNanos(1));
        tokens.primeOnStartup();

        loginResponse.set("\"30999|secondToken\"");
        tokens.scheduledCheck();

        assertThat(tokens.current()).contains("30999|secondToken");
        assertThat(logins).hasValue(2);
    }

    @Test
    void aFailedRefreshKeepsTheTokenItAlreadyHolds() {
        TokenRefresher tokens = refresher(Duration.ofNanos(1));
        tokens.primeOnStartup();
        assertThat(tokens.current()).contains("10225|firstToken");

        upstream.stop(0);
        tokens.scheduledCheck();

        // A working credential with hours left on it must not be thrown away because one HTTP call
        // failed. Discarding it would turn a transient blip into an outage.
        assertThat(tokens.current()).contains("10225|firstToken");
    }

    @Test
    void aRejectedLoginLeavesUsHoldingNothing() {
        // TechExcel answers a refusal with HTTP 200 and the envelope, like everything else.
        loginResponse.set("""
                {"Success":"false","Success Description":"","Error Code":"Credential Error",
                 "Error Description":"The provided credentials are incorrect."}""");
        TokenRefresher tokens = refresher(Duration.ofHours(20));

        tokens.primeOnStartup();

        assertThat(tokens.current()).isEmpty();
    }

    @Test
    void aLoginAnsweringWithATokenFieldIsAccepted() {
        // The document's response TABLE describes an object with a Token field; its SAMPLE shows a
        // bare string. Both are the vendor's own, so both are accepted.
        loginResponse.set("{\"Token\":\"40111|objectShapedToken\"}");
        TokenRefresher tokens = refresher(Duration.ofHours(20));

        tokens.primeOnStartup();

        assertThat(tokens.current()).contains("40111|objectShapedToken");
    }
}
