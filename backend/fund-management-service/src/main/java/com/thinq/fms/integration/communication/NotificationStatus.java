package com.thinq.fms.integration.communication;

import java.util.Objects;
import java.util.Optional;

/**
 * What a status read reports (caller-integration.md §7).
 *
 * <p>Previously this system read only {@code status} and inferred the rest. Two of the fields
 * below are answers the service now gives directly, and inferring what can be read is how a system
 * ends up disagreeing with its own supplier.
 *
 * @param status       the current status
 * @param stuck        <b>the hand-off started and never finished, and a human needs to look.</b>
 *                     The reconciler previously derived this from a poll window, which guesses at
 *                     something the platform already knows
 * @param addressKnown whether an address was ever recorded. See {@link #provenNonSend()}
 * @param recipientMask a mask, never the address. Null when none was recorded
 */
public record NotificationStatus(
        DeliveryStatus status,
        boolean stuck,
        boolean addressKnown,
        String recipientMask) {

    public NotificationStatus {
        Objects.requireNonNull(status, "status");
    }

    /**
     * Proof that nothing was sent.
     *
     * <p>§7 is explicit that this is a <i>positive</i> statement rather than an absence:
     * {@code address_known: false} means no address was ever recorded, so the provider was never
     * contacted. Together with a null mask it is proof of a non-send — stronger than a null mask
     * alone, which could mean either "not sent" or "sent, mask not recorded".
     *
     * <p>That distinction matters for a regulatory intimation: "we have no evidence it arrived" and
     * "we know it never left" are different findings, and only the second is actionable.
     */
    public boolean provenNonSend() {
        return !this.addressKnown && this.recipientMask == null;
    }

    public Optional<String> recipientMaskIfAny() {
        return Optional.ofNullable(this.recipientMask);
    }
}
