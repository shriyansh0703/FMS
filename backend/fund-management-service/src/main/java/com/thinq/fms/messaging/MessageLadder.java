package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which messages an event produces, on which channels, and when — REQ-601, 604, 608, 609 and 611.
 *
 * <p><b>This class decides schedules and channels. It does not send anything, resolve any figure,
 * or read any address.</b> That separation is what lets the escalation rules be tested exhaustively
 * without a vendor, a database or a clock, and the rules are where the requirements actually live:
 * a ladder that sends two steps instead of three, or an SMS at ₹400 that should have waited until
 * day 14, is a compliance failure that no amount of correct plumbing downstream will catch.
 *
 * <p>Every method returns intents to be written to the outbox against the event. Nothing here is
 * scheduled in the sense of a timer — REQ-622 queues against the event, and the relay re-evaluates
 * the asserted state before each dispatch, so an intent whose state has resolved is dropped rather
 * than sent and retracted. That is why a cleared debt needs no cancellation call: the remaining
 * intents simply stop passing their assertion.
 */
public final class MessageLadder {

    /** Rule C12's cap, and the length of the shortfall ladder. The two are deliberately equal. */
    public static final int MAX_SHORTFALL_SMS_PER_DAY = 3;

    /**
     * Below this a shortfall produces no message at all (REQ-601, §10).
     *
     * <p>A one-rupee shortfall is arithmetic noise, and a message about it costs the user's
     * attention on the next one that matters.
     */
    public static final Money SHORTFALL_MESSAGE_FLOOR = Money.ofPaise(100L);

    /** Above this, dues are chased on SMS from day 0 rather than from day 14 (REQ-608). */
    public static final Money DUES_SMS_IMMEDIATE_THRESHOLD = Money.ofPaise(50_000L);

    /** The three shortfall steps, as offsets from the moment the shortfall was identified. */
    static final List<Duration> SHORTFALL_STEPS =
            List.of(Duration.ZERO, Duration.ofMinutes(30), Duration.ofHours(2));

    /** REQ-608: day 0, 7, 14, 30, then monthly. Never daily. */
    static final List<Integer> DUES_DAYS = List.of(0, 7, 14, 30);
    static final int DUES_MONTHLY_INTERVAL_DAYS = 30;

    /** REQ-611: one chase at 30 minutes, one at write-off. Nothing between them. */
    public static final Duration PAYIN_CHASE_AFTER = Duration.ofMinutes(30);

    private final ZoneId zone;

