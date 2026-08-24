package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a trader may control, what they may not, and who is still reachable (REQ-624, 626, 627).
 *
 * <p><b>Rule C13 is enforced by the shape of this type.</b> There is no method that answers "may I
 * send a shortfall SMS", because the question has no legitimate caller — a preference must never
 * suppress a regulatory intimation. {@link #controllable()} names the channels a preference surface
 * may offer, and REQ-626 requires the surface state which messages cannot be turned off rather than
 * showing a control that does nothing.
 *
 * @param whatsappOptIn   the opt-in with its provenance, absent where none was given
 * @param optionalEmailOn whether non-regulatory email is wanted; regulatory email goes regardless
 * @param emailBouncing   the delivery log says email is not arriving (REQ-627)
 */
public record ChannelPreferences(Optional<WhatsappOptIn> whatsappOptIn,
                                 boolean optionalEmailOn,
                                 boolean emailBouncing) {

    /**
     * An explicit opt-in, with the provenance REQ-624 requires recorded.
     *
     * <p>The surface matters as much as the date: an opt-in captured during onboarding and one
     * captured in a settings screen are different consents, and a dispute turns on which was given.
     */
    public record WhatsappOptIn(LocalDate capturedOn, String capturedVia) {
        public WhatsappOptIn {
            Objects.requireNonNull(capturedOn, "capturedOn");
            Objects.requireNonNull(capturedVia, "capturedVia");
            if (capturedVia.isBlank()) {
                throw new IllegalArgumentException(
                        "REQ-624 requires the surface the opt-in was captured on; an opt-in with no "
                                + "provenance cannot be defended if the trader disputes it");
            }
        }
    }

    public ChannelPreferences {
        Objects.requireNonNull(whatsappOptIn, "whatsappOptIn");
    }

    /** Nothing opted in, email working. The default for every account until REQ-624 captures one. */
    public static ChannelPreferences defaults() {
        return new ChannelPreferences(Optional.empty(), true, false);
    }

    /** REQ-624: WhatsApp only where an explicit opt-in exists. */
    public boolean whatsappOptedIn() {
        return this.whatsappOptIn.isPresent();
    }

    /**
     * The channels a preference surface may offer controls for (REQ-626).
     *
     * <p>SMS is deliberately absent and always will be: Rule C13 makes the shortfall intimation and
     * dues messaging mandatory on it, so a control would be one that has no effect.
     */
    public static Set<MessageChannel> controllable() {
        return Set.of(MessageChannel.WHATSAPP, MessageChannel.EMAIL);
    }

    /**
     * Whether SMS is the only channel that will actually reach this trader (REQ-627).
     *
     * <p>Bouncing email with no WhatsApp opt-in means the ladder's richer steps are going nowhere,
     * and the trader looks unreachable while appearing to have been contacted three times. Support
     * needs to see it, and the funds-screen banner must not be dismissible while it holds.
     */
    public boolean smsOnlyReachable() {
        return this.emailBouncing && !whatsappOptedIn();
    }

    /**
     * Whether a non-regulatory message may go on this channel.
     *
     * <p>Regulatory messages do not ask. They are queued by {@link MessageLadder} without consulting
     * this type at all, which is why no caller can accidentally route one through here.
     */
    public boolean optionalMessagePermitted(MessageChannel channel) {
        Objects.requireNonNull(channel, "channel");
        return switch (channel) {
            case WHATSAPP -> whatsappOptedIn();
            case EMAIL -> this.optionalEmailOn;
            case SMS -> false;
        };
    }
}
