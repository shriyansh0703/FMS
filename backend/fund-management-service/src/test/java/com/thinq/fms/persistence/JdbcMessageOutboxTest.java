package com.thinq.fms.persistence;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.messaging.*;
import com.thinq.fms.platform.money.AccountRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The outbox against the real {@code fms_intent_once} index. */
class JdbcMessageOutboxTest extends PostgresTestSupport {

    private static final AtomicLong SEQ = new AtomicLong(40_000);
    private static final Instant NOW = Instant.parse("2026-08-22T09:00:00Z");

    private JdbcMessageOutbox outbox;
    private AccountRef account;

    @BeforeEach
    void setUp() {
        this.outbox = new JdbcMessageOutbox(db);
        this.account = AccountRef.of("UCC" + SEQ.getAndIncrement());
    }

    /**
     * {@code due()} is deliberately global — the relay dispatches every account's due intents — so
     * tests sharing one database must narrow to their own account rather than assume isolation.
     */
    private List<MessageIntent> mine(Instant now, int limit) {
        return this.outbox.due(now, limit).stream()
                .filter(i -> i.account().equals(this.account)).toList();
    }

    private MessageIntent intent(String templateKey, MessageChannel channel, String ref, Instant at) {
        return new MessageIntent(0L, this.account, templateKey, channel, "MARGIN_SHORTFALL", ref, at);
    }

    @Test
    @DisplayName("re-processing an event writes nothing the second time")
    void reProcessingAnEventWritesNothingTwice() {
        // The property that makes an event handler safe to retry after a crash. Without it, a
        // redelivered event either fails the whole transaction or sends the trader a second copy.
        List<MessageIntent> intents = List.of(
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "SHORTFALL-1", NOW),
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.EMAIL, "SHORTFALL-1", NOW));

        assertThat(this.outbox.write(intents)).isEqualTo(2);
        assertThat(this.outbox.write(intents)).as("a retry adds nothing and does not throw").isZero();
        assertThat(mine(NOW, 100)).hasSize(2);
    }

    @Test
    @DisplayName("the same template on a different channel is a different intent")
    void channelDistinguishesIntents() {
        this.outbox.write(List.of(intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "SHORTFALL-1", NOW)));

        assertThat(this.outbox.write(List.of(
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.EMAIL, "SHORTFALL-1", NOW))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a different occurrence of the same event is a different intent")
    void occurrenceDistinguishesIntents() {
        this.outbox.write(List.of(intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "SHORTFALL-1", NOW)));

        assertThat(this.outbox.write(List.of(
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "SHORTFALL-2", NOW))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("only due intents are returned, oldest schedule first")
    void onlyDueIntentsAreReturnedInScheduleOrder() {
        // A ladder's steps must go out in the order they were designed to escalate.
        this.outbox.write(List.of(
                intent("MARGIN_SHORTFALL_STEP_3", MessageChannel.SMS, "S-3", NOW.plusSeconds(7200)),
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "S-1", NOW),
                intent("MARGIN_SHORTFALL_STEP_2", MessageChannel.SMS, "S-2", NOW.plusSeconds(1800))));

        assertThat(mine(NOW.plusSeconds(1800), 100))
                .extracting(MessageIntent::templateKey)
                .containsExactly("MARGIN_SHORTFALL_STEP_1", "MARGIN_SHORTFALL_STEP_2");
    }

    @Test
    @DisplayName("a dispatched or dropped intent is never returned again")
    void aResolvedIntentIsNotReturnedAgain() {
        this.outbox.write(List.of(
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "S-1", NOW),
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.EMAIL, "S-1", NOW)));

        db.sql("UPDATE fms_message_intent SET dispatched_at = ? WHERE account_id = ? AND channel = 'SMS'")
                .params(java.sql.Timestamp.from(NOW), this.account.ucc()).update();
        db.sql("UPDATE fms_message_intent SET dropped_reason = 'STATE_RESOLVED'"
                        + " WHERE account_id = ? AND channel = 'EMAIL'")
                .params(this.account.ucc()).update();

        assertThat(mine(NOW, 100)).isEmpty();
    }

    @Test
    @DisplayName("the batch is bounded, so one account's backlog cannot starve the rest")
    void theBatchIsBounded() {
        this.outbox.write(List.of(
                intent("MARGIN_SHORTFALL_STEP_1", MessageChannel.SMS, "S-1", NOW),
                intent("MARGIN_SHORTFALL_STEP_2", MessageChannel.SMS, "S-2", NOW),
                intent("MARGIN_SHORTFALL_STEP_3", MessageChannel.SMS, "S-3", NOW)));

        assertThat(this.outbox.due(NOW, 2)).as("the LIMIT is applied globally").hasSize(2);
        assertThatThrownBy(() -> this.outbox.due(NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
