package com.thinq.fms.integration.juspay;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.AbstractVendorGateway;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.VendorHttpException;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Juspay, the payment gateway for money coming in.
 *
 * <h2>Two things this class refuses to do</h2>
 *
 * <p><b>It sends no personal data.</b> Juspay's {@code /orders} body accepts 113 documented
 * fields, most of them billing and shipping address components plus customer email and phone.
 * This client populates the payment fields and nothing else. The taxonomy's rule R4 forbids
 * regulated identifiers reaching an event property, and a field that is never sent cannot leak
 * from a gateway's logs either.
 *
 * <p><b>It does not decide that a payment failed.</b> Mapping a status is
 * {@link JuspayStatusMapper}'s job and its unmapped default is "awaiting", per Rule A9b. This
 * class surfaces what the gateway said.
 *
 * <h2>Amount crosses as a decimal, never a double</h2>
 *
 * <p>Juspay types {@code amount} as a JSON number. {@link Money#toVendorDecimal()} produces a
 * {@code BigDecimal}, which Jackson serialises as an exact decimal literal. Passing a
 * {@code double} here would reintroduce binary floating-point error at the one boundary where
 * real money changes hands, which is why {@code Money} offers no double conversion to pass.
 */
public final class JuspayGateway extends AbstractVendorGateway implements PayinGateway {

    private static final String VENDOR = "juspay";
    private static final String ORDERS_PATH = "/orders";

    private final JsonHttp http;
    private final String merchantId;
    private final JuspayStatusMapper statuses;

    public JuspayGateway(JsonHttp http,
                         String merchantId,
                         JuspayStatusMapper statuses,
                         Duration callTimeout,
                         CircuitBreaker circuitBreaker,
                         MeterRegistry meters) {
        super(VENDOR, callTimeout, circuitBreaker, meters);
        this.http = Objects.requireNonNull(http, "http");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
    }

    /**
     * Create an order the trader can pay against.
     *
     * @param orderId the caller's own reference, and the key everything downstream joins on. It
     *     becomes {@code gateway_payment_ref} on the attempt row, where V22's partial unique
     *     index enforces Rule A6 — one credit per payment, however many confirmations arrive
     */
    @Override
    public JuspayOrder createOrder(String orderId, AccountRef account, Money amount, String returnUrl) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a payin order is for a positive amount; got " + amount);
        }

        return call("create_order", () -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("order_id", orderId);
            // BigDecimal, so Jackson writes an exact decimal rather than a float.
            body.put("amount", amount.toVendorDecimal());
            body.put("currency", "INR");
            // The UCC, which the taxonomy's rule R4 permits precisely because it is not a
            // regulated identifier. No email, no phone, no address.
            body.put("customer_id", account.ucc());
            if (returnUrl != null) {
                body.put("return_url", returnUrl);
            }

            JsonNode response = this.http.post(ORDERS_PATH, body, headers(account));
            return toOrder(response, orderId, amount);
        });
    }

    /** The current state of an order. */
    @Override
    public JuspayOrder statusOf(String orderId, AccountRef account) {
        Objects.requireNonNull(orderId, "orderId");

        return call("order_status", () -> {
            JsonNode response = this.http.get(ORDERS_PATH + "/" + orderId, headers(account));
            return toOrder(response, orderId, null);
        });
    }

    /**
     * {@code x-routing-id} carries the account so Juspay can shard consistently. It is the UCC
     * for the same reason {@code customer_id} is.
     */
    private Map<String, String> headers(AccountRef account) {
        return Map.of("x-merchantid", this.merchantId, "x-routing-id", account.ucc());
    }

    private JuspayOrder toOrder(JsonNode response, String orderId, Money requested) {
        String rawStatus = text(response, "status");
        Money amount = response.hasNonNull("amount")
                // decimalValue keeps the wire literal exact; asDouble would not.
                ? Money.ofVendorDecimal(response.get("amount").decimalValue())
                : requested;

        if (amount == null) {
            throw new FmsInvariantException("juspay_order_without_amount",
                    "Juspay order " + orderId + " reported no amount");
        }

        return new JuspayOrder(
                orderId,
                text(response, "id"),
                text(response, "txn_id"),
                amount,
                rawStatus,
                this.statuses.map(rawStatus),
                paymentLink(response),
                response.path("refunded").asBoolean(false));
    }

    private static String paymentLink(JsonNode response) {
        JsonNode links = response.get("payment_links");
        return links == null || links.isNull() ? null : text(links, "web");
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    @Override
    protected FmsException translate(String operation, Exception e) {
        if (e instanceof VendorHttpException http) {
            // Juspay answers 400 with error_code and error_message for a request this system
            // built wrongly. That is ours, and counting it as an outage would hide it.
            if (http.status() == 400) {
                return new FmsInvariantException("juspay_request_rejected",
                        "Juspay rejected a request this system constructed: " + operation);
            }
            return new VendorUnavailableException(VENDOR,
                    "Juspay returned HTTP " + http.status() + " for " + operation, http);
        }
        return super.translate(operation, e);
    }
}
