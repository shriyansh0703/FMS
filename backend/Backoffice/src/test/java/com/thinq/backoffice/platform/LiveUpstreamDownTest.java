package com.thinq.backoffice.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Live mode with nothing at the other end.
 *
 * <p>THE DISTINCTION THIS PINS IS THE WHOLE REASON THE GATEWAY ANSWERS 502 ANYWHERE. Everywhere
 * else a failure arrives as HTTP 200 carrying TechExcel's verdict — "the back office considered
 * your request and refused it". An unreachable back office considered nothing. A caller that could
 * not tell those apart would retry a rejection and give up on an outage, which is exactly backwards.
 *
 * <p>Port 1 is chosen because nothing listens there and a connection is refused immediately, so the
 * test does not wait on a timeout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "backoffice.ratelimit.enabled=false",
        "techexcel.live=true",
        "techexcel.base-url=http://localhost:1/TechBoRest",
        "techexcel.timeout=2s",
        // Live mode requires managed auth now — callers have no token to present. The credential
        // is never used here because nothing is listening, which is the point of the test.
        "techexcel.auth.mode=managed",
        "techexcel.auth.username=api",
        "techexcel.auth.password=Api@123456"})
class LiveUpstreamDownTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anUnreachableBackOfficeIs502AndNotTheVendorsEnvelope() throws Exception {
        mvc.perform(post("/api/entry/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$['Error Code']").value("Database_Exception"))
                .andExpect(jsonPath("$['Error Description']",
                        org.hamcrest.Matchers.containsString("Upstream TechExcel unreachable")));
    }

    @Test
    void anUnmappedRouteIsAlso502RatherThanA404() throws Exception {
        // In live mode the catch-all forwards too, so it meets the same unreachable upstream.
        mvc.perform(post("/api/entry/whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway());
    }
}
