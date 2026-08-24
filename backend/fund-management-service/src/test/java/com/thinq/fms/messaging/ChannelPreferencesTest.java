package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/** REQ-624, 626 and 627 — what may be controlled, and who is still reachable. */
class ChannelPreferencesTest {

    private static final ChannelPreferences.WhatsappOptIn OPTED_IN =
            new ChannelPreferences.WhatsappOptIn(LocalDate.of(2026, 8, 1), "settings");

    @Test
    @DisplayName("WhatsApp goes only where an explicit opt-in exists")
    void whatsappNeedsAnExplicitOptIn() {
        assertThat(ChannelPreferences.defaults().whatsappOptedIn()).isFalse();
        assertThat(new ChannelPreferences(Optional.of(OPTED_IN), true, false).whatsappOptedIn())
                .isTrue();
    }

    @Test
    @DisplayName("an opt-in without its capture surface is refused")
    void anOptInWithoutProvenanceIsRefused() {
        // REQ-624 requires the date and the surface. An opt-in captured during onboarding and one
        // captured in settings are different consents, and a dispute turns on which was given.
        assertThatThrownBy(() -> new ChannelPreferences.WhatsappOptIn(LocalDate.of(2026, 8, 1), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SMS is not offered as a control, because it would have no effect")
    void smsIsNotControllable() {
        // Rule C13 makes the shortfall intimation and dues messaging mandatory on SMS. REQ-626
        // requires the surface say so rather than present a control that does nothing.
        assertThat(ChannelPreferences.controllable())
                .containsExactlyInAnyOrder(MessageChannel.WHATSAPP, MessageChannel.EMAIL)
                .doesNotContain(MessageChannel.SMS);
    }

    @Test
    @DisplayName("no preference can permit an optional message on SMS")
    void noPreferencePermitsAnOptionalSms() {
        // The channel is reserved for messages that do not ask. Answering true here would be the
        // first step toward a preference gating a regulatory message.
        ChannelPreferences everythingOn = new ChannelPreferences(Optional.of(OPTED_IN), true, false);

        assertThat(everythingOn.optionalMessagePermitted(MessageChannel.SMS)).isFalse();
    }

    @Test
    @DisplayName("optional email can be turned off without affecting anything regulatory")
    void optionalEmailCanBeTurnedOff() {
        ChannelPreferences off = new ChannelPreferences(Optional.empty(), false, false);

        assertThat(off.optionalMessagePermitted(MessageChannel.EMAIL)).isFalse();
        assertThat(off.optionalMessagePermitted(MessageChannel.WHATSAPP)).isFalse();
    }

    @Test
    @DisplayName("bouncing email with no opt-in leaves SMS the only reachable channel")
    void bouncingEmailWithNoOptInLeavesSmsOnly() {
        // REQ-627. The ladder's richer steps go nowhere and the trader looks contacted three times
        // while being unreachable, which is what support needs flagged.
        ChannelPreferences bouncing = new ChannelPreferences(Optional.empty(), true, true);

        assertThat(bouncing.smsOnlyReachable()).isTrue();
    }

    @Test
    @DisplayName("a WhatsApp opt-in keeps the account reachable even while email bounces")
    void anOptInKeepsTheAccountReachable() {
        ChannelPreferences bouncingButOptedIn =
                new ChannelPreferences(Optional.of(OPTED_IN), true, true);

        assertThat(bouncingButOptedIn.smsOnlyReachable()).isFalse();
    }

    @Test
    @DisplayName("working email is not an SMS-only account")
    void workingEmailIsNotSmsOnly() {
        assertThat(ChannelPreferences.defaults().smsOnlyReachable()).isFalse();
    }
}
