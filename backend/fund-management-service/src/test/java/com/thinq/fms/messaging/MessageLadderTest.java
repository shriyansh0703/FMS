package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The escalation rules, which is where the communications requirements actually live.
 *
 * <p>A ladder that sends two steps instead of three, or an SMS at ₹400 that should have waited
 * until day 14, is a compliance failure — and neither is visible anywhere downstream, because the
 * plumbing that sends them is correct either way. The class performs no I/O precisely so these can
 * be exhaustive.
 */
class MessageLadderTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final LocalDate DAY_ZERO = LocalDate.of(2026, 8, 21);

    private final MessageLadder ladder = new MessageLadder(IST);

    // ---- REQ-601, REQ-604: the shortfall ladder ----

    @Test
    @DisplayName("a shortfall escalates in exactly three steps")
    void aShortfallEscalatesInThreeSteps() {
        List<MessageIntent> intents = ladder.forMarginShortfall(
                ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, false);

        assertThat(intents).extracting(MessageIntent::templateKey).containsOnly(
                "MARGIN_SHORTFALL_STEP_1", "MARGIN_SHORTFALL_STEP_2", "MARGIN_SHORTFALL_STEP_3");
        // Distinct per step, not per intent — the two channels of one step share a schedule
        // deliberately, because Rule C1's minimum is both of them at that moment.
        assertThat(intents).extracting(MessageIntent::scheduledFor).containsOnly(
                NOW, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofHours(2)));
        assertThat(intents.stream().map(MessageIntent::scheduledFor).distinct().toList()).hasSize(3);
    }

    @Test
    @DisplayName("every step carries SMS and email, whatever the user prefers")
    void everyStepCarriesSmsAndEmail() {
        // Rule C13 makes the intimation regulatory and Rule C1 forbids relying on one channel.
        // No preference reaches this method at all, which is the point.
        List<MessageIntent> intents = ladder.forMarginShortfall(
                ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, false);

        for (int step = 1; step <= 3; step++) {
            String key = "MARGIN_SHORTFALL_STEP_" + step;
            assertThat(intents).filteredOn(i -> i.templateKey().equals(key))
                    .extracting(MessageIntent::channel)
                    .as("step %d", step)
                    .containsExactlyInAnyOrder(MessageChannel.SMS, MessageChannel.EMAIL);
        }
    }

    @Test
    @DisplayName("the ladder sends exactly three SMS, which is Rule C12's daily cap")
    void theLadderSendsExactlyThreeSms() {
        List<MessageIntent> intents = ladder.forMarginShortfall(
                ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, true);

        assertThat(intents).filteredOn(i -> i.channel() == MessageChannel.SMS)
                .hasSize(MessageLadder.MAX_SHORTFALL_SMS_PER_DAY);
    }

    @Test
    @DisplayName("no WhatsApp opt-in drops that step without delaying the others")
    void noOptInDropsTheStepWithoutDelayingTheOthers() {
        // REQ-604 is explicit that a missing opt-in must never suppress or postpone a regulatory
        // message. Comparing the schedules directly is what proves "never delays".
        List<MessageIntent> without = ladder.forMarginShortfall(
                ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, false);
        List<MessageIntent> with = ladder.forMarginShortfall(
                ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, true);

        assertThat(without).noneMatch(i -> i.channel() == MessageChannel.WHATSAPP);
        assertThat(with).filteredOn(i -> i.channel() == MessageChannel.WHATSAPP).hasSize(3);

        assertThat(without).extracting(MessageIntent::scheduledFor)
                .as("the SMS and email schedule is identical either way")
                .containsExactlyElementsOf(with.stream()
                        .filter(i -> i.channel() != MessageChannel.WHATSAPP)
                        .map(MessageIntent::scheduledFor).toList());
    }

    @ParameterizedTest(name = "a shortfall of {0} paise sends nothing")
    @ValueSource(longs = {1L, 50L, 99L})
    @DisplayName("a shortfall below one rupee produces no message at all")
    void aTrivialShortfallProducesNoMessage(long paise) {
        assertThat(ladder.forMarginShortfall(ACCOUNT, Money.ofPaise(paise), "SHORTFALL-1", NOW, true))
                .isEmpty();
    }

    @Test
    @DisplayName("exactly one rupee is at the floor and does send")
    void oneRupeeIsAtTheFloorAndSends() {
        // The boundary the floor is defined at. Off by one here either spams about noise or
        // silently drops a real intimation.
        assertThat(ladder.forMarginShortfall(ACCOUNT, Money.ofPaise(100L), "SHORTFALL-1", NOW, false))
                .isNotEmpty();
    }

    // ---- REQ-608: the dues sequence ----

    @Test
    @DisplayName("dues are chased on day 0, 7, 14, 30 and monthly, never daily")
    void duesFollowTheBandedSchedule() {
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L),
                "DUES-1", DAY_ZERO, DAY_ZERO.plusDays(95), false);

        assertThat(distinctDays(intents)).containsExactly(0, 7, 14, 30, 60, 90);
        // One template key per day, shared by that day's channels — never two keys for one day.
        assertThat(intents.stream().map(MessageIntent::templateKey).distinct().toList()).hasSize(6);
    }

    @Test
    @DisplayName("a debt above ₹500 is chased by SMS from day 0")
    void aLargeDebtIsChasedBySmsFromDayZero() {
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(50_001L),
                "DUES-1", DAY_ZERO, DAY_ZERO.plusDays(20), false);

        assertThat(intents).filteredOn(i -> i.channel() == MessageChannel.SMS
                        && i.templateKey().equals("DUES_OUTSTANDING_DAY_0"))
                .as("₹500.01 is above the threshold").hasSize(1);
    }

    @Test
    @DisplayName("a debt at or below ₹500 waits until day 14 for SMS")
    void aSmallDebtWaitsUntilDayFourteenForSms() {
        // ₹500 exactly is "at or below", so it waits. A trivial debt arriving by SMS on day 0
        // reads like an emergency, which is the banding's whole purpose.
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(50_000L),
                "DUES-1", DAY_ZERO, DAY_ZERO.plusDays(20), false);

        assertThat(smsDays(intents)).containsExactly(14);
        assertThat(intents).filteredOn(i -> i.channel() == MessageChannel.EMAIL)
                .as("email still goes from day 0").isNotEmpty();
    }

    @ParameterizedTest(name = "owed {0} paise, opted in {1}: WhatsApp present = {2}")
    @CsvSource({"50001, true, true", "50001, false, false", "50000, true, false", "1, true, false"})
    @DisplayName("WhatsApp on dues needs both a large debt and an opt-in")
    void whatsappOnDuesNeedsBoth(long owed, boolean optedIn, boolean expected) {
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(owed),
                "DUES-1", DAY_ZERO, DAY_ZERO.plusDays(40), optedIn);

        assertThat(intents.stream().anyMatch(i -> i.channel() == MessageChannel.WHATSAPP))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("email is sent on every dues day regardless of amount")
    void emailIsSentOnEveryDuesDay() {
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(1L),
                "DUES-1", DAY_ZERO, DAY_ZERO.plusDays(40), false);

        assertThat(emailDays(intents)).containsExactly(0, 7, 14, 30);
    }

    @Test
    @DisplayName("nothing is owed, nothing is queued")
    void nothingOwedQueuesNothing() {
        assertThat(ladder.forDuesOutstanding(ACCOUNT, Money.ZERO, "DUES-1", DAY_ZERO,
                DAY_ZERO.plusDays(40), true)).isEmpty();
    }

    @Test
    @DisplayName("dues days are measured in the account's own zone, not the server's")
    void duesDaysAreMeasuredInTheAccountsZone() {
        // Day 0 at midnight IST is 18:30 the previous day in UTC. A sequence computed in the wrong
        // zone drifts by a day, and the day-14 SMS band moves with it.
        List<MessageIntent> intents = ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L),
                "DUES-1", DAY_ZERO, DAY_ZERO, false);

        assertThat(intents.get(0).scheduledFor())
                .isEqualTo(DAY_ZERO.atStartOfDay(IST).toInstant());
    }

    // ---- REQ-609: clearance ----

    @Test
    @DisplayName("a cleared shortfall is confirmed on the channels the ladder used")
    void aClearedShortfallIsConfirmedOnTheSameChannels() {
        assertThat(ladder.forShortfallCleared(ACCOUNT, "SHORTFALL-1-CLEARED", NOW, false))
                .extracting(MessageIntent::channel)
                .containsExactlyInAnyOrder(MessageChannel.SMS, MessageChannel.EMAIL);

        assertThat(ladder.forShortfallCleared(ACCOUNT, "SHORTFALL-1-CLEARED", NOW, true))
                .extracting(MessageIntent::channel)
                .contains(MessageChannel.WHATSAPP);
    }

    @Test
    @DisplayName("clearance is keyed on the clearance, so it cannot be confirmed twice")
    void clearanceIsKeyedOnTheClearance() {
        // fms_intent_once is (account, template, channel, asserted_ref). Keying on the debt rather
        // than the clearance would let a second clearance of the same debt send again.
        assertThat(ladder.forDuesCleared(ACCOUNT, "DUES-1-CLEARED", NOW, false))
                .extracting(MessageIntent::assertedRef).containsOnly("DUES-1-CLEARED");
    }

    // ---- REQ-611: the payin chase ----

    @Test
    @DisplayName("a pending payin is chased once, at thirty minutes")
    void aPendingPayinIsChasedOnceAtThirtyMinutes() {
        List<MessageIntent> intents = ladder.forPendingPayin(ACCOUNT, "PAYIN-7", NOW, true);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).scheduledFor()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(intents.get(0).assertedState())
                .as("dropped if the payin resolves first").isEqualTo("PAYIN_UNRESOLVED");
    }

    @Test
    @DisplayName("the write-off message is not pre-scheduled alongside the chase")
    void theWriteOffMessageIsNotPreScheduled() {
        // "Not on a timer" is the requirement's own title. A pre-scheduled write-off message fires
        // whether or not the write-off happened, and Rule C12 forbids anything between the two.
        assertThat(ladder.forPendingPayin(ACCOUNT, "PAYIN-7", NOW, true))
                .noneMatch(i -> i.templateKey().equals("PAYIN_WRITTEN_OFF"));

        assertThat(ladder.forPayinWrittenOff(ACCOUNT, "PAYIN-7", NOW.plusSeconds(7200), true))
                .hasSize(1)
                .allMatch(i -> i.templateKey().equals("PAYIN_WRITTEN_OFF"));
    }

    @Test
    @DisplayName("without WhatsApp the payin chase falls back to email, not to silence")
    void thePayinChaseFallsBackToEmail() {
        // Rule C4. Adding-funds pending is a WhatsApp state in the matrix, and the fallback where
        // it is unavailable is email — the one thing it must not be is nothing.
        assertThat(ladder.forPendingPayin(ACCOUNT, "PAYIN-7", NOW, false))
                .extracting(MessageIntent::channel).containsExactly(MessageChannel.EMAIL);
    }

    // ---- occurrence references ----

    @Test
    @DisplayName("an occurrence reference is required, because the unique index cannot constrain a null")
    void anOccurrenceReferenceIsRequired() {
        for (String bad : new String[]{null, "", "   "}) {
            assertThatThrownBy(() ->
                    ladder.forMarginShortfall(ACCOUNT, Money.ofPaise(500_000L), bad, NOW, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("every entry point requires an occurrence reference, not just the shortfall ladder")
    void everyEntryPointRequiresAnOccurrenceReference() {
        // Mutation testing showed the guard was only exercised through forMarginShortfall: removing
        // the call from the other five methods survived. An intent with a null reference is one
        // fms_intent_once cannot constrain, so one event could queue unlimited messages.
        assertThatThrownBy(() -> ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L), null,
                DAY_ZERO, DAY_ZERO.plusDays(30), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ladder.forDuesCleared(ACCOUNT, " ", NOW, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ladder.forShortfallCleared(ACCOUNT, null, NOW, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ladder.forPendingPayin(ACCOUNT, "", NOW, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ladder.forPayinWrittenOff(ACCOUNT, null, NOW, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the clear-down and write-off messages honour the opt-in too")
    void clearDownAndWriteOffHonourTheOptIn() {
        assertThat(ladder.forDuesCleared(ACCOUNT, "DUES-1-CLEARED", NOW, true))
                .extracting(MessageIntent::channel).contains(MessageChannel.WHATSAPP);
        assertThat(ladder.forDuesCleared(ACCOUNT, "DUES-1-CLEARED", NOW, false))
                .extracting(MessageIntent::channel).doesNotContain(MessageChannel.WHATSAPP);

        assertThat(ladder.forPayinWrittenOff(ACCOUNT, "PAYIN-7", NOW, true))
                .extracting(MessageIntent::channel).containsExactly(MessageChannel.WHATSAPP);
        assertThat(ladder.forPayinWrittenOff(ACCOUNT, "PAYIN-7", NOW, false))
                .extracting(MessageIntent::channel).containsExactly(MessageChannel.EMAIL);
    }

    @Test
    @DisplayName("a dues day exactly on the horizon is included; one past it is not")
    void theDuesHorizonIsInclusive() {
        // The boundary decides whether the day-30 reminder is scheduled or silently dropped when
        // the horizon lands on it.
        assertThat(distinctDays(ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L), "DUES-1",
                DAY_ZERO, DAY_ZERO.plusDays(30), false))).containsExactly(0, 7, 14, 30);
        assertThat(distinctDays(ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L), "DUES-1",
                DAY_ZERO, DAY_ZERO.plusDays(29), false))).containsExactly(0, 7, 14);
        assertThat(distinctDays(ladder.forDuesOutstanding(ACCOUNT, Money.ofPaise(100_000L), "DUES-1",
                DAY_ZERO, DAY_ZERO.plusDays(60), false))).containsExactly(0, 7, 14, 30, 60);
    }

    @Test
    @DisplayName("every intent in one ladder shares the occurrence, so the event is one thing")
    void everyIntentSharesTheOccurrence() {
        assertThat(ladder.forMarginShortfall(ACCOUNT, Money.ofPaise(500_000L), "SHORTFALL-1", NOW, true))
                .extracting(MessageIntent::assertedRef).containsOnly("SHORTFALL-1");
    }

    private List<Integer> distinctDays(List<MessageIntent> intents) {
        return intents.stream()
                .map(i -> Integer.parseInt(i.templateKey().substring("DUES_OUTSTANDING_DAY_".length())))
                .distinct().sorted().toList();
    }

    private List<Integer> smsDays(List<MessageIntent> intents) {
        return distinctDays(intents.stream().filter(i -> i.channel() == MessageChannel.SMS).toList());
    }

    private List<Integer> emailDays(List<MessageIntent> intents) {
        return distinctDays(intents.stream().filter(i -> i.channel() == MessageChannel.EMAIL).toList());
    }
}
