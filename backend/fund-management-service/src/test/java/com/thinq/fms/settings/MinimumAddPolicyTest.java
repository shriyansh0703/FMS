package com.thinq.fms.settings;

import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-703 and Rule H3 — the floor, and the one case that must get past it. */
class MinimumAddPolicyTest {

    private static final Money MINIMUM = Money.ofPaise(10_000L);
    private final MinimumAddPolicy policy = new MinimumAddPolicy(MINIMUM);

    @Test
    @DisplayName("an amount at or above the minimum is permitted")
    void atOrAboveTheMinimumIsPermitted() {
        assertThat(policy.permits(MINIMUM, Money.ZERO)).isTrue();
        assertThat(policy.permits(Money.ofPaise(10_001L), Money.ZERO)).isTrue();
    }

    @Test
    @DisplayName("an amount below the minimum is refused where nothing is owed")
    void belowTheMinimumIsRefusedWithNoDebt() {
        assertThat(policy.permits(Money.ofPaise(9_999L), Money.ZERO)).isFalse();
    }

    @Test
    @DisplayName("the exact debt is permitted even though it is below the minimum")
    void theExactDebtIsPermitted() {
        // Rule H3. Without this an account owing ₹40 cannot settle it without depositing more than
        // it owes — our commercial floor standing between the trader and the one thing both sides
        // want.
        assertThat(policy.permits(Money.ofPaise(4_000L), Money.ofPaise(4_000L))).isTrue();
    }

    @ParameterizedTest(name = "owed {1}p, adding {0}p: permitted = {2}")
    @CsvSource({
            "4000, 4000, true",    // exact
            "4001, 4000, false",   // a rupee over the debt is an ordinary amount
            "3999, 4000, false",   // under the debt does not settle it
            "10000, 4000, true"})  // at the floor, permitted regardless
    @DisplayName("the waiver is exact, and applies only to the amount that settles the debt")
    void theWaiverIsExact(long amount, long owed, boolean permitted) {
        assertThat(policy.permits(Money.ofPaise(amount), Money.ofPaise(owed))).isEqualTo(permitted);
    }

    @Test
    @DisplayName("a non-positive amount is never permitted")
    void aNonPositiveAmountIsNeverPermitted() {
        assertThat(policy.permits(Money.ZERO, Money.ofPaise(4_000L))).isFalse();
        assertThat(policy.permits(Money.ofPaise(-1L), Money.ZERO)).isFalse();
    }

    @Test
    @DisplayName("the suggestion is the exact debt while in debt, and the minimum otherwise")
    void theSuggestionFollowsTheDebt() {
        // REQ-502. Suggesting the minimum to an account owing less would tell the trader to deposit
        // more than they owe.
        assertThat(policy.suggestedAmount(Money.ofPaise(4_000L))).isEqualTo(Money.ofPaise(4_000L));
        assertThat(policy.suggestedAmount(Money.ZERO)).isEqualTo(MINIMUM);
    }
}
