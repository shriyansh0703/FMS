package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LIVE MODE, THE PATH THAT WILL CARRY EVERY REAL CALL.
 *
 * <p>Every other test in this module runs against the mock, which means the entire proxy — the
 * prefix strip, the header relay, the managed-token fill-in, the verbatim status relay — was
 * unexercised by the suite while being the only code that runs in production. This class points the
 * gateway at a {@link HttpServer} on a loopback port and drives real requests through it.
 *
 * <p>The stub records the path and the {@code Authorization} header it was called with, because
 * those two are the whole contract: <em>did the call arrive where it should, acting as who it
 * should</em>.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "backoffice.ratelimit.enabled=false",
        "techexcel.auth.mode=managed",
        "techexcel.auth.username=api",
        "techexcel.auth.password=Api@123456",
        // Well under the documented 24h, and never reached inside a test run.
        "techexcel.auth.refresh-after=20h"})
class LiveModeTest {

    static HttpServer upstream;
    static final AtomicReference<String> lastPath = new AtomicReference<>();
    static final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress(0), 0);
        upstream.createContext("/TechBoRest/api/login", exchange -> respond(exchange, 200,
                "\"10225|managedToken\""));
        upstream.createContext("/TechBoRest/api", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            // 418 for the deliberately-unknown route below, so "relayed verbatim" is provable
            // rather than indistinguishable from our own answer.
            int status = exchange.getRequestURI().getPath().endsWith("/not_a_real_endpoint") ? 418 : 200;
            respond(exchange, status, "{\"Success\":\"True\",\"Success Description\":\"from upstream\"}");
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @DynamicPropertySource
    static void pointAtUpstream(DynamicPropertyRegistry registry) {
        registry.add("techexcel.live", () -> true);
        registry.add("techexcel.base-url",
                () -> "http://localhost:" + upstream.getAddress().getPort() + "/TechBoRest");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Autowired
    private MockMvc mvc;

    private void proxied(String path, String upstreamPath) throws Exception {
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("from upstream")));
        assertThat(lastPath.get()).as("%s must reach upstream", path).isEqualTo(upstreamPath);
    }

    @Test
    void everyRouteIsRelayedUpstreamInsteadOfAnsweredFromTheMock() throws Exception {
        proxied("/api/entry/ledger", "/TechBoRest/api/entry/ledger");
        proxied("/api/entry/virtual_debit_report", "/TechBoRest/api/entry/virtual_debit_report");
        proxied("/api/entry/new_interest_process", "/TechBoRest/api/entry/new_interest_process");
        proxied("/api/entry/brk_remeshire_view", "/TechBoRest/api/entry/brk_remeshire_view");
    }

    @Test
    void theTechBoRestPrefixIsStrippedBeforeForwarding() throws Exception {
        // Arriving WITH the prefix and arriving without it must reach the same upstream path. The
        // base URL carries the prefix, so failing to strip it would produce /TechBoRest/TechBoRest.
        proxied("/TechBoRest/api/entry/ledger", "/TechBoRest/api/entry/ledger");
    }

    @Test
    void aCallWithNoTokenOfItsOwnCarriesTheManagedOne() throws Exception {
        proxied("/api/entry/ledger", "/TechBoRest/api/entry/ledger");

        assertThat(lastAuth.get()).isEqualTo("Bearer 10225|managedToken");
    }

    @Test
    void aCallersOwnTokenAlwaysWinsOverTheManagedOne() throws Exception {
        // Nothing in this API issues such a token any more, but a header a caller sends is still
        // forwarded unchanged rather than overwritten — so a trusted internal caller acting as
        // itself keeps working if that is ever wired up.
        mvc.perform(post("/api/entry/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer callersOwnToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Turning managed mode on must never silently change who an authenticated caller acts as.
        assertThat(lastAuth.get()).isEqualTo("Bearer callersOwnToken");
    }

    @Test
    void anUndocumentedRouteIsForwardedRatherThanRefused() throws Exception {
        // The opposite of mock mode's 404. TechExcel serves far more than this service holds
        // documents for, and a pass-through that drops the rest is a worse lie than a 404.
        mvc.perform(post("/api/entry/not_a_real_endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(418));

        assertThat(lastPath.get()).isEqualTo("/TechBoRest/api/entry/not_a_real_endpoint");
    }

}
