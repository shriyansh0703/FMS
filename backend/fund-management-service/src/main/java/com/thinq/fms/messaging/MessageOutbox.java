package com.thinq.fms.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Where intents are written, so that queueing a message and changing the state that justifies it
 * are one transaction (REQ-622).
 *
 * <p><b>The point is the atomicity, not the storage.</b> A message queued outside the transaction
 * that produced its state can be sent for a state change that then rolls back — a trader told their
 * withdrawal settled when it did not. Writing the intent alongside the state change means both
 * happen or neither does, and the relay's re-check at dispatch handles the remaining case where the
 * state moved on legitimately between queueing and sending.
 *
 * <p>Duplicate intents are <b>not</b> an error. {@code fms_intent_once} makes one occurrence produce
 * one message per channel, and an event re-processed after a crash must be able to write the same
 * intents again and have the second write do nothing rather than fail the whole transaction.
 */
public interface MessageOutbox {

    /**
     * Queue these intents, ignoring any that already exist for their occurrence.
     *
     * @return the number actually written, which is fewer than {@code intents.size()} when the
     *     event has been processed before. Callers that care can log the difference; most should
     *     not, because a repeat is the expected outcome of a retry rather than a problem
     */
    int write(List<MessageIntent> intents);

    /**
     * Intents that are due and neither dispatched nor dropped, oldest first.
     *
     * <p>Ordered by schedule so a ladder's steps go out in the order they were designed to escalate,
     * and bounded so one account's backlog cannot starve every other account's.
     */
    List<MessageIntent> due(Instant now, int limit);
}
