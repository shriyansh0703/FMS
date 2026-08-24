package com.thinq.fms.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MvcResult;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * What happens when a client sends something wrong.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Every other API test sends well-formed JSON, and 159 of them passed while two real defects sat
 * on this path: a malformed body returned <b>500</b> and logged as an unhandled exception, and a
 * decimal {@code paise} was <b>accepted and silently truncated</b> — {@code 100.9} became
 * {@code 100} with a 201.
 *
 * <p>The second is the one worth remembering. `OpenApiSpecTest` asserted the published schema
 * declares {@code paise} as an integer, and it passed throughout. A contract test verifies what the
 * contract <i>says</i>; it says nothing about whether the deserialiser honours it. These are the
 * assertions that close that gap.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class HostileBodyApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Principal JYOTHI = () -> "JYOTHI01";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiTestConfiguration.InMemoryPayoutRepository repository;

    @BeforeEach
    void reset() {
        ApiTestConfiguration.reset();
        this.repository.clear();
    }

    @Test
    @DisplayName("a decimal paise value is refused, not truncated")
    void decimalPaiseIsRefused() throws Exception {
        // The defect: this returned 201 and stored 100, so a client sending a computed value that
        // happened to carry a fraction lost money with no error and no log line.
        JsonNode body = send("{\"amount\":{\"paise\":100.9,\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\"}", 400);

        assertThat(body.path("code").asString()).isEqualTo("invalid_request");
        assertThat(this.repository.openFor(ApiTestConfiguration.ACCOUNT))
                .as("nothing may be stored from a refused body").isEmpty();
    }

    @Test
    @DisplayName("a whole-number float is refused too — the type is wrong, not just the value")
    void wholeNumberFloatIsAlsoRefused() throws Exception {
        // 100.0 truncates losslessly, which is exactly why it must still be refused: accepting it
        // would make the rule "we truncate quietly when it happens to be safe", and the caller
        // whose next value is 100.9 would have no reason to expect different treatment.
        send("{\"amount\":{\"paise\":100.0,\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\"}", 400);
    }

    @Test
    @DisplayName("a quoted number is refused rather than parsed")
    void quotedNumberIsRefused() throws Exception {
        // "100" means the client has a serialisation bug. Accepting it hides the bug until the day
        // it sends something that is not a number at all.
        send("{\"amount\":{\"paise\":\"100\",\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\"}", 400);
    }

    @Test
    @DisplayName("an integer paise value still works")
    void integerPaiseStillWorks() throws Exception {
        // The other half of the fix: refusing decimals must not break the correct case.
        JsonNode body = send("{\"amount\":{\"paise\":100,\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\"}", 201);

        assertThat(body.path("state").asString()).isEqualTo("ACCEPTED");
    }

    @ParameterizedTest(name = "{0} is a 400, not a 500")
    @CsvSource(delimiter = '|', value = {
            "syntactically broken JSON       | { not json",
            "empty body                      | ",
            "a JSON array where an object is expected | [1,2,3]",
            "a bare string                   | \"hello\"",
            "missing amount                  | {\"destinationRef\":\"acc-4471\"}",
            "missing currency                | {\"amount\":{\"paise\":100},\"destinationRef\":\"acc-4471\"}",
            "paise as text                   | {\"amount\":{\"paise\":\"abc\",\"currency\":\"INR\"},\"destinationRef\":\"acc-4471\"}",
            "amount as a string              | {\"amount\":\"100\",\"destinationRef\":\"acc-4471\"}",
            "amount as a number              | {\"amount\":100,\"destinationRef\":\"acc-4471\"}",
            "paise larger than a long        | {\"amount\":{\"paise\":99999999999999999999,\"currency\":\"INR\"},\"destinationRef\":\"acc-4471\"}",
            "deeply nested rubbish           | {\"amount\":{\"paise\":{\"a\":{\"b\":1}},\"currency\":\"INR\"},\"destinationRef\":\"acc-4471\"}"
    })
    @DisplayName("every malformed body is a client error")
    void malformedBodiesAreClientErrors(String label, String body) throws Exception {
        // Each of these returned 500 and logged "unhandled exception on the API surface". A client
        // with a serialisation bug produced ERROR-level entries indefinitely, and the log stopped
        // being a signal.
        MvcResult result = this.mvc.perform(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("%s must be a 4xx", label)
                .isBetween(400, 499);
    }

    @Test
    @DisplayName("a malformed body leaks no internal detail")
    void malformedBodyLeaksNothing() throws Exception {
        // HttpMessageNotReadableException.getMessage() carries a fragment of the submitted JSON
        // and the fully qualified name of the target type. Returning it would leak internal type
        // names, and this system's rule is that no exception reaches a client as internal detail.
        JsonNode body = send("{\"amount\":{\"paise\":\"abc\",\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\"}", 400);

        String rendered = body.toString();
        assertThat(rendered)
                .doesNotContain("com.thinq")
                .doesNotContain("Exception")
                .doesNotContain("jackson")
                .doesNotContain("java.lang");
        assertThat(body.path("code").asString()).isEqualTo("invalid_request");
    }

    @Test
    @DisplayName("a wrong currency is refused and echoes only the caller's own value")
    void wrongCurrencyIsRefused() throws Exception {
        JsonNode body = send("{\"amount\":{\"paise\":100,\"currency\":\"USD\"},"
                + "\"destinationRef\":\"acc-4471\"}", 400);

        assertThat(body.path("message").asString()).contains("USD");
        assertThat(body.toString()).doesNotContain("com.thinq");
    }

    @Test
    @DisplayName("an unknown field is ignored rather than rejected")
    void unknownFieldIsIgnored() throws Exception {
        // Deliberate: a client sending a field this version does not know is forward-compatible,
        // not broken. The guarantee that matters — that such a field cannot name another trader's
        // account — is asserted in PayoutApiTest and in the OpenAPI schema check.
        send("{\"amount\":{\"paise\":100,\"currency\":\"INR\"},"
                + "\"destinationRef\":\"acc-4471\",\"somethingNew\":true}", 201);
    }

    /** Named `send` rather than `post` so it does not shadow the statically imported builder. */
    private JsonNode send(String body, int expectedStatus) throws Exception {
        MvcResult result = this.mvc.perform(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String rendered = result.getResponse().getContentAsString();
        return rendered.isBlank() ? JSON.createObjectNode() : JSON.readTree(rendered);
    }
}
