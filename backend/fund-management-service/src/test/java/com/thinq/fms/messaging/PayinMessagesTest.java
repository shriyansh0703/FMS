package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-612, 613, 614 and 615 — including the things these messages must NOT say. */
class PayinMessagesTest {

    private static final LocalDate WITHDRAWABLE_FROM = LocalDate.of(2026, 8, 24);

    private MessageSpec confirmation() {
        return PayinMessages.confirmed(Money.ofPaise(500_000L), "4471", PaymentRoute.UPI,
                Money.ofPaise(1_500_000L), Money.ofPaise(500_000L), Money.ofPaise(200_000L),
                WITHDRAWABLE_FROM);
    }

    @Test
    @DisplayName("a confirmation names the amount, the last four digits and the route")
    void aConfirmationNamesAmountSourceAndRoute() {
        assertThat(confirmation().parameters())
                .containsEntry("amount", "5000.00")
                .containsEntry("sourceMasked", "4471")
                .containsEntry("route", "UPI");
    }

    @Test
    @DisplayName("a confirmation states both figures and why one of them did not move")
    void aConfirmationStatesBothFiguresAndTheReason() {
        // REQ-613 and REQ-615. A payin raises available margin and leaves the withdrawable figure
        // alone, because Rule B4's ADDED_TODAY term subtracts exactly what was added. Stating only
        // "received" invites the trader to try withdrawing it straight back out.
        assertThat(confirmation().parameters())
                .containsEntry("availableMargin", "15000.00")
                .containsEntry("availableMarginChange", "5000.00")
                .containsEntry("withdrawable", "2000.00")
                .containsEntry("withdrawableUnchangedTerm", "ADDED_TODAY")
                .containsEntry("withdrawableFrom", "2026-08-24");
    }

    @Test
    @DisplayName("a confirmation carries no ledger balance and no full account number")
    void aConfirmationDisclosesNothingFurther() {
        // REQ-612's non-disclosure half, asserted as an absence because that is the only way it can
        // be asserted. The two margin figures are permitted and named by REQ-615; the settled ledger
        // balance is the "balance figure" REQ-612 forbids.
        assertThat(confirmation().parameters()).doesNotContainKeys(
                "ledgerBalance", "balance", "settledBalance", "accountNumber", "sourceAccount");
    }

