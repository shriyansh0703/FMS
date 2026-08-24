package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.AccountRef;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * {@link MessageOutbox} over {@code fms_message_intent} (V25.1).
 *
 * <p>Not a scanned component, for the reason recorded on the repositories: the API test contexts run
 * without a {@code DataSource} by design.
 */
public class JdbcMessageOutbox implements MessageOutbox {

    private final JdbcClient db;

    public JdbcMessageOutbox(JdbcClient db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code ON CONFLICT DO NOTHING} against {@code fms_intent_once} is what makes a re-processed
     * event idempotent. The alternative — checking for existence first — is a race that would let two
     * concurrent processings of the same event both find nothing and both queue, which is the
     * duplicate message the index exists to prevent.
     */
    @Override
    public int write(List<MessageIntent> intents) {
        Objects.requireNonNull(intents, "intents");

        int written = 0;
        for (MessageIntent intent : intents) {
            written += this.db.sql("""
                            INSERT INTO fms_message_intent
                                (account_id, template_key, channel, asserted_state, asserted_ref,
                                 scheduled_for)
                            VALUES (?, ?, ?, ?, ?, ?)
                            ON CONFLICT (account_id, template_key, channel, asserted_ref) DO NOTHING""")
                    .params(intent.account().ucc(),
                            intent.templateKey(),
                            intent.channel().name(),
                            intent.assertedState(),
                            intent.assertedRef(),
                            Timestamp.from(intent.scheduledFor()))
                    .update();
        }
        return written;
    }

    @Override
    public List<MessageIntent> due(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit <= 0) {
            throw new IllegalArgumentException("a dispatch batch is at least one intent; got " + limit);
        }

        return this.db.sql("""
                        SELECT id, account_id, template_key, channel, asserted_state, asserted_ref,
                               scheduled_for
                          FROM fms_message_intent
                         WHERE dispatched_at IS NULL AND dropped_reason IS NULL
                           AND scheduled_for <= ?
                         ORDER BY scheduled_for ASC, id ASC
                         LIMIT ?""")
                .params(Timestamp.from(now), limit)
                .query(this::map)
                .list();
    }

    private MessageIntent map(ResultSet rs, int rowNum) throws SQLException {
        return new MessageIntent(
                rs.getLong("id"),
                AccountRef.of(rs.getString("account_id")),
                rs.getString("template_key"),
                // fromWire rather than valueOf: it is the established parser for this vocabulary
                // and tolerates the case the vendor uses, so a row written by anything other than
                // this class still reads back rather than failing the whole dispatch batch.
                MessageChannel.fromWire(rs.getString("channel")),
                rs.getString("asserted_state"),
                rs.getString("asserted_ref"),
                rs.getTimestamp("scheduled_for").toInstant());
    }
}
