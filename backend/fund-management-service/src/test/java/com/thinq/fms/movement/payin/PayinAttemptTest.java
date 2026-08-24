package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * The attempt entity's own rules, as opposed to the orchestration around it.
 *
 * <p>Written from mutation results. Three mutants survived the whole suite here: disabling the
 * transition guard, turning {@code version++} into {@code version--}, and negating the terminal
 * check in {@code recordOutcome}. Each is a rule the code states and no test asserted — and the
 * version one now carries optimistic locking, so a broken increment is a lost update on a money row
 * rather than a cosmetic defect.
 */
class PayinAttemptTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");

    private PayinAttempt attempt(PayinState state) {
        return new PayinAttempt(1L, ACCOUNT, Money.ofPaise(500_000L), PaymentRoute.UPI,
                NOW, state, 0);
    }

    @Test
    @DisplayName("an illegal transition is refused, with the states named")
    void anIllegalTransitionIsRefused() {
        // Nothing in the suite asserted this, so replacing canTransitionTo's body with `return
        // true` — disabling every rule in the table — went unnoticed.
        PayinAttempt failed = attempt(PayinState.FAILED);

        assertThatThrownBy(() -> failed.recordOutcome(PayinOutcome.AWAITING_BANK, NOW))
                .isInstanceOf(FmsInvariantException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("a confirmation after a terminal failure is refused rather than credited")
    void aConfirmationAfterAFailureIsRefused() {
        // The other side of Rule A7. A late confirmation must land on an unresolved attempt, but a
        // FAILED attempt turning CONFIRMED is a contradiction the gateway should not produce, and
        // acting on it credits money against an attempt already reported as failed.
        PayinAttempt failed = attempt(PayinState.FAILED);

        assertThatThrownBy(() -> failed.recordOutcome(PayinOutcome.CONFIRMED, NOW))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_terminal_state_changed"));
    }

    @Test
    @DisplayName("a confirmed attempt may still be reversed, which is the terminal exception")
    void aConfirmedAttemptMayStillBeReversed() {
        PayinAttempt confirmed = attempt(PayinState.CONFIRMED);

        assertThatCode(() -> confirmed.reverse(NOW)).doesNotThrowAnyException();
        assertThat(confirmed.state()).isEqualTo(PayinState.REVERSED);
        assertThat(confirmed.resolvedAt()).contains(NOW);
    }

    @Test
    @DisplayName("only a confirmed attempt can be reversed")
    void onlyAConfirmedAttemptCanBeReversed() {
        assertThatThrownBy(() -> attempt(PayinState.AT_GATEWAY).reverse(NOW))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_not_reversible"));
    }

    @Test
    @DisplayName("every state change increments the version, because a write compares against it")
    void everyStateChangeIncrementsTheVersion() {
        // `version--` survived as a mutation. It is not cosmetic: the repository writes
        // `SET version = ?` and matches `WHERE version = loadedVersion`, so an increment going the
        // wrong way makes concurrent writers agree when they should collide.
        PayinAttempt attempt = attempt(PayinState.INITIATED);
        assertThat(attempt.version()).isZero();

        attempt.willUseGatewayReference("FMS-PAYIN-1");
        assertThat(attempt.version()).as("assigning the reference is a write").isEqualTo(1);

        attempt.sentToGateway("FMS-PAYIN-1");
        assertThat(attempt.version()).isEqualTo(2);

        attempt.recordOutcome(PayinOutcome.CONFIRMED, NOW);
        assertThat(attempt.version()).isEqualTo(3);
        // Resolution time is stamped only for a terminal outcome, and CONFIRMED is one. Negating
        // that check survived as a mutation, which would leave finished attempts undated and date
        // the unfinished ones.
        assertThat(attempt.resolvedAt()).contains(NOW);

        attempt.reverse(NOW.plusSeconds(60));
        assertThat(attempt.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("a repeat confirmation changes nothing and does not bump the version")
    void aRepeatConfirmationChangesNothing() {
        // Rule A6: repeat confirmations are expected, not exceptional. A version bump on a no-op
        // would make the next legitimate write collide against a row nothing actually changed.
        PayinAttempt attempt = attempt(PayinState.CONFIRMED);
        int before = attempt.version();

        assertThat(attempt.recordOutcome(PayinOutcome.CONFIRMED, NOW))
                .as("reports that it changed nothing").isFalse();
        assertThat(attempt.version()).isEqualTo(before);
    }

    @Test
    @DisplayName("the gateway reference cannot be assigned once the attempt has moved on")
    void theReferenceCannotBeAssignedLate() {
        PayinAttempt sent = attempt(PayinState.AT_GATEWAY);

        assertThatThrownBy(() -> sent.willUseGatewayReference("FMS-PAYIN-1"))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_reference_assigned_late"));
    }

    @Test
    @DisplayName("an awaiting outcome is not a resolution, so it carries no resolved time")
    void anAwaitingOutcomeIsNotAResolution() {
        PayinAttempt attempt = attempt(PayinState.AT_GATEWAY);

        attempt.recordOutcome(PayinOutcome.AWAITING_BANK, NOW);

        assertThat(attempt.state()).isEqualTo(PayinState.AWAITING_BANK);
        assertThat(attempt.resolvedAt()).as("Rule A9b: still in progress, so not resolved").isEmpty();
    }

    @Test
    @DisplayName("the funding source is recorded masked, never in full — REQ-612")
    void theFundingSourceIsRecordedMasked() {
        PayinAttempt attempt = attempt(PayinState.INITIATED);
        assertThat(attempt.sourceMasked()).isEmpty();

        attempt.recordSourceMasked("4471");

        assertThat(attempt.sourceMasked()).contains("4471");
    }

    @Test
    @DisplayName("an attempt is for a positive amount")
    void anAttemptIsForAPositiveAmount() {
        assertThatThrownBy(() -> new PayinAttempt(1L, ACCOUNT, Money.ZERO, PaymentRoute.UPI,
                NOW, PayinState.INITIATED, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
