package com.thinq.fms.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The published API description, generated from the controllers and then checked.
 *
 * <p><b>This is the Stage 8 Swagger gate.</b> It was N/A for four implementation passes because
 * there were no endpoints; now there are, so the specification has to be produced and validated
 * rather than asserted.
 *
 * <p>The checks below are not schema-validity checks — springdoc produces valid OpenAPI by
 * construction. They are the project's own rules, which a valid document can still break: money as
 * a decimal, an account identifier accepted from a caller, an undocumented error shape. Those are
 * the defects that propagate into every generated client, and clients are not ours to fix.
 *
 * <p>The generated spec is written to {@code target/openapi.json} so it can be diffed between
 * builds and attached to the gate report.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class OpenApiSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private JsonNode spec() throws Exception {
        String body = this.mvc.perform(get("/v3/api-docs").with(user("JYOTHI01")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).as("springdoc produced no document").isNotBlank();

        JsonNode spec = JSON.readTree(body);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
        return spec;
    }

    @Test
    @DisplayName("the specification is generated and describes every endpoint the controllers expose")
    void specDescribesEveryEndpoint() throws Exception {
        JsonNode paths = spec().path("paths");

        // An exact set, deliberately. A new endpoint must be added here consciously — which is
        // what caught the transaction routes when they landed, rather than letting them ship
        // undocumented and unnoticed.
        assertThat(fieldNames(paths)).containsExactlyInAnyOrder(
                "/api/v1/funds/payout",
                "/api/v1/funds/payout/{requestId}",
                "/api/v1/funds/payin/limits",
                "/api/v1/funds/transactions",
                "/api/v1/funds/transactions/{reference}",
                "/api/v1/funds/statement.csv");
        assertThat(spec().path("openapi").asString()).startsWith("3.");
    }

    @Test
    @DisplayName("every money field in the schema is an integer of paise, never a decimal")
    void moneyIsNeverADecimal() throws Exception {
        JsonNode money = spec().path("components").path("schemas").path("MoneyDto");

        assertThat(money.isMissingNode()).as("MoneyDto must appear in the published schema").isFalse();
        // Rule R5 and HLD §9.1c. A `number` here becomes a float in every generated client, and
        // those are not ours to fix afterwards.
        assertThat(money.path("properties").path("paise").path("type").asString()).isEqualTo("integer");
        assertThat(money.path("properties").path("currency").path("type").asString()).isEqualTo("string");

        // And no `number`-typed property anywhere in the document.
        List<String> numeric = new ArrayList<>();
        collectNumberTyped(spec().path("components").path("schemas"), "", numeric);
        assertThat(numeric).as("a decimal money field would appear here").isEmpty();
    }

    @Test
    @DisplayName("no request schema accepts an account identifier from the caller")
    void noRequestAcceptsAnAccountIdentifier() throws Exception {
        // §4.3: the account is resolved from the authenticated subject. A schema field for it
        // would invite a client to send one, and invite a future handler to read it.
        JsonNode command = spec().path("components").path("schemas").path("PayoutRequestCommand");

        assertThat(fieldNames(command.path("properties")))
                .containsExactlyInAnyOrder("amount", "destinationRef");
        assertThat(spec().toString().toLowerCase())
                .doesNotContain("\"accountid\"").doesNotContain("\"account_id\"").doesNotContain("\"ucc\"");
    }

    @Test
    @DisplayName("the error shape is published, so clients can branch on code")
    void errorShapeIsPublished() throws Exception {
        JsonNode error = spec().path("components").path("schemas").path("ErrorResponse");

        assertThat(error.isMissingNode()).isFalse();
        assertThat(fieldNames(error.path("properties"))).contains("code", "message", "details");
    }

    @Test
    @DisplayName("every documented failure status carries the error schema")
    void failureResponsesReferenceTheErrorSchema() throws Exception {
        JsonNode paths = spec().path("paths");
        List<String> missing = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.properties().iterator();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> path = pathEntries.next();
            Iterator<Map.Entry<String, JsonNode>> ops = path.getValue().properties().iterator();
            while (ops.hasNext()) {
                Map.Entry<String, JsonNode> op = ops.next();
                Iterator<Map.Entry<String, JsonNode>> responses = op.getValue().path("responses").properties().iterator();
                while (responses.hasNext()) {
                    Map.Entry<String, JsonNode> response = responses.next();
                    int status = Integer.parseInt(response.getKey());
                    if (status < 400) {
                        continue;
                    }
                    if (!response.getValue().toString().contains("ErrorResponse")) {
                        missing.add(path.getKey() + " " + op.getKey() + " -> " + status);
                    }
                }
            }
        }
        // A documented failure with an undocumented body leaves a client generator producing a
        // typed success and an untyped error, which is how error handling gets skipped.
        assertThat(missing).as("failure responses without the error schema").isEmpty();
    }

    @Test
    @DisplayName("the declared security scheme is the one the filter chain actually accepts")
    void securitySchemeMatchesWhatIsEnforced() throws Exception {
        // THIS IS A DRIFT GUARD, and it exists because the drift happened. The document declared a
        // bearer JWT while ApiSecurityConfiguration enforced HTTP Basic, and the earlier version of
        // this test asserted only that a scheme was declared and applied — which it was. Every
        // client generated from the specification sent `Authorization: Bearer …` and got 401 on
        // every call, and the Swagger gate reported the scheme as verified.
        JsonNode spec = spec();

        assertThat(spec.path("components").path("securitySchemes").path("platformAuth").path("scheme").asString())
                .isEqualTo("basic");
        // Applied at the document level: nothing but health is anonymous, so a generated client
        // that omitted the credential would fail on its first call.
        assertThat(spec.path("security").toString()).contains("platformAuth");

        // The half the old test was missing — the declaration is checked against BEHAVIOUR. A
        // bearer credential does not authenticate here, so declaring `bearer` would be a lie the
        // specification tells to every client generator.
        assertThat(this.mvc.perform(get("/api/v1/funds/payin/limits")
                        .header("Authorization", "Bearer any.jwt.shaped.value"))
                        .andReturn().getResponse().getStatus())
                .as("a bearer credential is not accepted, so it must not be the declared scheme")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("the interactive UI is not exposed")
    void swaggerUiIsNotShipped() throws Exception {
        // A second surface to secure on a service that moves money, for no operational benefit.
        // 404, specifically. An earlier version asserted only "not 200", which passed while the
        // catch-all handler was turning every unknown path into a 500 and logging it as an
        // unhandled exception.
        assertThat(this.mvc.perform(get("/swagger-ui/index.html").with(user("JYOTHI01"))).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    // ---- helpers ----

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        names.addAll(node.propertyNames());
        return names;
    }

    /** Every property typed `number` — the shape a decimal money field would take. */
    private static void collectNumberTyped(JsonNode node, String path, List<String> out) {
        if (node.isObject()) {
            if ("number".equals(node.path("type").asString())) {
                out.add(path);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                collectNumberTyped(f.getValue(), path + "/" + f.getKey(), out);
            }
        }
    }
}
