package com.thinq.fms.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.thinq.fms.ledgerview.LedgerEntry;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MvcResult;

import java.security.Principal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The transaction endpoints, through the real stack.
 *
 * <p>The query logic itself is covered by {@code TransactionQueryServiceTest}. What these assert is
 * the edge: that the view parameter reaches the right view, that the period is echoed, that an
 * export returns the same thing the list did (Rule L8a), and that the CSV leaves as a download
 * rather than as JSON.
 */
@SpringBootTest(classes = {com.thinq.fms.FundManagementApplication.class, ApiTestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" + PayoutApiTest.EXCLUDED,
        "spring.flyway.enabled=false"
})
class TransactionsApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Principal JYOTHI = () -> "JYOTHI01";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void seed() {
        ApiTestConfiguration.LEDGER.clear();
        ApiTestConfiguration.LEDGER.add(entry("V1", "R", 0L, 500_000L, 500_000L, "Fund transfer received"));
        ApiTestConfiguration.LEDGER.add(entry("V2", "J", 5_000L, 0L, 495_000L, "Brokerage and statutory charges"));
    }

    @Test
    @DisplayName("the movements view carries only money the trader moved")
    void movementsViewIsMoneyInAndOut() throws Exception {
        JsonNode body = json(get("/api/v1/funds/transactions").with(user("JYOTHI01")), 200);

        assertThat(body.path("view").asString()).isEqualTo("MOVEMENTS");
        assertThat(body.path("entries")).hasSize(1);
        assertThat(body.path("entries").get(0).path("reference").asString()).isEqualTo("V1");
        assertThat(body.path("entries").get(0).path("descriptionKey").asString()).isEqualTo("ENTRY_PAYIN");
    }

    @Test
    @DisplayName("the all-entries view carries the charge too, with its running balance")
    void allEntriesViewCarriesEverything() throws Exception {
        JsonNode body = json(get("/api/v1/funds/transactions?view=ALL_ENTRIES").with(user("JYOTHI01")), 200);

        assertThat(body.path("entries")).hasSize(2);
        // TechExcel's CLOSING_AMT, carried through rather than accumulated here.
        assertThat(body.path("entries").get(0).path("runningBalance").path("paise").asLong())
                .isEqualTo(495_000L);
    }

    @Test
    @DisplayName("the period is echoed back, so a client renders what it got")
    void periodIsEchoed() throws Exception {
        JsonNode body = json(get("/api/v1/funds/transactions").with(user("JYOTHI01")), 200);

        assertThat(body.path("period").path("to").asString()).isEqualTo(TODAY.toString());
        assertThat(body.path("period").path("from").asString()).isEqualTo(TODAY.minusDays(29).toString());
    }

    @Test
    @DisplayName("Rule L7: an empty period says so and offers a wider one")
    void emptyPeriodOffersAWiderOne() throws Exception {
        ApiTestConfiguration.LEDGER.clear();

        JsonNode body = json(get("/api/v1/funds/transactions").with(user("JYOTHI01")), 200);

        assertThat(body.path("entries")).isEmpty();
        // Not a bare empty array: blank space is indistinguishable from a failure to load.
        assertThat(body.path("suggestedWiderPeriod").isMissingNode()).isFalse();
        assertThat(body.path("period").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("an inverted or over-wide period is a 400, not a 500")
    void badPeriodIsAClientError() throws Exception {
        json(get("/api/v1/funds/transactions?from=2026-08-21&to=2026-08-01").with(user("JYOTHI01")), 400);
        json(get("/api/v1/funds/transactions?from=2020-01-01&to=2026-08-21").with(user("JYOTHI01")), 400);
    }

    @Test
    @DisplayName("a query parameter that will not convert is a 400 naming the parameter, not a 500")
    void unconvertibleQueryParameterIsAClientError() throws Exception {
        // This returned 500 internal_error. Spring raises MethodArgumentTypeMismatchException for a
        // value it cannot bind, and that type implements neither Spring's ErrorResponse interface
        // nor IllegalArgumentException, so it fell past every specific handler to the catch-all.
        // The published specification declares 400 on this operation, and a client sending a stale
        // enum value got an internal error and an ERROR log entry it could do nothing about.
        JsonNode view = json(get("/api/v1/funds/transactions?view=NONSENSE").with(user("JYOTHI01")), 400);

        assertThat(view.path("code").asString()).isEqualTo("invalid_request");
        assertThat(view.path("details").path("parameter").asString()).isEqualTo("view");
        // The permitted values are already published in the spec, so listing them leaks nothing and
        // saves the caller a round trip through the documentation to fix a typo.
        assertThat(view.path("details").path("permitted").toString())
                .contains("MOVEMENTS").contains("ALL_ENTRIES");

        // Not enum-specific: the same binding failure covers a date that will not parse.
        JsonNode date = json(get("/api/v1/funds/transactions?from=yesterday").with(user("JYOTHI01")), 400);
        assertThat(date.path("details").path("parameter").asString()).isEqualTo("from");
    }

    @Test
    @DisplayName("the refusal names the parameter without echoing what was sent")
    void theRefusalDoesNotEchoTheSubmittedValue() throws Exception {
        // §4.4's rule that no internal detail reaches a client cuts both ways here: the type name
        // TransactionView is internal and must not appear, and the submitted value is attacker-
        // controlled text that this API has no reason to reflect back.
        JsonNode body = json(get("/api/v1/funds/transactions?view=%3Cscript%3E").with(user("JYOTHI01")), 400);

        assertThat(body.toString())
                .doesNotContain("script")
                .doesNotContain("TransactionView");
    }

    @Test
    @DisplayName("detail finds an entry the movements view filters out; a missing one is 404")
    void detailReachesFilteredEntries() throws Exception {
        json(get("/api/v1/funds/transactions/V2").with(user("JYOTHI01")), 200);
        json(get("/api/v1/funds/transactions/NOPE").with(user("JYOTHI01")), 404);
    }

    @Test
    @DisplayName("Rule L8a: the export returns the same view and period as the list")
    void exportMatchesTheList() throws Exception {
        String body = exportBody("?view=ALL_ENTRIES");

        assertThat(body.lines().findFirst()).contains("Date,Description,Type,Reference,Amount,Balance");
        // Two entries plus a header, matching what ALL_ENTRIES returned above.
        assertThat(body.lines().count()).isEqualTo(3);
        // Plain, summable amounts: no symbol, no grouping.
        assertThat(body).contains("5000.00").contains("4950.00");
        assertThat(body).doesNotContain("₹");
    }

    @Test
    @DisplayName("REQ-407: the description column carries language, not a copy key")
    void exportCarriesPlainLanguage() throws Exception {
        // The defect this replaces: the column contained ENTRY_CHARGES and ENTRY_PAYIN — machine
        // keys in the one artifact a trader keeps, saves and gives to someone else. The PRD names
        // illegible history as one of four documented competitor defects.
        String body = exportBody("?view=ALL_ENTRIES");

        assertThat(body).contains("Funds added").contains("Charges");
        assertThat(body)
                .as("no copy key may reach the file")
                .doesNotContain("ENTRY_");
    }

    @Test
    @DisplayName("the export leaves as a download, not as JSON")
    void exportIsADownload() throws Exception {
        MvcResult csv = this.mvc.perform(
                get("/api/v1/funds/statement.csv").with(user("JYOTHI01"))).andReturn();

        assertThat(csv.getResponse().getHeader("Content-Disposition"))
                .isNotNull().contains("attachment").contains(".csv");
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
    }

    @Test
    @DisplayName("PR-32: an account number in an entry fails the export rather than being written")
    void exportFailsRatherThanLeakAnAccountNumber() throws Exception {
        // Failing is the deliberate choice. A redaction would produce a plausible file built from
        // a value that should never have reached that layer, and the upstream defect would go
        // unnoticed. The status matters too: the rows are gathered before any bytes are written,
        // so this is a clean 400 rather than a truncated file with a 200.
        ApiTestConfiguration.LEDGER.clear();
        ApiTestConfiguration.LEDGER.add(entry("501234567890", "R", 0L, 100L, 100L, "Fund transfer received"));

        MvcResult res = this.mvc.perform(
                get("/api/v1/funds/statement.csv?view=ALL_ENTRIES").with(user("JYOTHI01"))).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(res.getResponse().getContentAsString()).doesNotContain("501234567890");
    }

    // ---- harness ----

    /**
     * The body of a streamed export.
     *
     * <p><b>Async dispatch is required, and its absence made this test flaky.</b>
     * {@code StreamingResponseBody} completes asynchronously, so reading the response straight
     * after {@code andReturn()} races the writer — it passed when the class ran alone and returned
     * an empty body when it ran alongside others. Passing by timing is worse than failing.
     */
    private String exportBody(String query) throws Exception {
        MvcResult started = this.mvc.perform(
                get("/api/v1/funds/statement.csv" + query).with(user("JYOTHI01"))).andReturn();

        assertThat(started.getResponse().getStatus()).isEqualTo(200);
        assertThat(started.getRequest().isAsyncStarted()).isTrue();

        return this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .asyncDispatch(started))
                .andReturn().getResponse().getContentAsString();
    }

    private static LedgerEntry entry(String voucher, String transType, long debit, long credit,
                                     long closing, String narration) {
        return new LedgerEntry(voucher, "NSE_CASH", TODAY.minusDays(1),
                Money.ofPaise(debit), Money.ofPaise(credit), Money.ofPaise(closing),
                narration, transType, null, null, null, false, null, null);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r,
                          int expectedStatus) throws Exception {
        MvcResult result = this.mvc.perform(r).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
    }
}
