package com.thinq.backoffice.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The rate limit as a caller meets it: over HTTP, as a 429.
 *
 * <p>{@link RateLimiterTest} drives the bucket directly and says nothing about the response. This
 * says nothing about the bucket and everything about the response — which is the half a client
 * integrates against.
 *
 * <p>The buckets are ONE request wide here so the refusal is deterministic and instant. The real
 * defaults are in application.yml and are expected to change; a test pinned to them would fail the
 * next time somebody tuned production, which is the wrong thing to make fragile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "techexcel.live=false",
        "backoffice.ratelimit.enabled=true",
        "backoffice.ratelimit.defaults.requests=1",
        "backoffice.ratelimit.defaults.window=1m",
        // application.yml names ledger and login explicitly, and a named endpoint beats the
        // default — so overriding only the default would leave both at their production allowance
        // and this test would silently prove nothing.
        "backoffice.ratelimit.per-endpoint.ledger.requests=1",
        "backoffice.ratelimit.per-endpoint.ledger.window=1m"})
class RateLimitOverHttpTest {

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions call(String token) throws Exception {
        return mvc.perform(post("/api/entry/ledger")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    void theSecondCallIsRefusedWithEverythingACallerNeedsToBackOff() throws Exception {
        call("firstCaller");

        call("firstCaller")
                .andExpect(status().isTooManyRequests())
                // Never zero — a Retry-After of 0 invites an instant retry that is refused again.
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string("X-RateLimit-Limit", "1"))
                .andExpect(header().string("X-RateLimit-Window", "PT1M"))
                // 429, not the vendor's 200 envelope: this is OUR verdict about OUR capacity, and
                // saying the back office refused the data would send a caller to debug TechExcel.
                .andExpect(jsonPath("$['Error Code']").value("Rate_Limited"))
                .andExpect(jsonPath("$['Error Description']",
                        org.hamcrest.Matchers.containsString("was")));
    }

    @Test
    void oneCallerBeingThrottledDoesNotThrottleAnother() throws Exception {
        call("noisyCaller");
        call("noisyCaller").andExpect(status().isTooManyRequests());

        // Identity is the bearer token. A shared bucket would let one runaway consumer take the
        // API down for everybody, which is the outcome the limiter exists to prevent.
        call("quietCaller").andExpect(status().isOk());
    }

    @Test
    void theBarePathAndThePrefixedPathShareOneBucket() throws Exception {
        mvc.perform(post("/api/entry/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer alternatingCaller")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // Alternating between the two prefixes must not hand a caller double the allowance.
        mvc.perform(post("/TechBoRest/api/entry/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer alternatingCaller")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aCallerSendingNoHeaderIsStillIdentifiedAndStillLimited() throws Exception {
        // With no caller credential anywhere in this API, the fallback identity — the remote
        // address — is the ONLY identity most callers have. If that path did not limit, the
        // limiter would effectively be off in production.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/entry/ledger")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/entry/ledger")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }
}
