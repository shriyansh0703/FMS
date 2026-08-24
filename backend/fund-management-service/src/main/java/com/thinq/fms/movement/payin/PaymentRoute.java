package com.thinq.fms.movement.payin;

/**
 * A rail money can arrive on.
 *
 * <p>Only routes this system can <b>execute</b> are here. Rule A9d is explicit that a self-service
 * route is never offered, because the button would promise a payment and deliver instructions —
 * so a rail the trader would have to complete in their own banking app has no value in this enum.
 */
public enum PaymentRoute {

    /** Capped by NPCI, and individual banks may enforce a lower ceiling this system cannot see. */
    UPI,

    /** Net banking. Carries {@code nbFee}, which is ₹0 in this phase and absorbed. */
    NET_BANKING,

    /** No ceiling on the rail itself. */
    NEFT
}
