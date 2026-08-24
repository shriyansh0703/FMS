package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.DeliveryStatus;
import com.thinq.fms.integration.communication.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §7.9: the Communication Service never retries, so this system notices.
 *
 * <p>The subtle assertions here are the two that stop a well-meaning fix making things worse:
 * a message stuck at {@code dispatched} must alert rather than resubmit, and SMS
 * {@code delivered} must never count as proof a trader saw anything.
 */
class DeliveryReconcilerTest {

    private static final Instant SUBMITTED = Instant.parse("2026-08-21T09:00:00Z");
    private final DeliveryReconciler reconciler = new DeliveryReconciler(Duration.ofMinutes(30));

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class,
            names = {"FAILED", "BOUNCED", "REJECTED", "DROPPED", "EXPIRED"})
    @DisplayName("every terminal non-delivery resubmits under a new request id")
    void terminalNonDeliveryResubmits(DeliveryStatus status) {
        assertThat(reconciler.actionFor(status, SUBMITTED, SUBMITTED.plusSeconds(60)))
                .isEqualTo(ReconciliationAction.RESUBMIT);
    }

    @Test
    @DisplayName("a message stuck non-terminal past the window alerts rather than resubmitting")
    void stuckMessageAlertsRatherThanResubmits() {
        // A notification can sit at `dispatched` indefinitely on a channel with no delivery
        // reporting. Resubmitting would send a second copy of a message that very likely arrived,
        // which is why the window produces a human rather than a retry.
        assertThat(reconciler.actionFor(DeliveryStatus.DISPATCHED, SUBMITTED, SUBMITTED.plusSeconds(3600)))
                .isEqualTo(ReconciliationAction.ALERT);

        assertThat(reconciler.actionFor(DeliveryStatus.DISPATCHED, SUBMITTED, SUBMITTED.plusSeconds(600)))
                .isEqualTo(ReconciliationAction.KEEP_POLLING);
    }

    @Test
    @DisplayName("the platform's own stuck flag alerts immediately, without waiting out the window")
    void stuckIsReadRatherThanInferred() {
        // §7: "the hand-off started and never finished. A human needs to look." The reconciler
        // previously derived this from a poll window, which guesses at something the platform
        // already knows — and spends the part of the deadline that was still usable waiting.
        var stuck = new com.thinq.fms.integration.communication.NotificationStatus(
                DeliveryStatus.DISPATCHED, true, true, "p***@thinq.co");

        assertThat(reconciler.actionFor(stuck, SUBMITTED, SUBMITTED.plusSeconds(30)))
                .as("well inside the poll window, and already flagged")
                .isEqualTo(ReconciliationAction.ALERT);
    }

    @Test
    @DisplayName("a settled status wins over stuck — nothing further is owed either way")
    void settledBeatsStuck() {
        // Order matters: a delivered notification flagged stuck is finished, and alerting on it
        // would page someone about a message that arrived.
        var delivered = new com.thinq.fms.integration.communication.NotificationStatus(
                DeliveryStatus.DELIVERED, true, true, null);

        assertThat(reconciler.actionFor(delivered, SUBMITTED, SUBMITTED.plusSeconds(30)))
                .isEqualTo(ReconciliationAction.SETTLED);
    }

    @Test
    @DisplayName("not stuck inside the window still keeps polling")
    void notStuckStillPolls() {
        var progressing = new com.thinq.fms.integration.communication.NotificationStatus(
                DeliveryStatus.DISPATCHED, false, true, null);

        assertThat(reconciler.actionFor(progressing, SUBMITTED, SUBMITTED.plusSeconds(30)))
                .isEqualTo(ReconciliationAction.KEEP_POLLING);
    }

    @Test
    @DisplayName("delivered and sent settle; nothing further is owed")
    void deliveredAndSentSettle() {
        assertThat(reconciler.actionFor(DeliveryStatus.DELIVERED, SUBMITTED, SUBMITTED.plusSeconds(60)))
                .isEqualTo(ReconciliationAction.SETTLED);
        assertThat(reconciler.actionFor(DeliveryStatus.SENT, SUBMITTED, SUBMITTED.plusSeconds(99999)))
                .isEqualTo(ReconciliationAction.SETTLED);
    }

    @Test
    @DisplayName("SMS delivered is never proof the trader saw the message")
    void smsDeliveredIsNotProofOfReceipt() {
        // The aggregator publishes no delivery reports at all; acceptance is recorded as
        // `delivered` with a SYNTHETIC_ACCEPT_NO_DLR marker. No decision may rest on it —
        // including whether a regulatory intimation obligation was met.
        assertThat(reconciler.provesReceipt(DeliveryStatus.DELIVERED, MessageChannel.SMS)).isFalse();
        assertThat(reconciler.provesReceipt(DeliveryStatus.DELIVERED, MessageChannel.EMAIL)).isTrue();
    }

    @Test
    @DisplayName("one channel succeeding makes the intimation, per Rule C1 and §7.9")
    void oneChannelSucceedingMakesTheIntimation() {
        // Requiring both would declare a failure while the trader has in fact been told.
        assertThat(reconciler.intimationMade(DeliveryStatus.FAILED, DeliveryStatus.DELIVERED)).isTrue();
        assertThat(reconciler.intimationMade(DeliveryStatus.DELIVERED, DeliveryStatus.BOUNCED)).isTrue();
        assertThat(reconciler.intimationMade(DeliveryStatus.FAILED, DeliveryStatus.BOUNCED)).isFalse();
    }

    @Test
    @DisplayName("a failed channel is recorded even when the other succeeded")
    void failedChannelIsRecordedEvenWhenTheOtherSucceeded() {
        // REQ-627: a trader reachable on only one channel is a fact support needs. Suppressing it
        // because the other worked hides someone drifting toward unreachable.
        assertThat(reconciler.recordChannelFailure(DeliveryStatus.BOUNCED)).isTrue();
        assertThat(reconciler.intimationMade(DeliveryStatus.BOUNCED, DeliveryStatus.DELIVERED)).isTrue();
    }

    @Test
    @DisplayName("both channels failing while the state stands pages a human")
    void bothFailingWhileTheStateStandsPagesAHuman() {
        // The one case retrying cannot resolve: the account is in an action state, the deadline is
        // live, and no channel carried the message.
        assertThat(reconciler.pageAHuman(DeliveryStatus.FAILED, DeliveryStatus.BOUNCED, true)).isTrue();

        // Resolved in the meantime — no message is owed, so no page.
        assertThat(reconciler.pageAHuman(DeliveryStatus.FAILED, DeliveryStatus.BOUNCED, false)).isFalse();

        // One got through.
        assertThat(reconciler.pageAHuman(DeliveryStatus.FAILED, DeliveryStatus.DELIVERED, true)).isFalse();
    }

    @Test
    @DisplayName("a channel with no outcome yet is not treated as delivered")
    void missingOutcomeIsNotSuccess() {
        // Absence of evidence is not evidence of delivery. Reading null as success would declare
        // an intimation made on the strength of a submission nobody has heard back about.
        assertThat(reconciler.intimationMade(null, null)).isFalse();
        assertThat(reconciler.provesReceipt(null, MessageChannel.EMAIL)).isFalse();
        assertThat(reconciler.pageAHuman(null, null, true)).isTrue();
    }
}