    @Test
    @DisplayName("a source longer than four digits is refused rather than truncated")
    void aLongSourceIsRefused() {
        // Truncating silently would work until someone passed a full number and it appeared in an
        // email. Refusing puts the failure at the call site.
        assertThatThrownBy(() -> PayinMessages.confirmed(Money.ofPaise(500_000L), "50100012345678",
                PaymentRoute.UPI, Money.ZERO, Money.ZERO, Money.ZERO, WITHDRAWABLE_FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last four");
    }

    @Test
    @DisplayName("the confirmation goes on email, the only channel that carries two figures")
    void theConfirmationGoesOnEmail() {
        assertThat(confirmation().channel()).isEqualTo(MessageChannel.EMAIL);
    }

    // ---- REQ-614: failures ----

    @ParameterizedTest
    @EnumSource(value = PayinOutcome.class, names = "CONFIRMED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every failure outcome gets its own template, never a shared one")
    void everyFailureOutcomeGetsItsOwnTemplate(PayinOutcome outcome) {
        // Rule A9a's outcomes are not interchangeable and the recovery differs for each. A shared
        // template invites shared copy, and shared copy is how "no answer from the bank" comes to
        // read as "failed".
        MessageSpec spec = PayinMessages.failed(outcome, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false);

        assertThat(spec.templateKey()).isEqualTo("PAYIN_" + outcome.name());
    }

    @Test
    @DisplayName("a confirmed outcome cannot be sent as a failure")
    void aConfirmedOutcomeCannotBeSentAsAFailure() {
        assertThatThrownBy(() -> PayinMessages.failed(PayinOutcome.CONFIRMED, Money.ofPaise(1L),
                PaymentRoute.UPI, List.of(), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an unresolved payin is presented as unknown, and tells the user not to pay again")
    void anUnresolvedPayinIsUnknownNotFailed() {
        // Rule A9b. The recovery is the opposite of a failure's: paying again is how a trader ends
        // up debited twice for one deposit.
        MessageSpec spec = PayinMessages.failed(PayinOutcome.UNKNOWN, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false);

        assertThat(spec.parameters())
                .containsEntry("resolution", "UNKNOWN")
                .containsEntry("doNotRetry", "true");
    }

    @Test
    @DisplayName("a bank decline is presented as failed, and retrying is not discouraged")
    void aBankDeclineIsFailed() {
        MessageSpec spec = PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false);

        assertThat(spec.parameters())
                .containsEntry("resolution", "FAILED")
                .containsEntry("doNotRetry", "false");
    }

    @ParameterizedTest
    @EnumSource(value = PayinOutcome.class,
            names = {"CANCELLED_BY_USER", "SERVICE_UNREACHABLE"})
    @DisplayName("where the payment never reached the bank, the refund is not stated conditionally")
    void whereNothingReachedTheBankTheRefundIsNotConditional(PayinOutcome outcome) {
        assertThat(PayinMessages.failed(outcome, Money.ofPaise(500_000L), PaymentRoute.UPI,
                List.of(), false).parameters())
                .containsEntry("refundConditional", "false");
    }

    @ParameterizedTest
    @EnumSource(value = PayinOutcome.class,
            names = {"BANK_DECLINED", "INSUFFICIENT_FUNDS_AT_BANK", "ABOVE_BANK_LIMIT", "UNKNOWN",
                    "AWAITING_BANK"})
    @DisplayName("where the bank may have debited, the refund is stated conditionally")
    void whereTheBankMayHaveDebitedTheRefundIsConditional(PayinOutcome outcome) {
        // Rule C5. Asserting that nothing was taken is the one thing that must not be said, because
        // it is sometimes false and the trader stops looking for their money.
        assertThat(PayinMessages.failed(outcome, Money.ofPaise(500_000L), PaymentRoute.UPI,
                List.of(), false).parameters())
                .containsEntry("refundConditional", "true");
    }

    @Test
    @DisplayName("our own outage is owned rather than blamed on the bank")
    void ourOwnOutageIsOwned() {
        // Rule A9c. A trader told "declined" for our outage phones their bank, which knows nothing
        // about it.
        assertThat(PayinMessages.failed(PayinOutcome.SERVICE_UNREACHABLE, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false).parameters())
                .containsEntry("causeIsOurs", "true");

        assertThat(PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false).parameters())
                .containsEntry("causeIsOurs", "false");
    }

    @Test
    @DisplayName("only routes that can actually be executed are offered")
    void onlyExecutableRoutesAreOffered() {
        // Rule A9d. Offering a route with no headroom sends the trader into a second refusal.
        assertThat(PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(PaymentRoute.NEFT), false).parameters())
                .containsEntry("alternativeRoutes", "NEFT");

        assertThat(PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(500_000L),
                PaymentRoute.UPI, List.of(), false).parameters())
                .containsEntry("alternativeRoutes", "");
    }

    @Test
    @DisplayName("a failure goes to WhatsApp where opted in, and to email otherwise")
    void aFailureFallsBackToEmail() {
        // Rule C2 puts adding-funds failure on WhatsApp; Rule C4 makes email the fallback, never
        // silence.
        assertThat(PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(1L),
                PaymentRoute.UPI, List.of(), true).channel()).isEqualTo(MessageChannel.WHATSAPP);
        assertThat(PayinMessages.failed(PayinOutcome.BANK_DECLINED, Money.ofPaise(1L),
                PaymentRoute.UPI, List.of(), false).channel()).isEqualTo(MessageChannel.EMAIL);
    }
}