    public MessageLadder(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * The margin-shortfall ladder (REQ-601, REQ-604).
     *
     * <p>Three steps. SMS and email on every step regardless of any preference, because Rule C13
     * makes the intimation regulatory and Rule C1 forbids relying on one channel for an action
     * state. WhatsApp is added only where the user opted in, and its absence <b>drops that step
     * without delaying the others</b> — the requirement is explicit that a missing opt-in must never
     * suppress or postpone a regulatory message.
     *
     * @param whatsappOptedIn REQ-624's explicit opt-in. False is not an error and produces no
     *     record of failure; the step simply does not exist
     * @return an empty list when the shortfall is below the floor
     */
    public List<MessageIntent> forMarginShortfall(AccountRef account,
                                                  Money shortfall,
                                                  String occurrenceRef,
                                                  Instant identifiedAt,
                                                  boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(shortfall, "shortfall");
        Objects.requireNonNull(identifiedAt, "identifiedAt");
        requireRef(occurrenceRef);

        if (shortfall.compareTo(SHORTFALL_MESSAGE_FLOOR) < 0) {
            return List.of();
        }

        List<MessageIntent> intents = new ArrayList<>();
        for (int step = 0; step < SHORTFALL_STEPS.size(); step++) {
            Instant at = identifiedAt.plus(SHORTFALL_STEPS.get(step));
            String key = "MARGIN_SHORTFALL_STEP_" + (step + 1);

            // Rule C1's minimum, on every step. Not conditional on anything.
            intents.add(intent(account, key, MessageChannel.SMS, "MARGIN_SHORTFALL", occurrenceRef, at));
            intents.add(intent(account, key, MessageChannel.EMAIL, "MARGIN_SHORTFALL", occurrenceRef, at));

            if (whatsappOptedIn) {
                intents.add(intent(account, key, MessageChannel.WHATSAPP, "MARGIN_SHORTFALL",
                        occurrenceRef, at));
            }
        }
        return List.copyOf(intents);
    }

    /**
     * The dues sequence (REQ-608).
     *
     * <p>Day 0, 7, 14, 30 and monthly thereafter, banded by amount rather than by day count alone:
     * email from day 0 always, SMS from day 0 above ₹500 and from day 14 at or below it, WhatsApp
     * only above ₹500 and only with an opt-in. The banding is the requirement — a ₹40 debt chased
     * by SMS on day 0 reads like an emergency, and a ₹40,000 one that waits two weeks reads like
     * nothing at all.
     *
     * <p>No cancellation path is needed when the debt clears. REQ-622 queues against the event, so
     * the remaining intents fail their state assertion at dispatch and are dropped with
     * {@link DropReason#STATE_RESOLVED} rather than being sent and retracted.
     *
     * @param through how far ahead to schedule; the sequence is unbounded in principle, so the
     *     caller states the horizon rather than this method inventing one
     */
    public List<MessageIntent> forDuesOutstanding(AccountRef account,
                                                  Money owed,
                                                  String occurrenceRef,
                                                  LocalDate firstOwedOn,
                                                  LocalDate through,
                                                  boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(owed, "owed");
        Objects.requireNonNull(firstOwedOn, "firstOwedOn");
        Objects.requireNonNull(through, "through");
        requireRef(occurrenceRef);

        if (!owed.isPositive()) {
            return List.of();
        }
        boolean aboveThreshold = owed.compareTo(DUES_SMS_IMMEDIATE_THRESHOLD) > 0;

        List<MessageIntent> intents = new ArrayList<>();
        for (int day : duesDays(firstOwedOn, through)) {
            LocalDate on = firstOwedOn.plusDays(day);
            Instant at = on.atStartOfDay(this.zone).toInstant();
            String key = "DUES_OUTSTANDING_DAY_" + day;

            intents.add(intent(account, key, MessageChannel.EMAIL, "DUES_OUTSTANDING", occurrenceRef, at));

            if (aboveThreshold || day >= 14) {
                intents.add(intent(account, key, MessageChannel.SMS, "DUES_OUTSTANDING", occurrenceRef, at));
            }
            if (aboveThreshold && whatsappOptedIn) {
                intents.add(intent(account, key, MessageChannel.WHATSAPP, "DUES_OUTSTANDING",
                        occurrenceRef, at));
            }
        }
        return List.copyOf(intents);
    }

    /**
     * The clear-down confirmation (REQ-609), sent once and on the channels the sequence used.
     *
     * <p>The occurrence reference is the clearance, not the debt, so {@code fms_intent_once} makes
     * "not more than once for one clearance" a constraint rather than a convention.
     */
    public List<MessageIntent> forDuesCleared(AccountRef account,
                                              String clearanceRef,
                                              Instant clearedAt,
                                              boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(clearedAt, "clearedAt");
        requireRef(clearanceRef);

        List<MessageIntent> intents = new ArrayList<>();
        intents.add(intent(account, "DUES_CLEARED", MessageChannel.EMAIL, "DUES_CLEARED",
                clearanceRef, clearedAt));
        intents.add(intent(account, "DUES_CLEARED", MessageChannel.SMS, "DUES_CLEARED",
                clearanceRef, clearedAt));
        if (whatsappOptedIn) {
            intents.add(intent(account, "DUES_CLEARED", MessageChannel.WHATSAPP, "DUES_CLEARED",
                    clearanceRef, clearedAt));
        }
        return List.copyOf(intents);
    }

    /**
     * The shortfall-cleared confirmation (REQ-609), on the same channels the ladder used.
     *
     * <p>"The same channels" is why this takes the opt-in rather than assuming: a ladder that ran
     * without WhatsApp must not be closed off on it.
     */
    public List<MessageIntent> forShortfallCleared(AccountRef account,
                                                   String clearanceRef,
                                                   Instant clearedAt,
                                                   boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(clearedAt, "clearedAt");
        requireRef(clearanceRef);

        List<MessageIntent> intents = new ArrayList<>();
        intents.add(intent(account, "MARGIN_SHORTFALL_CLEARED", MessageChannel.SMS,
                "MARGIN_SHORTFALL_CLEARED", clearanceRef, clearedAt));
        intents.add(intent(account, "MARGIN_SHORTFALL_CLEARED", MessageChannel.EMAIL,
                "MARGIN_SHORTFALL_CLEARED", clearanceRef, clearedAt));
        if (whatsappOptedIn) {
            intents.add(intent(account, "MARGIN_SHORTFALL_CLEARED", MessageChannel.WHATSAPP,
                    "MARGIN_SHORTFALL_CLEARED", clearanceRef, clearedAt));
        }
        return List.copyOf(intents);
    }

    /**
     * The pending-payin chase (REQ-611): exactly one message at 30 minutes, and nothing else.
     *
     * <p>The write-off message is <b>not</b> scheduled here. It is queued when the write-off
     * actually happens, because Rule C12 forbids anything between the two and a pre-scheduled
     * write-off message would fire on a timer whether or not the write-off occurred — which is
     * precisely the "not on a timer" the requirement's title objects to.
     *
     * <p>Both this intent and the write-off message assert the payin is still unresolved, so a
     * payin that confirms in the meantime drops them rather than telling the trader their
     * completed deposit is still pending.
     */
    public List<MessageIntent> forPendingPayin(AccountRef account,
                                               String attemptRef,
                                               Instant startedAt,
                                               boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(startedAt, "startedAt");
        requireRef(attemptRef);

        Instant at = startedAt.plus(PAYIN_CHASE_AFTER);

        // Rule C2's matrix puts adding-funds pending on WhatsApp, with email as the fallback where
        // WhatsApp is unavailable — Rule C4: the fallback is email, not silence.
        MessageChannel channel = whatsappOptedIn ? MessageChannel.WHATSAPP : MessageChannel.EMAIL;
        return List.of(intent(account, "PAYIN_PENDING_CHASE", channel, "PAYIN_UNRESOLVED",
                attemptRef, at));
    }

    /**
     * The message for a resolved payin — confirmation or the specific failure (REQ-612 to REQ-615).
     *
     * <p>Queued as an intent carrying no figures. REQ-621 resolves parameters at dispatch from the
     * same {@code derive()} call the screen uses, so capturing them here would produce a message
     * stating figures that were current when the gateway answered rather than when it was sent.
     */
    public List<MessageIntent> forPayinOutcome(AccountRef account,
                                               com.thinq.fms.integration.juspay.PayinOutcome outcome,
                                               String attemptRef,
                                               Instant resolvedAt,
                                               boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        requireRef(attemptRef);

        if (outcome.isAwaitingResolution()) {
            // Rule A9b: nothing is announced while the outcome is unknown. The 30-minute chase from
            // forPendingPayin covers that window, and announcing an unresolved payment as an
            // outcome is exactly what that rule forbids.
            return List.of();
        }

        if (outcome == com.thinq.fms.integration.juspay.PayinOutcome.CONFIRMED) {
            // Email only: it is the one channel that can carry the effect on two figures (REQ-613).
            return List.of(intent(account, "PAYIN_CONFIRMED", MessageChannel.EMAIL,
                    "PAYIN_CONFIRMED", attemptRef, resolvedAt));
        }

        MessageChannel channel = whatsappOptedIn ? MessageChannel.WHATSAPP : MessageChannel.EMAIL;
        return List.of(intent(account, "PAYIN_" + outcome.name(), channel,
                "PAYIN_" + outcome.name(), attemptRef, resolvedAt));
    }

    /** The single further message at write-off (REQ-611), queued when it happens. */
    public List<MessageIntent> forPayinWrittenOff(AccountRef account,
                                                  String attemptRef,
                                                  Instant writtenOffAt,
                                                  boolean whatsappOptedIn) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(writtenOffAt, "writtenOffAt");
        requireRef(attemptRef);

        MessageChannel channel = whatsappOptedIn ? MessageChannel.WHATSAPP : MessageChannel.EMAIL;
        return List.of(intent(account, "PAYIN_WRITTEN_OFF", channel, "PAYIN_WRITTEN_OFF",
                attemptRef, writtenOffAt));
    }

