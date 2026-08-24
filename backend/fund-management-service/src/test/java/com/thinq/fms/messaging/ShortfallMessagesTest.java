package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/** REQ-602, REQ-603, Rule C16 and Rule H7. */
class ShortfallMessagesTest {

    private static final Money REQUIREMENT = Money.ofPaise(1_000_000L);
    private static final Money AVAILABLE = Money.ofPaise(750_000L);
    private static final Money SHORTFALL = Money.ofPaise(250_000L);

    private MessageSpec step(MessageChannel channel, Optional<Duration> remaining) {
        return ShortfallMessages.forStep("MARGIN_SHORTFALL_STEP_1", channel, REQUIREMENT,
                AVAILABLE, SHORTFALL, remaining, ShortfallMessages.Cause.MARKET_MOVE);
    }

    @ParameterizedTest
    @EnumSource(MessageChannel.class)
    @DisplayName("every channel states the exact amount short")
    void everyChannelStatesTheAmount(MessageChannel channel) {
        // REQ-602: the trader must not be re-entering a figure under a deadline, whichever channel
        // reached them.
        assertThat(step(channel, Optional.of(Duration.ofHours(2))).parameters())
                .containsEntry("shortfall", "2500.00");
    }

    @Test
    @DisplayName("SMS carries no action control at all — Rule C16")
    void smsCarriesNoActionControl() {
        // Unconditional. The consequence is that SMS has to be actionable from its text alone, which
        // is why the amount and the deadline are stated rather than delegated to a control.
        assertThat(step(MessageChannel.SMS, Optional.of(Duration.ofHours(2))).parameters())
                .containsEntry("actionControl", "NONE");
    }

    @Test
    @DisplayName("the richer channels carry the action that resolves the state")
    void richerChannelsCarryTheAction() {
        for (MessageChannel channel : new MessageChannel[]{MessageChannel.EMAIL, MessageChannel.WHATSAPP}) {
            assertThat(step(channel, Optional.of(Duration.ofHours(2))).parameters())
                    .as("%s", channel)
                    .containsEntry("actionControl", "FUND_EXACT_AMOUNT");
        }
    }

    @Test
    @DisplayName("only email carries the breakdown, because only email can")
    void onlyEmailCarriesTheBreakdown() {
        // REQ-603. Putting three figures into an SMS produces a message nobody reads to the end.
        assertThat(step(MessageChannel.EMAIL, Optional.empty()).parameters())
                .containsEntry("requirement", "10000.00")
                .containsEntry("availableMargin", "7500.00")
                .containsEntry("subjectState", "MARGIN_SHORTFALL");

        assertThat(step(MessageChannel.SMS, Optional.empty()).parameters())
                .doesNotContainKeys("requirement", "availableMargin", "subjectState");
    }

    @Test
    @DisplayName("the three figures must reconcile as an arithmetic the trader can follow")
    void theThreeFiguresMustReconcile() {
        assertThatThrownBy(() -> ShortfallMessages.forStep("MARGIN_SHORTFALL_STEP_1",
                MessageChannel.EMAIL, REQUIREMENT, AVAILABLE, Money.ofPaise(1L),
                Optional.empty(), ShortfallMessages.Cause.MARKET_MOVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requirement less the available margin");
    }

    @Test
    @DisplayName("an unknown deadline is said to be unknown rather than omitted")
    void anUnknownDeadlineIsSaidToBeUnknown() {
        // Omitting it would read as no deadline at all, which is the opposite of the situation.
        assertThat(step(MessageChannel.SMS, Optional.empty()).parameters())
                .containsEntry("deadlineKnown", "false")
                .containsEntry("minutesRemaining", "");

        assertThat(step(MessageChannel.SMS, Optional.of(Duration.ofHours(2))).parameters())
                .containsEntry("deadlineKnown", "true")
                .containsEntry("minutesRemaining", "120");
    }

    @ParameterizedTest
    @EnumSource(ShortfallMessages.Cause.class)
    @DisplayName("the cause distinguishes the trader's own doing from a market move")
    void theCauseIsDistinguished(ShortfallMessages.Cause cause) {
        // Rule B8. A trader told they caused a shortfall the market caused will look for a trade
        // they did not make.
        MessageSpec spec = ShortfallMessages.forStep("MARGIN_SHORTFALL_STEP_1",
                MessageChannel.EMAIL, REQUIREMENT, AVAILABLE, SHORTFALL, Optional.empty(), cause);

        assertThat(spec.parameters()).containsEntry("cause", cause.name());
    }

    @Test
    @DisplayName("there is no shortfall message without a shortfall")
    void noMessageWithoutAShortfall() {
        assertThatThrownBy(() -> ShortfallMessages.forStep("MARGIN_SHORTFALL_STEP_1",
                MessageChannel.SMS, Money.ZERO, Money.ZERO, Money.ZERO, Optional.empty(),
                ShortfallMessages.Cause.MARKET_MOVE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
