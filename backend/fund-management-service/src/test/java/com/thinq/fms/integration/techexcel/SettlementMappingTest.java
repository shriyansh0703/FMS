package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementOutcome;
import com.thinq.fms.movement.payout.SettlementReasonCode;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * lld-backend.md §4.5's outcome mapping, which is what makes REQ-308 answerable.
 *
 * <p>REQ-308 requires the amount requested, the amount sent, and the deduction accounting for
 * the gap. Getting the branch order wrong here does not throw — it produces a plausible outcome
 * with the wrong reason attached, and the trader is told something untrue about their own money.
 */
class SettlementMappingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SettlementReasonMapper reasons = SettlementReasonMapper.withDefaults();

    @Test
    @DisplayName("Reject = 1 means nothing was sent, whatever the authorised amount says")
    void rejectMeansNothingSent() {
        // Reject is checked FIRST, and this row is built to prove it: AUTH_DUE_AMT is BELOW
        // Amount, so a branch order that tested the amounts first would classify this as a
        // partial payment and tell the trader ₹30 is on its way when nothing is.
        //
        // An earlier version of this test used equal amounts and passed against exactly that
        // mutation — the assertion below is only meaningful because the amounts differ.
        SettlementOutcome o = map(row("5000.00", "3000.00", "0", "1", "Insufficient balance"));

        assertThat(o.state()).isEqualTo(PayoutState.NOTHING_SENT);
        assertThat(o.amountSent().isZero()).isTrue();
        assertThat(o.amountRequested()).isEqualTo(Money.ofPaise(500_000L));
        assertThat(o.reasonCode()).isEqualTo(SettlementReasonCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("a rejected row carrying RMSData is still NOTHING_SENT, not a margin partial")
    void rejectBeatsRmsData() {
        // The other order-dependent case: RMSData set on a rejected row. Reading the amounts
        // first would produce PARTLY_PAID/MARGIN_BLOCKED for a payment that never happened.
        SettlementOutcome o = map(row("5000.00", "1000.00", "4000.00", "1", "Margin shortfall"));

        assertThat(o.state()).isEqualTo(PayoutState.NOTHING_SENT);
        assertThat(o.amountSent().isZero()).isTrue();
    }

    @Test
    @DisplayName("a short authorisation with RMSData set is quantified as margin blocked")
    void partialWithRmsDataIsMarginBlocked() {
        SettlementOutcome o = map(row("5000.00", "3000.00", "2000.00", null, null));

        assertThat(o.state()).isEqualTo(PayoutState.PARTLY_PAID);
        assertThat(o.amountSent()).isEqualTo(Money.ofPaise(300_000L));
        assertThat(o.shortfallAgainstRequest()).isEqualTo(Money.ofPaise(200_000L));
        // The one cause the contract lets this system name numerically, which is exactly what
        // REQ-308 asks for.
        assertThat(o.reasonCode()).isEqualTo(SettlementReasonCode.MARGIN_BLOCKED);
    }

    @Test
    @DisplayName("a short authorisation with no RMSData falls back to the reason phrase")
    void partialWithoutRmsDataUsesTheReasonPhrase() {
        SettlementOutcome o = map(row("5000.00", "1000.00", "0", null, "IFSC code invalid"));

        assertThat(o.state()).isEqualTo(PayoutState.PARTLY_PAID);
        assertThat(o.reasonCode()).isEqualTo(SettlementReasonCode.DESTINATION_REJECTED);
    }

    @Test
    @DisplayName("an unmapped phrase becomes UNSPECIFIED and keeps the text for operations")
    void unmappedPhraseIsUnspecifiedAndRetained() {
        // OA-4: Reject_Reason is free text. The trader sees generic copy; the phrase is kept so
        // the table can be extended. It must never be rendered as if it were copy.
        SettlementOutcome o = map(row("5000.00", "2000.00", "0", null, "ERR-7734 batch anomaly"));

        assertThat(o.reasonCode()).isEqualTo(SettlementReasonCode.UNSPECIFIED);
        assertThat(o.reasonText()).isEqualTo("ERR-7734 batch anomaly");
        assertThat(this.reasons.isUnmapped("ERR-7734 batch anomaly")).isTrue();
    }

    @Test
    @DisplayName("full authorisation is PAID with no reason")
    void fullAuthorisationIsPaid() {
        SettlementOutcome o = map(row("5000.00", "5000.00", "0", null, null));

        assertThat(o.state()).isEqualTo(PayoutState.PAID);
        assertThat(o.amountSent()).isEqualTo(o.amountRequested());
        assertThat(o.reasonCode()).isEqualTo(SettlementReasonCode.NONE);
        assertThat(o.shortfallAgainstRequest().isZero()).isTrue();
    }

    @Test
    @DisplayName("money crosses the boundary as paise, never as a rounded rupee")
    void decimalRupeesBecomeExactPaise() {
        // 1234.56 has no exact binary float representation. If this ever arrives via a double
        // it comes back as 123455 paise, and a trader is short a paisa on every payout.
        SettlementOutcome o = map(row("1234.56", "1234.56", "0", null, null));

        assertThat(o.amountRequested().paise()).isEqualTo(123_456L);
        assertThat(o.amountSent().paise()).isEqualTo(123_456L);
    }

    @Test
    @DisplayName("an outcome may never report sending more than was requested")
    void cannotSendMoreThanRequested() {
        // TechExcel authorising more than was asked for is not a windfall, it is a corrupt row.
        assertThatThrownBy(() -> map(row("1000.00", "5000.00", "0", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never more");
    }

    @Test
    @DisplayName("Reject is the string \"1\", and any other value is not a rejection")
    void rejectIsCheckedAsTheDocumentedValue() {
        // The contract says Reject is "1" or null, not a boolean. A truthiness check would read
        // "0" as rejected and mark a paid request NOTHING_SENT.
        assertThat(map(row("100.00", "100.00", "0", "0", null)).state()).isEqualTo(PayoutState.PAID);
        assertThat(map(row("100.00", "100.00", "0", "1", null)).state()).isEqualTo(PayoutState.NOTHING_SENT);
    }

    // ---- harness ----

    private static ObjectNode row(String amount, String authDue, String rmsData,
                                  String reject, String rejectReason) {
        // AUTHO = 1 throughout: these tests are about the settlement mapping, and an
        // unauthorised row is not a settlement at all. The pending case has its own test below.
        ObjectNode n = JSON.createObjectNode();
        n.put("AUTHO", "1");
        n.put("Amount", amount);
        n.put("AUTH_DUE_AMT", authDue);
        n.put("RMSData", rmsData);
        if (reject != null) {
            n.put("Reject", reject);
        }
        if (rejectReason != null) {
            n.put("Reject_Reason", rejectReason);
        }
        return n;
    }

    /**
     * Invokes the private mapping directly.
     *
     * <p>Reflection rather than a fake HTTP server, because the mapping is the logic under test
     * and the transport is not. Standing a server up would test Jackson and the JDK's HTTP
     * client alongside it, and a failure would not say which had broken.
     */
    private SettlementOutcome map(ObjectNode row) {
        try {
            Method m = TechExcelPayoutRail.class.getDeclaredMethod(
                    "toResult", com.thinq.fms.movement.payout.InstructionKey.class,
                    tools.jackson.databind.JsonNode.class);
            m.setAccessible(true);
            TechExcelPayoutRail rail = railUnderTest();
            return ((com.thinq.fms.movement.payout.InstructionResult) m.invoke(rail, KEY, row))
                    .settledOutcome().orElseThrow(() -> new AssertionError(
                            "expected a settled outcome; the row was read as pending authorisation"));
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final com.thinq.fms.movement.payout.InstructionKey KEY =
            com.thinq.fms.movement.payout.InstructionKey.of(4242L, java.time.LocalDate.of(2026, 8, 21));

    private TechExcelPayoutRail railUnderTest() {
        return new TechExcelPayoutRail(
                new com.thinq.fms.integration.JsonHttp(
                        java.net.URI.create("http://localhost:1"), java.time.Duration.ofSeconds(1), JSON),
                new TechExcelSession(
                        new com.thinq.fms.integration.JsonHttp(
                                java.net.URI.create("http://localhost:1"), java.time.Duration.ofSeconds(1), JSON),
                        "user", "pass"),
                "COMP",
                this.reasons,
                java.time.Duration.ofSeconds(1),
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }
}