    /** Day offsets in the dues sequence up to the horizon: 0, 7, 14, 30, then every 30 days. */
    static List<Integer> duesDays(LocalDate firstOwedOn, LocalDate through) {
        int horizon = (int) java.time.temporal.ChronoUnit.DAYS.between(firstOwedOn, through);
        List<Integer> days = new ArrayList<>();
        for (int day : DUES_DAYS) {
            if (day <= horizon) {
                days.add(day);
            }
        }
        int next = DUES_DAYS.get(DUES_DAYS.size() - 1) + DUES_MONTHLY_INTERVAL_DAYS;
        while (next <= horizon) {
            days.add(next);
            next += DUES_MONTHLY_INTERVAL_DAYS;
        }
        return List.copyOf(days);
    }

    private static MessageIntent intent(AccountRef account, String templateKey,
                                        MessageChannel channel, String assertedState,
                                        String assertedRef, Instant scheduledFor) {
        // id 0 until the outbox assigns one; the intent is identified by its occurrence until then.
        return new MessageIntent(0L, account, templateKey, channel, assertedState, assertedRef,
                scheduledFor);
    }

    private static void requireRef(String occurrenceRef) {
        if (occurrenceRef == null || occurrenceRef.isBlank()) {
            throw new IllegalArgumentException(
                    "an occurrence reference identifies which event this ladder belongs to; without "
                            + "one fms_intent_once cannot stop the same event queueing twice");
        }
    }
}
