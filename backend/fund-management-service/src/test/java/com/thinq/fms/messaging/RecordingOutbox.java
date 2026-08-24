package com.thinq.fms.messaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MessageOutbox} that keeps what was written, so tests can assert which messages an event
 * produced rather than only that it did not throw.
 *
 * <p>Deduplicates on the occurrence the way {@code fms_intent_once} does, so a test that processes
 * an event twice sees the same behaviour it would get from the database.
 */
public final class RecordingOutbox implements MessageOutbox {

    private final List<MessageIntent> written = new ArrayList<>();

    @Override
    public int write(List<MessageIntent> intents) {
        int added = 0;
        for (MessageIntent intent : intents) {
            boolean seen = this.written.stream().anyMatch(existing ->
                    existing.account().equals(intent.account())
                            && existing.templateKey().equals(intent.templateKey())
                            && existing.channel() == intent.channel()
                            && existing.assertedRef().equals(intent.assertedRef()));
            if (!seen) {
                this.written.add(intent);
                added++;
            }
        }
        return added;
    }

    @Override
    public List<MessageIntent> due(Instant now, int limit) {
        return this.written.stream().filter(i -> i.isDueAt(now)).limit(limit).toList();
    }

    public List<MessageIntent> written() {
        return List.copyOf(this.written);
    }

    public List<String> templateKeys() {
        return this.written.stream().map(MessageIntent::templateKey).toList();
    }
}
