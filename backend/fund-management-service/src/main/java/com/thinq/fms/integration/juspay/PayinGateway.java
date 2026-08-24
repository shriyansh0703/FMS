package com.thinq.fms.integration.juspay;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

/**
 * The two operations the payin orchestrator needs from a payment gateway.
 *
 * <p>Narrower than {@link JuspayGateway} on purpose, and for the same reason
 * {@code NotificationSubmitter} is narrower than the communication client: the orchestrator depends
 * on the capability rather than on a final class holding an HTTP client, so its behaviour can be
 * tested without a network.
 *
 * <p>It is also the seam that would matter if the gateway ever changed. Juspay is one of several
 * that could carry a payin, and an orchestrator naming the vendor directly would have to be edited
 * to swap it.
 */
public interface PayinGateway {

    /**
     * Create an order the trader can pay against.
     *
     * @param orderId the caller's own reference, which becomes {@code gateway_payment_ref} on the
     *     attempt row — where V22's partial unique index enforces Rule A6, one credit per payment
     */
    JuspayOrder createOrder(String orderId, AccountRef account, Money amount, String returnUrl);

    /** The current state of an order. */
    JuspayOrder statusOf(String orderId, AccountRef account);
}
