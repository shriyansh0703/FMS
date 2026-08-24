package com.thinq.fms.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The access control itself (Stage 11, HIGH-1 and MEDIUM-1).
 *
 * <p>Before this, the service performed no authentication of its own: every controller read its
 * caller from a {@link java.security.Principal} it never validated, so the whole decision belonged to
 * a gateway not configured, asserted or named anywhere in this repository. It did fail closed — an
 * unauthenticated request got a 500 and no data — but a control that exists only in the deployment
 * topology is one misrouted ingress from being absent.
 *
 * <p>These assertions are the point of the fix. A filter chain nothing tests is a filter chain that
 * can be removed in a refactor without anything going red.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class ApiSecurityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0} refuses an unauthenticated caller")
    @ValueSource(strings = {
            "/api/v1/funds/transactions",
            "/api/v1/funds/payout",
            "/api/v1/funds/payin/limits",
            "/api/v1/funds/statement.csv"})
    @DisplayName("an unauthenticated request is refused with 401 and no data")
    void unauthenticatedRequestsAreRefused(String path) throws Exception {
        MvcResult result = this.mvc.perform(get(path)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("401, not the 500 this used to return — a burst of probing must not read as an "
                        + "outage and page the availability owner instead of the security owner")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("a refusal names no account and leaks no internal detail")
    void aRefusalLeaksNothing() throws Exception {
        MvcResult result = this.mvc.perform(get("/api/v1/funds/transactions")).andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("code").asString()).isEqualTo("unauthenticated");
        // Distinguishing "no such account" from "wrong credentials" would let a caller enumerate
        // accounts one refusal at a time.
        assertThat(body.get("message").asString())
                .doesNotContainIgnoringCase("account")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("user");
    }

    @Test
    @DisplayName("a write endpoint refuses an unauthenticated caller before reading the body")
    void aWriteRefusesBeforeReadingTheBody() throws Exception {
        // The body is deliberately malformed. A 400 here would mean the request reached parsing,
        // which means an unauthenticated caller can probe validation behaviour.
        MvcResult result = this.mvc.perform(post("/api/v1/funds/payout")
                .contentType("application/json")
                .content("{ this is not json")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("a new endpoint is protected by default, not once someone remembers to list it")
    void unknownApiPathsAreAlsoProtected() throws Exception {
        // anyRequest().authenticated() means a path that does not exist yet still refuses. Six of
        // the thirteen planned endpoints are unbuilt; they arrive protected.
        assertThat(this.mvc.perform(get("/api/v1/funds/summary")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("the health probe stays reachable, because an unreachable one fails the deployment")
    void theHealthProbeStaysReachable() throws Exception {
        int status = this.mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();

        assertThat(status).as("permitted, whether or not the endpoint is enabled here")
                .isNotEqualTo(401);
    }
}
