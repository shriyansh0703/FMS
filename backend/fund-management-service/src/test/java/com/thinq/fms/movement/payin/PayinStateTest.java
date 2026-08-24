package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payin state machine, which had no test of its own.
 *
 * <p>Mutation testing is what surfaced that: replacing {@code canTransitionTo}'s body with
 * {@code return true} — disabling every transition rule in the system — survived the entire suite,
 * because the table was only ever exercised along paths that happened to be legal. Nothing asserted
 * that an illegal move is refused, so nothing would have noticed the table being wrong.
 *
 * <p>That matters more than usual here, since F-36 widened {@code INITIATED} to reach CONFIRMED and
 * AWAITING_BANK directly, and the widening is exactly the kind of change that wants a test naming
 * what is still forbidden.
 */
class PayinStateTest {

    @Test
    @DisplayName("INITIATED can resolve directly, because a timed-out createOrder may still settle")
    void initiatedCanResolveDirectly() {
        // F-30/F-36: a timeout abandons the wait, not the call, so an attempt the gateway never
        // acknowledged can still be confirmed. Without these the money is refused.
        assertThat(PayinState.INITIATED.canTransitionTo(PayinState.CONFIRMED)).isTrue();
        assertThat(PayinState.INITIATED.canTransitionTo(PayinState.AWAITING_BANK)).isTrue();
        assertThat(PayinState.INITIATED.canTransitionTo(PayinState.AT_GATEWAY)).isTrue();
        assertThat(PayinState.INITIATED.canTransitionTo(PayinState.FAILED)).isTrue();
        assertThat(PayinState.INITIATED.canTransitionTo(PayinState.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("nothing reaches REVERSED except a confirmed payin")
    void onlyAConfirmedPayinIsReversible() {
        // Rule A10 reverses money that arrived. Reversing anything else would compensate for a
        // credit that was never made.
        for (PayinState from : PayinState.values()) {
            assertThat(from.canTransitionTo(PayinState.REVERSED))
                    .as("%s -> REVERSED", from)
                    .isEqualTo(from == PayinState.CONFIRMED);
        }
    }

    @Test
    @DisplayName("AWAITING_BANK resolves either way but cannot be cancelled")
    void awaitingBankResolvesButCannotBeCancelled() {
        // Rule A9b: neither success nor failure, and not the trader's to cancel — the bank may
        // already have taken the money.
        assertThat(PayinState.AWAITING_BANK.canTransitionTo(PayinState.CONFIRMED)).isTrue();
        assertThat(PayinState.AWAITING_BANK.canTransitionTo(PayinState.FAILED)).isTrue();
        assertThat(PayinState.AWAITING_BANK.canTransitionTo(PayinState.CANCELLED)).isFalse();
        assertThat(PayinState.AWAITING_BANK.canTransitionTo(PayinState.AT_GATEWAY)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PayinState.class, names = {"FAILED", "CANCELLED", "REVERSED"})
    @DisplayName("a terminal state goes nowhere at all")
    void aTerminalStateGoesNowhere(PayinState terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (PayinState to : PayinState.values()) {
            assertThat(terminal.canTransitionTo(to))
                    .as("%s must not reach %s", terminal, to)
                    .isFalse();
        }
        assertThat(terminal.allowedNext()).isEmpty();
    }

    @Test
    @DisplayName("a state never transitions to itself")
    void noSelfTransitions() {
        for (PayinState state : PayinState.values()) {
            assertThat(state.canTransitionTo(state)).as("%s -> itself", state).isFalse();
        }
    }

    @Test
    @DisplayName("CONFIRMED is the only state that counts as money the account has")
    void onlyConfirmedAffectsBalance() {
        // Rule A5. If REVERSED also affected the balance, reversed money would still be spendable;
        // if CONFIRMED did not, real deposits would be invisible to every figure.
        for (PayinState state : PayinState.values()) {
            assertThat(state.affectsBalance())
                    .as("%s affects balance", state)
                    .isEqualTo(state == PayinState.CONFIRMED);
        }
    }

    @Test
    @DisplayName("the non-terminal states are exactly the ones still in flight")
    void inFlightIsTheComplementOfTerminal() {
        Set<PayinState> inFlight = EnumSet.noneOf(PayinState.class);
        for (PayinState state : PayinState.values()) {
            assertThat(state.isInFlight()).as("%s", state).isEqualTo(!state.isTerminal());
            if (state.isInFlight()) {
                inFlight.add(state);
            }
        }
        assertThat(inFlight).containsExactlyInAnyOrder(
                PayinState.INITIATED, PayinState.AT_GATEWAY, PayinState.AWAITING_BANK);

        // CONFIRMED is terminal and therefore not in flight, yet it still has an outgoing
        // transition to REVERSED. That is the one deliberate exception — Rule A10 undoes money
        // that arrived — and it is why aTerminalStateGoesNowhere excludes it.
        assertThat(PayinState.CONFIRMED.isTerminal()).isTrue();
        assertThat(PayinState.CONFIRMED.allowedNext()).containsExactly(PayinState.REVERSED);
    }

    @Test
    @DisplayName("allowedNext agrees with canTransitionTo for every pair")
    void allowedNextAgreesWithCanTransitionTo() {
        // Two ways to ask the same question. A table read by one and not the other is how they
        // drift apart, and allowedNext returning an empty set survived as a mutation.
        for (PayinState from : PayinState.values()) {
            for (PayinState to : PayinState.values()) {
                assertThat(from.allowedNext().contains(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(from.canTransitionTo(to));
            }
        }
        assertThat(PayinState.INITIATED.allowedNext()).isNotEmpty();
    }

    @Test
    @DisplayName("an unknown outcome is held as awaiting, never as failed — Rule A9a")
    void unknownIsHeldNotFailed() {
        assertThat(PayinState.forOutcome(PayinOutcome.UNKNOWN)).isEqualTo(PayinState.AWAITING_BANK);
        assertThat(PayinState.forOutcome(PayinOutcome.AWAITING_BANK))
                .isEqualTo(PayinState.AWAITING_BANK);
        assertThat(PayinState.forOutcome(PayinOutcome.CONFIRMED)).isEqualTo(PayinState.CONFIRMED);
        assertThat(PayinState.forOutcome(PayinOutcome.BANK_DECLINED)).isEqualTo(PayinState.FAILED);
    }
}
