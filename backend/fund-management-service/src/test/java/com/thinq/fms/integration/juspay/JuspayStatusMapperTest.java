package com.thinq.fms.integration.juspay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vocabulary Juspay speaks, and the detector for when it changes.
 *
 * <p>{@code isUnmapped} is how this system notices the gateway has started sending a status it does
 * not recognise. It had no test, and an untested drift detector is one nobody knows will fire —
 * which matters more than usual here, because the consequence of a silently unrecognised status is
 * a payment treated as UNKNOWN when it was actually CHARGED.
 */
class JuspayStatusMapperTest {

    private final JuspayStatusMapper mapper = JuspayStatusMapper.withDefaults();

    @ParameterizedTest(name = "{0} maps to {1}")
    @CsvSource({
            "CHARGED, CONFIRMED",
            "AUTHORIZATION_FAILED, BANK_DECLINED",
            "AUTHENTICATION_FAILED, BANK_DECLINED",
            "JUSPAY_DECLINED, SERVICE_UNREACHABLE",
            "NEW, AWAITING_BANK",
            "STARTED, AWAITING_BANK",
            "PENDING_VBV, AWAITING_BANK",
            "AUTHORIZING, AWAITING_BANK"})
    @DisplayName("every known status maps to its outcome, and none of them is unmapped")
    void everyKnownStatusMaps(String raw, PayinOutcome expected) {
        assertThat(this.mapper.map(raw)).isEqualTo(expected);
        assertThat(this.mapper.isUnmapped(raw))
                .as("a status the mapper knows is not drift")
                .isFalse();
    }

    @Test
    @DisplayName("every in-progress status is awaiting, never failed — Rule A9b")
    void inProgressIsNeverFailed() {
        // The distinction that decides the recovery: wait, and specifically do not retry. Reading
        // an in-progress payment as failed is how a trader is told their money is gone while the
        // bank is still moving it.
        for (String inProgress : new String[]{"NEW", "STARTED", "PENDING_VBV", "AUTHORIZING"}) {
            assertThat(this.mapper.map(inProgress))
                    .as("%s is in progress", inProgress)
                    .isEqualTo(PayinOutcome.AWAITING_BANK);
        }
    }

    @ParameterizedTest(name = "an unrecognised status {0} is reported as drift")
    @ValueSource(strings = {"CHARGED_PARTIALLY", "VOIDED", "COD_INITIATED", "SOMETHING_NEW"})
    @DisplayName("a status the mapper has never seen is unmapped, not guessed")
    void anUnrecognisedStatusIsUnmapped(String raw) {
        // Guessing what an unfamiliar status meant is how a rejection gets read as a success.
        assertThat(this.mapper.map(raw)).isEqualTo(PayinOutcome.UNKNOWN);
        assertThat(this.mapper.isUnmapped(raw)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a null, empty or blank status is unknown rather than a crash")
    void aMissingStatusIsUnknown(String raw) {
        assertThat(this.mapper.map(raw)).isEqualTo(PayinOutcome.UNKNOWN);
        assertThat(this.mapper.isUnmapped(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"charged", "  CHARGED  ", "Charged"})
    @DisplayName("case and surrounding space do not make a known status look like drift")
    void caseAndSpaceDoNotCauseFalseDrift() {
        // A false drift signal is not harmless: it turns a confirmed payment into UNKNOWN, which
        // Rule A9b then holds rather than resolves.
        assertThat(this.mapper.map("charged")).isEqualTo(PayinOutcome.CONFIRMED);
        assertThat(this.mapper.isUnmapped("  CHARGED  ")).isFalse();
    }

    @Test
    @DisplayName("UNKNOWN is held open, not treated as a terminal failure")
    void unknownIsHeldOpenRatherThanFailed() {
        // Rule A9a: unknown is not failed. Drift must not present to the trader as a failed
        // deposit, so the outcome an unrecognised status maps to has to be non-terminal and
        // awaiting — the same shape as an in-progress payment.
        assertThat(PayinOutcome.UNKNOWN.isTerminal()).isFalse();
        assertThat(PayinOutcome.UNKNOWN.isAwaitingResolution()).isTrue();
        assertThat(PayinOutcome.BANK_DECLINED.isTerminal())
                .as("a real decline is terminal, so the two are genuinely different")
                .isTrue();
    }
}
