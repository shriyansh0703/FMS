package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.AccountRef;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A message this system intends to send, written in the same transaction as the state change that
 * caused it.
 *
 * <p>That atomicity is the point: the intent and the state commit together or neither does, so
 * there is no window where a shortfall exists and the message announcing it was lost, nor one
 * where a message announces a shortfall that was rolled back.
 *
 * @param id            {@code fms_message_intent.id}, and also the Communication Service's
 *                      {@code request_id}. One per intent, never per attempt — reusing it returns
 *                      the first result and sends nothing
 * @param account       whose state this concerns
 * @param templateKey   which message. A template is authored per channel, so the SMS and email
 *                      wordings are different keys rather than one key on two channels
 * @param channel       the single channel this intent is for. Rule C1's two channels are two
 *                      intents with two ids that fail independently
 * @param assertedState the state this message claims is true, re-checked before dispatch
 * @param assertedRef   the occurrence this belongs to. Never null — {@code fms_intent_once} does
 *                      not constrain a NULL, so a null here would let one occurrence produce
 *                      unlimited duplicate intents
 * @param scheduledFor  when it becomes due. {@code now()} for immediate, or the offset for a
 *                      ladder step
 */
public record MessageIntent(
        long id,
        AccountRef account,
        String templateKey,
        MessageChannel channel,
        String assertedState,
        String assertedRef,
        Instant scheduledFor) {

    public MessageIntent {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(assertedState, "assertedState");
        Objects.requireNonNull(scheduledFor, "scheduledFor");

        // Mirrors V25.1's NOT NULL and non-empty check. Enforced here too because this is where
        // the mistake is made — the constraint catches it at the write, this catches it at the
        // build, and the second is where the stack trace names the caller.
        if (assertedRef == null || assertedRef.isBlank()) {
            throw new IllegalArgumentException(
                    "assertedRef identifies the occurrence and cannot be blank; fms_intent_once "
                            + "does not constrain a null, so one occurrence could produce unlimited intents");
        }
    }

    /** Whether this intent is due at the given instant. */
    public boolean isDueAt(Instant now) {
        return !this.scheduledFor.isAfter(now);
    }

    /** The Communication Service's {@code request_id} for this intent. */
    public String requestId() {
        return Long.toString(this.id);
    }

    public Optional<String> assertedRefIfPresent() {
        return Optional.of(this.assertedRef);
    }
}
