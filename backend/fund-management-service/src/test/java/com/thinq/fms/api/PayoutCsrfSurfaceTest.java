package com.thinq.fms.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The property that stops a cross-site forgery reaching the withdrawal endpoint (Stage 11, MEDIUM-1).
 *
 * <p><b>Why this test exists rather than a CSRF token.</b> CSRF protection is disabled, and the
 * reasoning usually given for that — a stateless API has no ambient credential — is false here,
 * because HTTP Basic is enabled and a browser reattaches cached Basic credentials to requests a
 * third-party page initiates. The actual protection is narrower: an HTML form can send only three
 * content types cross-origin without a CORS preflight, every write mapping declares
 * {@code consumes = application/json}, and so all three are refused before a handler runs. A
 * cross-origin JSON request needs a preflight, and no CORS configuration grants one.
 *
 * <p>That protection was previously undocumented and explained with the wrong reason. These
 * assertions turn it into something that fails loudly if reversed — which matters because the change
 * most likely to reverse it, adding CORS for the frontend, will not look like a security change to
 * whoever makes it.
 *
 * <p><b>What these tests are and are not sensitive to.</b> They guard the behaviour: the 415s come
 * from message-converter negotiation, and removing the {@code consumes} declaration on the mapping
 * does not change them — verified by removing it and watching all five still pass. What would fail
 * them is a form-binding converter being added, a write endpoint accepting a form content type, or a
 * CORS configuration granting a preflight. Those are the changes that actually open the hole.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class PayoutCsrfSurfaceTest {

    /** A well-formed withdrawal body, so a refusal is about the content type and nothing else. */
    private static final String VALID_BODY =
            "{\"paise\":100000,\"currency\":\"INR\",\"destinationRef\":\"acc-1\"}";

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0} is refused before the handler runs")
    @ValueSource(strings = {
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "text/plain"})
    @DisplayName("the three cross-origin form content types cannot reach the withdrawal handler")
    void crossOriginFormContentTypesAreRefused(String contentType) throws Exception {
        // These are exactly the content types a hostile page can POST cross-origin without a
        // preflight. The caller is authenticated on purpose: the point is that even a caller whose
        // Basic credentials the browser would reattach cannot get a form POST through.
        int status = this.mvc.perform(post("/api/v1/funds/payout")
                        .with(user("JYOTHI01"))
                        .contentType(contentType)
                        .content(VALID_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("%s must be refused as an unsupported media type, not processed", contentType)
                .isEqualTo(415);
    }

    @Test
    @DisplayName("JSON reaches the handler, which is why a preflight is the only way in cross-origin")
    void jsonReachesTheHandler() throws Exception {
        // Not asserting success — the stub may refuse this withdrawal on its merits. Asserting that
        // it got past media-type negotiation, which is what makes the 415s above meaningful rather
        // than an artefact of every POST being rejected.
        int status = this.mvc.perform(post("/api/v1/funds/payout")
                        .with(user("JYOTHI01"))
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("JSON is negotiated; the response is then about the request's own merits")
                .isNotEqualTo(415);
    }

    @Test
    @DisplayName("no CORS configuration grants a cross-origin preflight")
    void noCorsPreflightIsGranted() throws Exception {
        // The other half of the protection. If a permissive CORS configuration is ever added, this
        // fails and the CSRF decision has to be revisited — which is the entire point of pinning it.
        int status = this.mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .options("/api/v1/funds/payout")
                                .header("Origin", "https://evil.example")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "content-type"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("a preflight from an arbitrary origin must not be granted")
                .isNotEqualTo(200);
    }
}
