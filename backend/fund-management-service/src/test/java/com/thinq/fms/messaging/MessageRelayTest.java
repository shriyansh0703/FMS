package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.DeliveryStatus;
import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.integration.communication.NotificationReceipt;
import com.thinq.fms.integration.communication.NotificationSubmitter;
import com.thinq.fms.platform.money.AccountRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-622: a warning must stop arriving once the thing it warned about is over.
 *
 * <p>The requirement forbids sending and then retracting, so the only place a resolved state can
 * be caught is before submission. That makes the <i>order</i> of operations the requirement, and
 * an implementation that submitted first and reconciled afterwards would satisfy every assertion
 * about content while failing the rule outright.
 */
class MessageRelayTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");

    @Test
    @DisplayName("a resolved state drops the message and never submits it")
    void resolvedStateDropsBeforeSubmitting() {
        RecordingClient client = new RecordingClient();
        RecordingJournal journal = new RecordingJournal();

        Optional<NotificationReceipt> r = relay(intent -> false, client, journal)
                .dispatch(shortfallStep(), Map.of(), NOW);

        assertThat(r).isEmpty();
        // The load-bearing assertion: nothing reached the Communication Service. Once it accepts
        // a notification the message is gone, and REQ-622 forbids retracting it.
        assertThat(client.submissions).isEmpty();
        assertThat(journal.drops).containsExactly(DropReason.STATE_RESOLVED);
    }

    @Test
    @DisplayName("a state that still holds is submitted under the intent id as request_id")
    void holdingStateIsSubmitted() {
        RecordingClient client = new RecordingClient();
        RecordingJournal journal = new RecordingJournal();

        Optional<NotificationReceipt> r = relay(intent -> true, client, journal)
                .dispatch(shortfallStep(), Map.of("amountPaise", "500000"), NOW);

        assertThat(r).isPresent();
        assertThat(client.submissions).hasSize(1);
        // One request_id per intent, never per attempt: a crash after submission replays onto the
        // same key and the service returns the original rather than sending twice.
        assertThat(client.submissions.get(0)).isEqualTo("4242");
        assertThat(journal.submissions).hasSize(1);
        assertThat(journal.drops).isEmpty();
    }

    @Test
    @DisplayName("a missing address drops the message rather than failing the run")
    void missingAddressIsADropNotAFailure() {
        RecordingClient client = new RecordingClient();
        RecordingJournal journal = new RecordingJournal();

        MessageRelay relay = new MessageRelay(intent -> true, client,
                (a, c) -> Optional.empty(), journal);

        assertThat(relay.dispatch(shortfallStep(), Map.of(), NOW)).isEmpty();
        assertThat(client.submissions).isEmpty();
        assertThat(journal.drops).containsExactly(DropReason.NO_ADDRESS);
    }

    @Test
    @DisplayName("every drop is recorded with its reason, never silently")
    void everyDropIsRecorded() {
        // REQ-623 requires suppressed messages logged as well as sent ones. A silent drop looks
        // exactly like a message the system forgot, and support cannot tell them apart.
        RecordingJournal journal = new RecordingJournal();
        relay(intent -> false, new RecordingClient(), journal).dispatch(shortfallStep(), Map.of(), NOW);

        assertThat(journal.drops).isNotEmpty();
        assertThat(journal.droppedIntentIds).containsExactly(4242L);
    }

    @Test
    @DisplayName("an intent that is not yet due is refused rather than sent early")
    void notYetDueIsRefused() {
        assertThatThrownBy(() -> relay(intent -> true, new RecordingClient(), new RecordingJournal())
                .dispatch(shortfallStep(), Map.of(), NOW.minusSeconds(3600)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not due");
    }

    @Test
    @DisplayName("an intent cannot be built without an occurrence reference")
    void assertedRefIsRequired() {
        // Mirrors V25.1's NOT NULL. fms_intent_once does not constrain a NULL, so a null here
        // would let one shortfall produce unlimited duplicate intents — the defect the Stage 9
        // review found and this check prevents recurring at the build site.
        assertThatThrownBy(() -> new MessageIntent(1L, ACCOUNT, "k", MessageChannel.SMS,
                "SHORTFALL_OPEN", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> new MessageIntent(1L, ACCOUNT, "k", MessageChannel.SMS,
                "SHORTFALL_OPEN", "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- harness ----

    private static MessageIntent shortfallStep() {
        return new MessageIntent(4242L, ACCOUNT, "shortfall_step2_sms", MessageChannel.SMS,
                "SHORTFALL_OPEN", "SHORTFALL-2026-08-21", NOW);
    }

    private MessageRelay relay(StateAssertionChecker checker, RecordingClient client, RecordingJournal journal) {
        return new MessageRelay(checker, client, (a, c) -> Optional.of("+919451740121"), journal);
    }

    /**
     * A submitter that records rather than calls.
     *
     * <p>Two lines, because {@code NotificationSubmitter} is the one capability the relay needs.
     * Before that seam existed this had to subclass the HTTP client, and the client is final for
     * good reason — so the seam is what makes the ordering assertion above testable at all.
     */
    private static final class RecordingClient implements NotificationSubmitter {
        final List<String> submissions = new ArrayList<>();

        @Override
        public NotificationReceipt submit(com.thinq.fms.integration.communication.NotificationSubmission s) {
            this.submissions.add(s.requestId());
            return new NotificationReceipt("ntf-1", "tmpl-v3", "sub-1", s.channel(),
                    DeliveryStatus.ACCEPTED, false);
        }
    }

    private static final class RecordingJournal implements MessageRelay.MessageIntentJournal {
        final List<DropReason> drops = new ArrayList<>();
        final List<Long> droppedIntentIds = new ArrayList<>();
        final List<Long> submissions = new ArrayList<>();

        @Override
        public void recordSubmission(MessageIntent intent, NotificationReceipt receipt) {
            this.submissions.add(intent.id());
        }

        @Override
        public void recordDrop(MessageIntent intent, DropReason reason) {
            this.drops.add(reason);
            this.droppedIntentIds.add(intent.id());
        }
    }
}
