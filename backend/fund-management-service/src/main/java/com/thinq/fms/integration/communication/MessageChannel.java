package com.thinq.fms.integration.communication;

import java.util.Locale;

/**
 * A channel the Communication Service can send on.
 *
 * <p>The wire values are lowercase because the service's are, and mapping case at the boundary
 * rather than at the call site keeps the enum usable in Java's own conventions.
 */
public enum MessageChannel {

    /** Rule C1's minimum pair, with email. */
    SMS("sms"),

    /** Rule C1's minimum pair, with SMS. Carries the arithmetic REQ-603 requires shown. */
    EMAIL("email"),

    /**
     * Modelled, and not yet usable.
     *
     * <p>OA-2: FMS's grant for this channel is unconfirmed (TASK-02), and the service refuses a
     * channel outside a caller's permitted list with {@code 403 channel_not_permitted}. It is
     * modelled here so that turning it on is configuration rather than a code change, and it is
     * deliberately absent from V26's {@code fms_msg_channel_vocabulary} constraint so a delivery
     * row cannot exist for a message that could never be submitted.
     */
    WHATSAPP("whatsapp");

    private final String wireValue;

    MessageChannel(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return this.wireValue;
    }

    public static MessageChannel fromWire(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (MessageChannel c : values()) {
            if (c.wireValue.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException("unknown channel: " + raw);
    }
}
