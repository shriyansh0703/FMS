package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.NotificationSubmitter;
import com.thinq.fms.integration.communication.NotificationReceipt;
import com.thinq.fms.integration.communication.NotificationSubmission;
import com.thinq.fms.platform.error.ChannelNotPermittedException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Takes a due intent and either submits it or drops it (REQ-622).
 *
 * <h2>The order of operations is the requirement</h2>
 *
 * <p>Re-check, then submit. Never submit and reconcile afterwards. REQ-622 states plainly that the
 * system must not send a message and then retract it, so the only place a resolved state can be
 * caught is <i>before</i> the message leaves — and once the Communication Service has accepted it,
 * it is gone.
 *
 * <p>That ordering is why a ladder step cannot be a delayed job: the job would fire and send. This
 * looks again first.
 *
 * <h2>A drop is recorded, not swallowed</h2>
 *
 * <p>Every drop writes its reason. REQ-623 requires suppressed messages logged as well as sent
 * ones, and a silent drop is indistinguishable from a message the system forgot — support cannot
 * tell a working suppression from a bug.
 */
public final class MessageRelay {

    private final StateAssertionChecker assertions;
    private final NotificationSubmitter client;
    private final MessageAddressBook addresses;
    private final MessageIntentJournal journal;

    public MessageRelay(StateAssertionChecker assertions,
                        NotificationSubmitter client,
                        MessageAddressBook addresses,
                        MessageIntentJournal journal) {
        this.assertions = Objects.requireNonNull(assertions, "assertions");
        this.client = Objects.requireNonNull(client, "client");
        this.addresses = Objects.requireNonNull(addresses, "addresses");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * Dispatch one due intent.
     *
     * @return the receipt when it was submitted, or empty when it was dropped. Empty is an
     *     ordinary outcome rather than a failure — a dropped ladder step is REQ-622 working
     */
    public Optional<NotificationReceipt> dispatch(MessageIntent intent, Map<String, String> parameters, Instant now) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(parameters, "parameters");

        if (!intent.isDueAt(now)) {
            throw new IllegalArgumentException(
                    "intent " + intent.id() + " is not due until " + intent.scheduledFor());
        }

        // 1. Re-check FIRST. Everything below this line is irreversible.
        if (!this.assertions.stillHolds(intent)) {
            this.journal.recordDrop(intent, DropReason.STATE_RESOLVED);
            return Optional.empty();
        }

        // 2. An address for this channel, or the message cannot go.
        Optional<String> address = this.addresses.addressFor(intent.account(), intent.channel());
        if (address.isEmpty()) {
            this.journal.recordDrop(intent, DropReason.NO_ADDRESS);
            return Optional.empty();
        }

        // 3. Submit. The intent id is the request_id, so a crash between here and the journal
        //    write replays onto the same key and the service returns the original result rather
        //    than sending a second time.
        try {
            NotificationReceipt receipt = this.client.submit(new NotificationSubmission(
                    intent.requestId(), intent.templateKey(), intent.channel(), address.get(), parameters));
            this.journal.recordSubmission(intent, receipt);
            return Optional.of(receipt);
        } catch (ChannelNotPermittedException e) {
            // A refused channel is a configuration error, not a message that failed to send. It is
            // recorded as a suppression so it shows in the delivery log, and it alerts separately.
            this.journal.recordDrop(intent, DropReason.CHANNEL_NOT_PERMITTED);
            return Optional.empty();
        }
    }

    /** Where a channel's address for an account comes from. */
    public interface MessageAddressBook {
        Optional<String> addressFor(com.thinq.fms.platform.money.AccountRef account,
                                    com.thinq.fms.integration.communication.MessageChannel channel);
    }

    /**
     * Records what happened to an intent.
     *
     * <p>Both methods, not just the submission one. REQ-622 requires a dropped ladder step
     * recorded with its reason, and REQ-623 requires suppressed messages logged alongside sent
     * ones.
     */
    public interface MessageIntentJournal {
        void recordSubmission(MessageIntent intent, NotificationReceipt receipt);

        void recordDrop(MessageIntent intent, DropReason reason);
    }
}
