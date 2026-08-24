package com.thinq.fms.integration.juspay;

import com.thinq.fms.platform.money.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * One Juspay order, as this system reads it.
 *
 * @param orderId     our own reference, and what {@code gateway_payment_ref} carries
 * @param juspayId    Juspay's internal id for the order
 * @param txnId       the payment attempt's id, where one exists yet
 * @param amount      the order amount, in paise
 * @param rawStatus   Juspay's status string, verbatim. Retained so an unmapped value can be
 *                    alerted on and the mapping table corrected — never rendered to a trader
 * @param outcome     the mapped outcome. {@code UNKNOWN} when {@code rawStatus} did not map
 * @param paymentLink where to send the trader to pay, on a freshly created order
 * @param refunded    whether the order has been completely refunded
 */
public record JuspayOrder(
        String orderId,
        String juspayId,
        String txnId,
        Money amount,
        String rawStatus,
        PayinOutcome outcome,
        String paymentLink,
        boolean refunded) {

    public JuspayOrder {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(outcome, "outcome");
    }

    public Optional<String> paymentLinkIfPresent() {
        return Optional.ofNullable(this.paymentLink);
    }

    /**
     * Whether this system may credit the account.
     *
     * <p>Only on {@code CONFIRMED}. Rule A5: money exists in the balances once it is confirmed
     * and not before, and an in-flight attempt affects no balance.
     */
    public boolean isCreditable() {
        return this.outcome == PayinOutcome.CONFIRMED;
    }
}
