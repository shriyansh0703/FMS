package com.thinq.fms.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-708 — three states that are deliberately not interchangeable. */
class DebitInterestRateTest {

    @Test
    @DisplayName("a configured rate may be quoted in a message")
    void aConfiguredRateMayBeQuoted() {
        DebitInterestRate rate = DebitInterestRate.configured(new BigDecimal("18.00"));

        assertThat(rate.quotableInMessages()).isTrue();
        assertThat(rate.canComputeAccrual()).isTrue();
    }

    @Test
    @DisplayName("a provisional rate is never quoted in a message, though it still computes")
    void aProvisionalRateIsNeverQuoted() {
        // REQ-708 forbids sending any message quoting a stand-in. A trader told 18% and charged
        // something else has been misinformed by us, not by a configuration gap. The figure is
        // still computable for a non-production display, which is why the two answers differ.
        DebitInterestRate rate = DebitInterestRate.provisional(new BigDecimal("18.00"));

        assertThat(rate.quotableInMessages()).isFalse();
        assertThat(rate.canComputeAccrual()).isTrue();
    }

    @Test
    @DisplayName("an unavailable rate computes nothing and quotes nothing")
    void anUnavailableRateComputesNothing() {
        // The debt is still stated; only the accrual figure is withheld. Presenting the debt as
        // static would tell the trader it is not growing.
        DebitInterestRate rate = DebitInterestRate.unavailable();

        assertThat(rate.quotableInMessages()).isFalse();
        assertThat(rate.canComputeAccrual()).isFalse();
        assertThat(rate.annualPercent()).isEmpty();
    }

    @Test
    @DisplayName("the shipped default is provisional, because TechExcel does not carry the rate yet")
    void theShippedDefaultIsProvisional() {
        assertThat(FundsSettings.defaults().debitInterest().provisional())
                .as("marking it configured would let it into a message")
                .isTrue();
        assertThat(FundsSettings.defaults().debitInterest().quotableInMessages()).isFalse();
    }

    @Test
    @DisplayName("a zero rate is a rate, not an absence")
    void aZeroRateIsARate() {
        // Interest waived is a real commercial decision and reads differently to "we could not
        // obtain the rate". Rejecting zero would force the waiver to be expressed as unavailable,
        // which would then suppress the accrual line entirely.
        DebitInterestRate waived = DebitInterestRate.configured(BigDecimal.ZERO);

        assertThat(waived.canComputeAccrual()).isTrue();
        assertThat(waived.quotableInMessages()).isTrue();
    }

    @Test
    @DisplayName("a negative rate is refused")
    void aNegativeRateIsRefused() {
        assertThatThrownBy(() -> DebitInterestRate.configured(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
