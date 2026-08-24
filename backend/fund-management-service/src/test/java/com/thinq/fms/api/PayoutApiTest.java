package com.thinq.fms.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.thinq.fms.derivation.WithdrawableVerdict;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The withdrawal endpoints, end to end through the real Spring stack.
 *
 * <p>What is under test here is the <b>edge</b>: status codes, the error body, and above all that
 * the account comes from the authenticated principal rather than from anything the caller controls.
 * The domain rules themselves are covered by {@code PayoutOrchestratorTest}; repeating them here
 * would test the same logic twice and the wiring not at all.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // No database in this slice. The endpoints under test reach the domain and stop there.
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class PayoutApiTest {

    static final String EXCLUDED = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Principal JYOTHI = () -> "JYOTHI01";
    private static final Principal SOMEONE_ELSE = () -> "OTHER99";

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
    @DisplayName("a valid request is created, and carries Rule W3a's shrink warning")
    void validRequestIsCreated() throws Exception {
        JsonNode body = json(post("/api/v1/funds/payout")
                .with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(400_000L, "acc-4471")), 201);

        assertThat(body.path("state").asString()).isEqualTo("ACCEPTED");
        // Rule W3a: the request reserves nothing and settles against whatever is available, so the
        // trader is told it may shrink BEFORE they commit rather than when it happens.
        assertThat(body.path("shrinkWarningKey").asString()).isEqualTo("WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT");
        // Profile PR-31: only the masked form ever leaves this system.
        assertThat(body.path("destinationMasked").asString()).isEqualTo("••••4471");
        assertThat(body.path("withdrawableAtRequest").path("paise").asLong()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("money crosses the wire as integer paise with its currency, never a decimal")
    void moneyIsPaiseOnTheWire() throws Exception {
        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(123_456L, "acc-4471")), 201);

        JsonNode amount = body.path("withdrawableAtRequest");
        assertThat(amount.path("paise").isIntegralNumber())
                .as("a float here propagates into every generated client").isTrue();
        assertThat(amount.path("currency").asString()).isEqualTo("INR");
    }

    @Test
    @DisplayName("more than the withdrawable figure is 422 and carries the figure")
    void aboveWithdrawableIsUnprocessableAndExplains() throws Exception {
        ApiTestConfiguration.withdrawable = Money.ofPaise(300_000L);

        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(400_000L, "acc-4471")), 422);

        assertThat(body.path("code").asString()).isEqualTo("amount_exceeds_withdrawable");
        // REQ-102: the client explains the refusal without a second round trip.
        assertThat(body.path("details").path("withdrawable").path("paise").asLong()).isEqualTo(300_000L);
        assertThat(body.path("details").path("requested").path("paise").asLong()).isEqualTo(400_000L);
    }

    @Test
    @DisplayName("a DIVERGENT verdict is 409 and names the verdict, not a figure")
    void divergentVerdictIsConflict() throws Exception {
        ApiTestConfiguration.verdict = WithdrawableVerdict.DIVERGENT;

        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(1_000L, "acc-4471")), 409);

        assertThat(body.path("code").asString()).isEqualTo("withdrawable_unavailable");
        assertThat(body.path("details").path("verdict").asString()).isEqualTo("DIVERGENT");
    }

    @Test
    @DisplayName("Rule W4's constraint violation surfaces as 409 request_already_open")
    void secondOpenRequestIsConflict() throws Exception {
        json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(1_000L, "acc-4471")), 201);

        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(2_000L, "acc-4471")), 409);

        assertThat(body.path("code").asString()).isEqualTo("request_already_open");
    }

    @Test
    @DisplayName("an unverified destination is 422")
    void unverifiedDestinationIsUnprocessable() throws Exception {
        ApiTestConfiguration.destinationVerified = false;

        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(1_000L, "acc-4471")), 422);

        assertThat(body.path("code").asString()).isEqualTo("destination_not_verified");
    }

    @Test
    @DisplayName("a zero or negative amount is refused")
    void nonPositiveAmountIsRefused() throws Exception {
        // Checking WHERE this is refused, not just that it is. The @Positive annotation on the
        // command's accessor does not run — `amountPaise()` is not a JavaBean getter, so Bean
        // Validation never sees it. The refusal comes from the orchestrator instead, which is the
        // right place for it under §4.3's rule-in-the-domain split, but the annotation implied a
        // second guard that does not exist.
        JsonNode zero = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(0L, "acc-4471")), 400);
        assertThat(zero.path("code").asString()).isEqualTo("invalid_request");

        JsonNode negative = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(-500L, "acc-4471")), 400);
        assertThat(negative.path("code").asString()).isEqualTo("invalid_request");
    }

    @Test
    @DisplayName("a malformed body is 400 and names the fields, nothing more")
    void malformedBodyIsBadRequest() throws Exception {
        JsonNode body = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinationRef\":\"\"}"), 400);

        assertThat(body.path("code").asString()).isEqualTo("invalid_request");
        assertThat(body.path("details").has("amount") || body.path("details").has("destinationRef")).isTrue();
        // The body must not leak an exception class or a stack frame.
        assertThat(body.toString()).doesNotContain("Exception").doesNotContain("com.thinq");
    }

    @Test
    @DisplayName("a non-numeric request id is a 400, not a 500")
    void aNonNumericRequestIdIsAClientError() throws Exception {
        // requestId is a long, so a non-numeric segment cannot reach cancel() — which is exactly
        // why the rejection belongs at the binding boundary. It came back as 500 internal_error
        // until MethodArgumentTypeMismatchException got its own handler.
        JsonNode body = json(delete("/api/v1/funds/payout/not-a-number").with(user("JYOTHI01")), 400);

        assertThat(body.path("code").asString()).isEqualTo("invalid_request");
        assertThat(body.path("details").path("parameter").asString()).isEqualTo("requestId");
    }

    @Test
    @DisplayName("another trader's request is not found, never forbidden")
    void anotherTradersRequestIsNotFound() throws Exception {
        JsonNode created = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(1_000L, "acc-4471")), 201);
        long id = created.path("requestId").asLong();

        // §4.3: confirming existence would itself leak, so not-yours and not-there answer alike.
        JsonNode body = json(delete("/api/v1/funds/payout/" + id).with(user("OTHER99")), 409);

        assertThat(body.path("code").asString()).isEqualTo("not_cancellable");
        assertThat(body.path("details").path("reason").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("the account comes from the principal — a body cannot name someone else's")
    void accountComesFromThePrincipal() throws Exception {
        // The request body carries an accountId that the endpoint must ignore entirely.
        json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"paise\":1000,\"currency\":\"INR\"},"
                        + "\"destinationRef\":\"acc-4471\",\"accountId\":\"OTHER99\"}"), 201);

        // It landed under the principal's account, not the body's.
        assertThat(this.repository.openFor(ApiTestConfiguration.ACCOUNT)).isPresent();
        assertThat(this.repository.openFor(com.thinq.fms.platform.money.AccountRef.of("OTHER99"))).isEmpty();
    }

    @Test
    @DisplayName("cancelling returns the cancelled request; no open request returns 204")
    void cancelAndOpenRequestLifecycle() throws Exception {
        json(get("/api/v1/funds/payout").with(user("JYOTHI01")), 204);

        JsonNode created = json(post("/api/v1/funds/payout").with(user("JYOTHI01"))
                .contentType(MediaType.APPLICATION_JSON).content(command(1_000L, "acc-4471")), 201);
        json(get("/api/v1/funds/payout").with(user("JYOTHI01")), 200);

        JsonNode cancelled = json(delete("/api/v1/funds/payout/" + created.path("requestId").asLong())
                .with(user("JYOTHI01")), 200);
        assertThat(cancelled.path("state").asString()).isEqualTo("CANCELLED");

        json(get("/api/v1/funds/payout").with(user("JYOTHI01")), 204);
    }

    @Test
    @DisplayName("route limits report an uncapped route as null, not zero")
    void uncappedRouteIsNullNotZero() throws Exception {
        JsonNode body = json(get("/api/v1/funds/payin/limits").with(user("JYOTHI01")), 200);

        JsonNode neft = body.path("routes").findValuesAsString("route").contains("NEFT")
                ? findRoute(body, "NEFT") : null;
        assertThat(neft).isNotNull();
        // Rendering an uncapped route as zero would tell a trader NEFT is exhausted.
        assertThat(neft.path("remainingToday").isNull() || neft.path("remainingToday").isMissingNode())
                .isTrue();
        assertThat(findRoute(body, "UPI").path("remainingToday").path("paise").asLong())
                .isEqualTo(15_000_000L);
    }

    // ---- harness ----

    private static JsonNode findRoute(JsonNode body, String route) {
        for (JsonNode r : body.path("routes")) {
            if (route.equals(r.path("route").asString())) {
                return r;
            }
        }
        throw new AssertionError("route not present: " + route);
    }

    private static String command(long paise, String destination) {
        return "{\"amount\":{\"paise\":" + paise + ",\"currency\":\"INR\"},"
                + "\"destinationRef\":\"" + destination + "\"}";
    }

    private JsonNode json(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = this.mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("status for %s", request)
                .isEqualTo(expectedStatus);
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
    }
}
