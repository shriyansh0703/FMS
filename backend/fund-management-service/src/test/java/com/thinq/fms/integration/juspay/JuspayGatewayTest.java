package com.thinq.fms.integration.juspay;

import tools.jackson.databind.ObjectMapper;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.StubVendor;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Juspay call path, against a real HTTP server. */
class JuspayGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");

    @Test
    @DisplayName("a created order returns its payment link and starts as awaiting")
    void createOrderSucceeds() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/orders",
                "{\"status\":\"NEW\",\"id\":\"ord_juspay_1\",\"order_id\":\"FMS-1\","
                        + "\"payment_links\":{\"web\":\"https://pay.example/1\"}}")) {

            JuspayOrder order = gateway(vendor).createOrder("FMS-1", ACCOUNT, Money.ofPaise(250_000L), null);

            assertThat(order.juspayId()).isEqualTo("ord_juspay_1");
            assertThat(order.paymentLinkIfPresent()).contains("https://pay.example/1");
            // Rule A9b: an in-progress payment is awaiting, never failed.
            assertThat(order.outcome()).isEqualTo(PayinOutcome.AWAITING_BANK);
            assertThat(order.isCreditable()).isFalse();
        }
    }

    @Test
    @DisplayName("the amount crosses as an exact decimal, and no personal data goes with it")
    void requestCarriesAmountExactlyAndNothingPersonal() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/orders",
                "{\"status\":\"NEW\",\"id\":\"o1\",\"order_id\":\"FMS-1\"}")) {

            gateway(vendor).createOrder("FMS-1", ACCOUNT, Money.ofPaise(123_456L), null);

            String body = vendor.requestBodies().get(0);
            // BigDecimal serialised as a number: 1234.56 exactly, not 1234.5599999999999.
            assertThat(body).contains("\"amount\":1234.56");
            assertThat(body).contains("\"customer_id\":\"JYOTHI01\"");
            // Juspay's /orders accepts 113 fields, most of them address and contact components.
            // None of them is sent, so none of them can leak from a gateway's logs.
            assertThat(body)
                    .doesNotContain("customer_email")
                    .doesNotContain("customer_phone")
                    .doesNotContain("billing_address");
        }
    }

    @Test
    @DisplayName("a CHARGED order is creditable; every other known status is not")
    void onlyChargedIsCreditable() throws Exception {
        assertThat(statusWith("CHARGED").isCreditable()).isTrue();
        assertThat(statusWith("AUTHORIZATION_FAILED").outcome()).isEqualTo(PayinOutcome.BANK_DECLINED);
        assertThat(statusWith("PENDING_VBV").outcome()).isEqualTo(PayinOutcome.AWAITING_BANK);
    }

    @Test
    @DisplayName("an unrecognised status is unknown, never failed")
    void unrecognisedStatusIsUnknownNotFailed() throws Exception {
        // Rule A9b, and the safer error: a status this system has not seen is far more likely to
        // be a new success variant than a new failure, and telling a trader their payment failed
        // when it succeeded is the more expensive mistake.
        JuspayOrder order = statusWith("SOME_NEW_STATUS");

        assertThat(order.outcome()).isEqualTo(PayinOutcome.UNKNOWN);
        assertThat(order.outcome().isAwaitingResolution()).isTrue();
        assertThat(order.outcome().mayRetry()).as("never offer a retry on an unknown outcome").isFalse();
        // Retained verbatim so the mapping table can be corrected. Never rendered to a trader.
        assertThat(order.rawStatus()).isEqualTo("SOME_NEW_STATUS");
    }

    @Test
    @DisplayName("a 400 is this system's bug, not an outage")
    void badRequestIsOursNotTheirs() throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/orders", "{\"status\":\"error\",\"error_code\":\"invalid_amount\"}")
                .withStatus(400)) {

            assertThatThrownBy(() -> gateway(vendor).createOrder("FMS-1", ACCOUNT, Money.ofPaise(1L), null))
                    .isInstanceOf(FmsException.class)
                    .satisfies(e -> assertThat(((FmsException) e).code()).isEqualTo("juspay_request_rejected"));
        }
    }

    @Test
    @DisplayName("a 503 is an outage")
    void serviceUnavailableIsAnOutage() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/orders", "{}").withStatus(503)) {
            assertThatThrownBy(() -> gateway(vendor).createOrder("FMS-1", ACCOUNT, Money.ofPaise(1L), null))
                    .isInstanceOf(VendorUnavailableException.class);
        }
    }

    private JuspayOrder statusWith(String status) throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/orders",
                "{\"status\":\"" + status + "\",\"order_id\":\"FMS-1\",\"amount\":2500.00}")) {
            return gateway(vendor).statusOf("FMS-1", ACCOUNT);
        }
    }

    private static JuspayGateway gateway(StubVendor vendor) {
        return new JuspayGateway(new JsonHttp(vendor.baseUri(), Duration.ofSeconds(2), JSON),
                "merch-1", JuspayStatusMapper.withDefaults(), Duration.ofSeconds(5),
                CircuitBreaker.ofDefaults("t-" + System.nanoTime()), new SimpleMeterRegistry());
    }
}
