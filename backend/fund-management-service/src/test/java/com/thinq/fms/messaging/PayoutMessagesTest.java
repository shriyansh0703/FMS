package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementReasonCode;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-616, 617, 618, 619 and 620 — where Rule C8 and the "where is the money" rule live. */
class PayoutMessagesTest {

    private static final Money REQUESTED = Money.ofPaise(1_000_000L);
    private static final String FMS_REF = "FMS-W-4471";

    private List<MessageSpec> settled(PayoutState outcome, Money sent, Optional<String> bankRef,
                                      Optional<SettlementReasonCode> reason, boolean whatsapp) {
        return PayoutMessages.settled(outcome, REQUESTED, sent, "4471", FMS_REF, bankRef, reason,
                whatsapp);
    }

    // ---- REQ-616 ----

    @Test
    @DisplayName("a user cancellation is confirmed by email only")
    void aCancellationIsEmailOnly() {
        MessageSpec spec = PayoutMessages.cancelledByUser(REQUESTED, FMS_REF);

        assertThat(spec.channel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(spec.templateKey()).isEqualTo("WITHDRAWAL_CANCELLED");
    }

    @Test
    @DisplayName("a cancellation states that nothing moved, because nothing was ever held")
    void aCancellationStatesNothingMoved() {
        // Rule W3: a request reserves nothing. "Your funds have been returned" would imply they had
        // been taken, and the trader would go looking for a movement that never happened.
        assertThat(PayoutMessages.cancelledByUser(REQUESTED, FMS_REF).parameters())
                .containsEntry("nothingMoved", "true");
    }

    // ---- REQ-620, Rule C8 ----

    @Test
    @DisplayName("the bank's reference and ours are separate fields")
    void theTwoReferencesAreSeparateFields() {
        var parameters = settled(PayoutState.PAID, REQUESTED, Optional.of("UTR-99887766"),
                Optional.empty(), false).get(0).parameters();

        assertThat(parameters)
                .containsEntry("fmsReference", FMS_REF)
                .containsEntry("bankReference", "UTR-99887766");
    }

    @Test
    @DisplayName("passing our reference as the bank's is refused outright")
    void ourReferenceCannotBeSentAsTheBanks() {
        // Rule C8. The two look alike, and a trader given ours goes to their bank with a value the
        // bank has never seen and cannot trace.
        assertThatThrownBy(() -> settled(PayoutState.PAID, REQUESTED, Optional.of(FMS_REF),
                Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rule C8");
    }

    @Test
    @DisplayName("an unavailable bank reference is said to be pending, not substituted")
    void anUnavailableBankReferenceIsSaidToBePending() {
        var parameters = settled(PayoutState.PAID, REQUESTED, Optional.empty(),
                Optional.empty(), false).get(0).parameters();

        assertThat(parameters)
                .containsEntry("bankReference", "")
                .containsEntry("bankReferencePending", "true");
        assertThat(parameters.get("bankReference"))
                .as("never ours in its place").isNotEqualTo(FMS_REF);
    }

    @Test
    @DisplayName("a movement that was never deducted carries no bank reference at all")
    void nothingDeductedCarriesNoBankReference() {
        var parameters = settled(PayoutState.NOTHING_SENT, Money.ZERO, Optional.empty(),
                Optional.of(SettlementReasonCode.INSUFFICIENT_BALANCE), false).get(0).parameters();

        assertThat(parameters)
                .containsEntry("bankReference", "")
                .containsEntry("bankReferencePending", "false");
    }

    // ---- REQ-618 ----

    @Test
    @DisplayName("a terminal message says where the money is, not only what happened")
    void aTerminalMessageSaysWhereTheMoneyIs() {
        var paid = settled(PayoutState.PAID, REQUESTED, Optional.of("UTR-1"), Optional.empty(),
                false).get(0).parameters();
        assertThat(paid)
                .containsEntry("moneyLeftForBank", "true")
                .containsEntry("destinationMasked", "4471")
                .containsEntry("nothingDeducted", "false");

        var nothing = settled(PayoutState.NOTHING_SENT, Money.ZERO, Optional.empty(),
                Optional.of(SettlementReasonCode.INSUFFICIENT_BALANCE), false).get(0).parameters();
        assertThat(nothing)
                .containsEntry("moneyLeftForBank", "false")
                .containsEntry("nothingDeducted", "true");
    }

    @Test
    @DisplayName("no destination is named where the money never left")
    void noDestinationWhereTheMoneyNeverLeft() {
        // Naming an account the money did not reach reads as though it did.
        assertThat(settled(PayoutState.NOTHING_SENT, Money.ZERO, Optional.empty(),
                Optional.of(SettlementReasonCode.MARGIN_BLOCKED), false).get(0).parameters())
                .containsEntry("destinationMasked", "");
    }

    // ---- REQ-617 ----

    @Test
    @DisplayName("a partial transfer is its own message, with both figures and the gap named")
    void aPartialTransferIsItsOwnMessage() {
        var spec = settled(PayoutState.PARTLY_PAID, Money.ofPaise(600_000L), Optional.of("UTR-2"),
                Optional.of(SettlementReasonCode.INSUFFICIENT_BALANCE), false).get(0);

        assertThat(spec.templateKey()).isEqualTo("WITHDRAWAL_PARTLY_PAID");
        assertThat(spec.parameters())
                .containsEntry("amountRequested", "10000.00")
                .containsEntry("amountSent", "6000.00")
                .containsEntry("shortfall", "4000.00")
                .containsEntry("deductionReason", "INSUFFICIENT_BALANCE")
                .containsEntry("requestClosed", "true");
    }

    @Test
    @DisplayName("a partial transfer without a named deduction is refused")
    void aPartialTransferMustNameTheDeduction() {
        // Rule W10. Two figures and no explanation reads as an error rather than a decision.
        assertThatThrownBy(() -> settled(PayoutState.PARTLY_PAID, Money.ofPaise(600_000L),
                Optional.of("UTR-2"), Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rule W10");
    }

    @Test
    @DisplayName("a non-partial outcome still names its reason where there is one")
    void aNonPartialOutcomeNamesItsReason() {
        // REQ-618: never only a status. "Nothing was sent" without MARGIN_BLOCKED beside it leaves
        // the trader unable to tell whether to fix something or wait.
        assertThat(settled(PayoutState.NOTHING_SENT, Money.ZERO, Optional.empty(),
                Optional.of(SettlementReasonCode.MARGIN_BLOCKED), false).get(0).parameters())
                .containsEntry("reason", "MARGIN_BLOCKED");

        assertThat(settled(PayoutState.PAID, REQUESTED, Optional.of("UTR-1"), Optional.empty(),
                false).get(0).parameters())
                .as("a clean payment has no reason to name").doesNotContainKey("reason");
    }

    // ---- REQ-619 ----

    @ParameterizedTest
    @EnumSource(value = PayoutState.class,
            names = {"PAID", "PARTLY_PAID", "NOTHING_SENT", "RETURNED", "INSTRUCTED"})
    @DisplayName("each end-of-day outcome gets its own template")
    void eachOutcomeGetsItsOwnTemplate(PayoutState outcome) {
        Money sent = outcome == PayoutState.PAID ? REQUESTED
                : outcome == PayoutState.PARTLY_PAID ? Money.ofPaise(600_000L) : Money.ZERO;
        Optional<SettlementReasonCode> reason = outcome == PayoutState.PARTLY_PAID
                ? Optional.of(SettlementReasonCode.INSUFFICIENT_BALANCE) : Optional.empty();

        assertThat(settled(outcome, sent, Optional.empty(), reason, false).get(0).templateKey())
                .isEqualTo("WITHDRAWAL_" + outcome.name());
    }

    @Test
    @DisplayName("an unavailable rail leaves the request open and cancellable")
    void anUnavailableRailLeavesTheRequestOpen() {
        // REQ-619 is explicit that this outcome alone must not close the request — the money never
        // moved and the trader must still be able to withdraw the request.
        assertThat(settled(PayoutState.INSTRUCTED, Money.ZERO, Optional.empty(),
                Optional.empty(), false).get(0).parameters())
                .containsEntry("requestClosed", "false")
                .containsEntry("stillCancellable", "true");
    }

    @Test
    @DisplayName("every other outcome closes the request")
    void everyOtherOutcomeClosesTheRequest() {
        assertThat(settled(PayoutState.PAID, REQUESTED, Optional.empty(), Optional.empty(), false)
                .get(0).parameters())
                .containsEntry("requestClosed", "true")
                .containsEntry("stillCancellable", "false");
    }

    @Test
    @DisplayName("a non-terminal state is not announced at all")
    void aNonTerminalStateIsNotAnnounced() {
        assertThatThrownBy(() -> settled(PayoutState.ACCEPTED, Money.ZERO, Optional.empty(),
                Optional.empty(), false)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- channels ----

    @Test
    @DisplayName("WhatsApp carries sent and partly sent only, and only with an opt-in")
    void whatsappCarriesOnlyTheOutcomesWhereMoneyMoved() {
        // Rule C2's matrix. A "nothing sent" outcome on WhatsApp would be a notification about the
        // absence of a movement, which the matrix deliberately leaves to email.
        assertThat(settled(PayoutState.PAID, REQUESTED, Optional.of("UTR-1"), Optional.empty(), true))
                .extracting(MessageSpec::channel)
                .containsExactly(MessageChannel.EMAIL, MessageChannel.WHATSAPP);

        assertThat(settled(PayoutState.NOTHING_SENT, Money.ZERO, Optional.empty(),
                Optional.of(SettlementReasonCode.MARGIN_BLOCKED), true))
                .extracting(MessageSpec::channel).containsExactly(MessageChannel.EMAIL);

        assertThat(settled(PayoutState.PAID, REQUESTED, Optional.of("UTR-1"), Optional.empty(), false))
                .extracting(MessageSpec::channel).containsExactly(MessageChannel.EMAIL);
    }

    @Test
    @DisplayName("every outcome reaches email, so no outcome is silent")
    void everyOutcomeReachesEmail() {
        for (PayoutState outcome : List.of(PayoutState.PAID, PayoutState.PARTLY_PAID,
                PayoutState.NOTHING_SENT, PayoutState.RETURNED, PayoutState.INSTRUCTED)) {
            Money sent = outcome == PayoutState.PAID ? REQUESTED
                    : outcome == PayoutState.PARTLY_PAID ? Money.ofPaise(600_000L) : Money.ZERO;
            Optional<SettlementReasonCode> reason = outcome == PayoutState.PARTLY_PAID
                    ? Optional.of(SettlementReasonCode.INSUFFICIENT_BALANCE) : Optional.empty();

            assertThat(settled(outcome, sent, Optional.empty(), reason, false))
                    .as("%s", outcome)
                    .extracting(MessageSpec::channel).contains(MessageChannel.EMAIL);
        }
    }
}
