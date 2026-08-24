package com.thinq.fms.integration.communication;

import java.util.Locale;

/**
 * The Communication Service's ten-value status vocabulary (caller-integration.md §8).
 *
 * <p>Mirrors V26's {@code fms_msg_status_vocabulary} constraint exactly. The two must stay in
 * step: a value this enum accepts and the constraint rejects is a delivery row that cannot be
 * written, and the reverse is a status nothing can render.
 */
public enum DeliveryStatus {

    /** Durably queued. Nothing has been sent. */
    ACCEPTED("accepted", false),
    /** A worker has taken it and is handing it off. */
    CLAIMED("claimed", false),
    /** Handed to the provider, which accepted it. No delivery report yet. */
    DISPATCHED("dispatched", false),
    /** Terminal. Never sent, and nothing will retry it. */
    FAILED("failed", true),
    /** The provider reports it left their system. */
    SENT("sent", false),

    /**
     * The provider reports it reached the recipient — <b>on email only</b>.
     *
     * <p>On SMS this means the vendor accepted the message, not that a handset received it: the
     * aggregator publishes no delivery-report mechanism at all, and acceptance is recorded as
     * {@code delivered} with a {@code SYNTHETIC_ACCEPT_NO_DLR} marker. See
     * {@link #provesReceipt(MessageChannel)} — no decision may rest on this value for SMS,
     * including whether a regulatory intimation obligation was met.
     */
    DELIVERED("delivered", true),

    /** Rejected by the receiving system. Terminal. */
    BOUNCED("bounced", true),
    /** The provider refused it. Terminal. */
    REJECTED("rejected", true),
    /** The provider discarded it, e.g. a suppression list. Terminal. */
    DROPPED("dropped", true),
    /** Never confirmed within the window. */
    EXPIRED("expired", true);

    private final String wireValue;
    private final boolean terminal;

    DeliveryStatus(String wireValue, boolean terminal) {
        this.wireValue = wireValue;
        this.terminal = terminal;
    }

    public String wireValue() {
        return this.wireValue;
    }

    /**
     * Whether the service will report anything further about this notification.
     *
     * <p>Distinct from {@link #needsNoFurtherAction()}, and the two are easy to conflate. This one
     * is about the <i>service</i>: will another status arrive? That is a fact about the provider.
     */
    public boolean isTerminal() {
        return this.terminal;
    }

    /**
     * Whether <b>this system</b> is finished with the notification.
     *
     * <p>Not the same question as {@link #isTerminal()}. {@code SENT} is non-terminal — the
     * service may yet report a delivery — but the provider has the message and nothing further is
     * owed, so the reconciler stops. {@code FAILED} is terminal and very much needs action.
     *
     * <p>The two notions were previously distinguished only by an {@code if} inside the
     * reconciler, which invited the next reader to "fix" the apparent inconsistency in the wrong
     * direction.
     */
    public boolean needsNoFurtherAction() {
        return this == DELIVERED || this == SENT;
    }

    /**
     * Whether this status is evidence the recipient actually received the message.
     *
     * <p>A method rather than a property of the status, because the answer depends on the
     * channel: {@code delivered} is a genuine provider report on email and a synthetic vendor
     * acceptance on SMS. Making callers pass the channel is what stops the SMS case being read
     * as proof by a flow that only ever tested email.
     */
    public boolean provesReceipt(MessageChannel channel) {
        return this == DELIVERED && channel == MessageChannel.EMAIL;
    }

    public static DeliveryStatus fromWire(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (DeliveryStatus s : values()) {
            if (s.wireValue.equals(v)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown delivery status: " + raw);
    }
}
