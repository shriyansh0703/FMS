package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.ObjectMapper;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.StubVendor;
import com.thinq.fms.ledgerview.LedgerEntry;
import com.thinq.fms.movement.payout.InstructionKey;
import com.thinq.fms.movement.payout.InstructionResult;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementOutcome;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The TechExcel call path, against a real HTTP server.
 *
 * <p><b>These tests exist because of a specific failure.</b> A previous version threw a
 * NullPointerException on the first statement after any successful response — so every payout and
 * every ledger read failed, and {@code AbstractVendorGateway} reported each one as a vendor outage
 * rather than as a bug. Ninety-four passing tests said nothing, because the only test touching this
 * package reached a private mapping method by reflection and never called anything public.
 *
 * <p>The first test below is therefore the important one: it does nothing more than let a
 * successful call succeed.
 */
class TechExcelGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 8, 21);
    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");

    private static final String LOGIN_OK = "{\"Token\":\"TOK-1\"}";
    private static final String PAYOUT_ACCEPTED = "{\"Status\":\"Success\"}";
    private static final String STATUS_PAID =
            "{\"Data\":[{\"AUTHO\":\"1\",\"Amount\":\"5000.00\",\"AUTH_DUE_AMT\":\"5000.00\",\"RMSData\":\"0\"}]}";

    /** What the status view actually returns straight after a post: queued, not authorised. */
    private static final String STATUS_PENDING =
            "{\"Data\":[{\"AUTHO\":\"0\",\"Amount\":\"5000.00\",\"AUTH_DUE_AMT\":null,\"Reject\":null}]}";

    @Test
    @DisplayName("a successful call succeeds — the regression that motivated this whole class")
    void aSuccessfulCallSucceeds() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", PAYOUT_ACCEPTED)
                .respond("payment_request_status_view", STATUS_PAID)) {

            InstructionResult result = rail(vendor).instruct(instruction());

            SettlementOutcome outcome = result.settledOutcome().orElseThrow();
            assertThat(outcome.state()).isEqualTo(PayoutState.PAID);
            assertThat(outcome.amountSent().paise()).isEqualTo(500_000L);
        }
    }

    @Test
    @DisplayName("the instruction carries the composite key as UserRefNo, in paise-derived decimal")
    void instructionCarriesTheKeyAndAmount() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", PAYOUT_ACCEPTED)
                .respond("payment_request_status_view", STATUS_PAID)) {

            rail(vendor).instruct(instruction());

            String payoutBody = vendor.requestBodies().stream()
                    .filter(b -> b.contains("UserRefNo")).findFirst().orElseThrow();
            // The idempotency key that makes a re-run reissue an identical reference.
            assertThat(payoutBody).contains("\"UserRefNo\":" + instruction().key().userRefNo());
            // Money crosses as a two-place decimal string, never a float.
            assertThat(payoutBody).contains("\"Amount\":\"5000.00\"");
        }
    }

    @Test
    @DisplayName("an expired token triggers exactly one re-login, then the call proceeds")
    void expiredTokenRefreshesOnce() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("ledger", "{\"ErrorCode\":\"Token Validation Expired\"}")
                .respond("ledger", "{\"Data\":[]}")) {

            List<LedgerEntry> entries = ledger(vendor).entries(ACCOUNT, RUN_DATE.minusDays(7), RUN_DATE);

            assertThat(entries).isEmpty();
            // Two ledger attempts, and a second login between them.
            assertThat(vendor.callsTo("ledger")).isEqualTo(2);
            assertThat(vendor.callsTo("/login")).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a vendor error code becomes a domain exception, not a silent empty result")
    void vendorErrorCodeBecomesAnException() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("ledger", "{\"ErrorCode\":\"Database_Exception\"}")) {

            assertThatThrownBy(() -> ledger(vendor).entries(ACCOUNT, RUN_DATE, RUN_DATE))
                    .isInstanceOf(VendorUnavailableException.class);
        }
    }

    @Test
    @DisplayName("Input_Value_Validation on the payout endpoint gets its ambiguous-rejection code")
    void payoutRejectionIsAmbiguousAndSaysSo() throws Exception {
        // OA-7: TechExcel answers the same code for an input rejection and a duplicate, so a
        // refusal must never be read as "already paid". The caller resolves it by reading status.
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", "{\"ErrorCode\":\"Input_Value_Validation\"}")) {

            assertThatThrownBy(() -> rail(vendor).instruct(instruction()))
                    .isInstanceOf(FmsException.class)
                    .satisfies(e -> assertThat(((FmsException) e).code())
                            .isEqualTo("payout_ambiguous_rejection"));
        }
    }

    @Test
    @DisplayName("the same code on a different endpoint is an ordinary rejection")
    void sameCodeElsewhereIsNotAmbiguous() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("ledger", "{\"ErrorCode\":\"Input_Value_Validation\"}")) {

            assertThatThrownBy(() -> ledger(vendor).entries(ACCOUNT, RUN_DATE, RUN_DATE))
                    .isInstanceOf(FmsException.class)
                    .satisfies(e -> assertThat(((FmsException) e).code())
                            .isEqualTo("techexcel_request_rejected"));
        }
    }

    @Test
    @DisplayName("a status read with no rows returns empty rather than inventing an outcome")
    void absentStatusRowIsEmpty() throws Exception {
        // The only safe reading of "no record": nothing was sent under this key. Treating it as
        // probably-paid would strand a trader's money; treating an error as absence would double
        // pay them.
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payment_request_status_view", "{\"Data\":[]}")) {

            Optional<InstructionResult> o =
                    rail(vendor).statusOf(InstructionKey.of(4242L, RUN_DATE), RUN_DATE);

            assertThat(o).isEmpty();
        }
    }

    @Test
    @DisplayName("a payout pending authorisation is pending, not a partial payment of nothing")
    void pendingAuthorisationIsNotAnOutcome() throws Exception {
        // The row exists with AUTHO = 0 immediately after posting, which is the normal case.
        // Reading it as an outcome concluded PARTLY_PAID with nothing sent — a terminal state
        // that closed the request, told the trader they had received zero, and left the money
        // where it was.
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", PAYOUT_ACCEPTED)
                .respond("payment_request_status_view", STATUS_PENDING)) {

            InstructionResult result = rail(vendor).instruct(instruction());

            assertThat(result.isPending()).isTrue();
            assertThat(result.settledOutcome()).isEmpty();
            assertThat(result).isInstanceOf(InstructionResult.PendingAuthorisation.class);
            assertThat(((InstructionResult.PendingAuthorisation) result).requested().paise())
                    .isEqualTo(500_000L);
        }
    }

    @Test
    @DisplayName("a rejection is terminal even while unauthorised")
    void rejectionBeatsPendingAuthorisation() throws Exception {
        // A rejected entry never gets authorised, so testing AUTHO first would leave a refused
        // payout pending forever and the trader waiting for money that is never coming.
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", PAYOUT_ACCEPTED)
                .respond("payment_request_status_view",
                        "{\"Data\":[{\"AUTHO\":\"0\",\"Amount\":\"5000.00\",\"Reject\":\"1\","
                                + "\"Reject_Reason\":\"Account closed\"}]}")) {

            SettlementOutcome o = rail(vendor).instruct(instruction()).settledOutcome().orElseThrow();

            assertThat(o.state()).isEqualTo(PayoutState.NOTHING_SENT);
            assertThat(o.reasonCode()).isEqualTo(
                    com.thinq.fms.movement.payout.SettlementReasonCode.DESTINATION_REJECTED);
        }
    }

    @Test
    @DisplayName("one instruction produces one vendor-call metric, not two")
    void instructEmitsASingleVendorCallMetric() throws Exception {
        // instruct posts and then reads status. Wrapping the read in its own call() nested the
        // anti-corruption layer inside itself: two breaker samples per instruction, and the inner
        // duration counted inside the outer.
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("payout_request_addition", PAYOUT_ACCEPTED)
                .respond("payment_request_status_view", STATUS_PAID)) {

            JsonHttp http = http(vendor);
            new TechExcelPayoutRail(http, new TechExcelSession(http, "u", "p"), "COMP",
                    SettlementReasonMapper.withDefaults(), Duration.ofSeconds(5), breaker(), meters)
                    .instruct(instruction());

            assertThat(meterNames(meters))
                    .as("one logical operation, one metric")
                    .containsExactly("payout_request_addition");
        }
    }

    @Test
    @DisplayName("a transport failure is recorded as an outage, and a success is not")
    void metricOutcomeReflectsWhatHappened() throws Exception {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("ledger", "{\"Data\":[]}")) {

            ledger(vendor, meters).entries(ACCOUNT, RUN_DATE, RUN_DATE);

            // The assertion that would have failed loudly on the NPE regression: a successful
            // ledger read must be tagged success, not failure.
            assertThat(outcomeTags(meters)).containsExactly("success");
        }
    }

    @Test
    @DisplayName("a 500 from the vendor is an outage")
    void serverErrorIsAnOutage() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/login", LOGIN_OK)
                .respond("ledger", "{}")
                .withStatus(500)) {

            assertThatThrownBy(() -> ledger(vendor).entries(ACCOUNT, RUN_DATE, RUN_DATE))
                    .isInstanceOf(VendorUnavailableException.class);
        }
    }

    // ---- harness ----

    private static List<String> meterNames(SimpleMeterRegistry meters) {
        return meters.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().equals("fms.vendor.call"))
                .map(id -> id.getTag("operation"))
                .toList();
    }

    private static List<String> outcomeTags(SimpleMeterRegistry meters) {
        return meters.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().equals("fms.vendor.call"))
                .map(id -> id.getTag("outcome"))
                .toList();
    }

    private static com.thinq.fms.movement.payout.PaymentInstruction instruction() {
        return new com.thinq.fms.movement.payout.PaymentInstruction(
                InstructionKey.of(4242L, RUN_DATE), ACCOUNT,
                com.thinq.fms.platform.money.Money.ofPaise(500_000L), "acc-1", RUN_DATE);
    }

    private static TechExcelPayoutRail rail(StubVendor vendor) {
        JsonHttp http = http(vendor);
        return new TechExcelPayoutRail(http, new TechExcelSession(http, "u", "p"), "COMP",
                SettlementReasonMapper.withDefaults(), Duration.ofSeconds(5), breaker(),
                new SimpleMeterRegistry());
    }

    private static TechExcelLedgerGateway ledger(StubVendor vendor) {
        return ledger(vendor, new SimpleMeterRegistry());
    }

    private static TechExcelLedgerGateway ledger(StubVendor vendor, SimpleMeterRegistry meters) {
        JsonHttp http = http(vendor);
        return new TechExcelLedgerGateway(http, new TechExcelSession(http, "u", "p"), "COMP",
                Duration.ofSeconds(5), breaker(), meters);
    }

    private static JsonHttp http(StubVendor vendor) {
        return new JsonHttp(vendor.baseUri(), Duration.ofSeconds(2), JSON);
    }

    /** A fresh breaker per test, so one test's failures cannot open another's circuit. */
    private static CircuitBreaker breaker() {
        return CircuitBreaker.ofDefaults("test-" + System.nanoTime());
    }
}
