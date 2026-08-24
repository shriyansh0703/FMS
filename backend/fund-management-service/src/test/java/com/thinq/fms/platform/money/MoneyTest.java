package com.thinq.fms.platform.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The primitive every figure in this system is expressed in.
 *
 * <p>It had no test class of its own until now, which is how the type carrying every rupee in the
 * product came to have the weakest branch coverage of anything exercised. The arithmetic is small
 * enough to read and be satisfied by, and that is exactly the reason it went unchecked: an
 * overflow that wraps produces a valid-looking money figure, and a wrong number that looks right is
 * the failure mode this whole design is built to avoid.
 */
class MoneyTest {

    @Test
    @DisplayName("addition that would overflow throws rather than wrapping")
    void additionOverflowThrows() {
        // A wrapped total is a silently wrong money figure. Math.addExact is the reason this
        // throws, and this is what proves the operation actually routes through it.
        assertThatThrownBy(() -> Money.ofPaise(Long.MAX_VALUE).plus(Money.ofPaise(1L)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("subtraction that would overflow throws rather than wrapping")
    void subtractionOverflowThrows() {
        assertThatThrownBy(() -> Money.ofPaise(Long.MIN_VALUE).minus(Money.ofPaise(1L)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("negating the smallest long throws rather than returning itself")
    void negatingTheSmallestLongThrows() {
        // Long.MIN_VALUE has no positive counterpart, so a plain unary minus returns it unchanged.
        // A debit balance that stayed negative after being negated would be read as a credit.
        assertThatThrownBy(() -> Money.ofPaise(Long.MIN_VALUE).negated())
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("addition and subtraction are exact at ordinary magnitudes")
    void arithmeticIsExact() {
        assertThat(Money.ofPaise(1_50_000L).plus(Money.ofPaise(2_50_000L)))
                .isEqualTo(Money.ofPaise(4_00_000L));
        assertThat(Money.ofPaise(4_00_000L).minus(Money.ofPaise(1_50_000L)))
                .isEqualTo(Money.ofPaise(2_50_000L));
        assertThat(Money.ofPaise(-75_000L).negated()).isEqualTo(Money.ofPaise(75_000L));
    }

    @ParameterizedTest(name = "{0} paise: negative={1} zero={2} positive={3}")
    @CsvSource({"-1, true, false, false", "0, false, true, false", "1, false, false, true"})
    @DisplayName("the three predicates partition the number line without overlapping")
    void predicatesPartitionTheNumberLine(long paise, boolean negative, boolean zero, boolean positive) {
        Money money = Money.ofPaise(paise);

        assertThat(money.isNegative()).isEqualTo(negative);
        assertThat(money.isZero()).isEqualTo(zero);
        assertThat(money.isPositive()).isEqualTo(positive);
    }

    @Test
    @DisplayName("flooredAtZero clamps only the negative side — Rule B9's single exception")
    void flooredAtZeroClampsOnlyTheNegativeSide() {
        assertThat(Money.ofPaise(-1L).flooredAtZero()).isEqualTo(Money.ZERO);
        assertThat(Money.ZERO.flooredAtZero()).isEqualTo(Money.ZERO);
        assertThat(Money.ofPaise(1L).flooredAtZero()).isEqualTo(Money.ofPaise(1L));
    }

    @ParameterizedTest(name = "{0} rupees round-trips as a two-place decimal")
    @ValueSource(strings = {"0.00", "0.01", "1.00", "1.99", "99999999.99", "-1.50"})
    @DisplayName("vendor decimals convert both ways without loss")
    void vendorDecimalsConvertBothWays(String rupees) {
        Money money = Money.ofVendorDecimal(rupees);

        assertThat(money.toVendorDecimal().toPlainString()).isEqualTo(rupees);
    }

    @Test
    @DisplayName("a vendor decimal is read exactly, not through a double")
    void vendorDecimalIsReadExactly() {
        // new BigDecimal(0.1) captures binary floating-point imprecision; the String form does not.
        // One paise per conversion is a reconciliation break nobody can explain a month later.
        assertThat(Money.ofVendorDecimal("0.10").paise()).isEqualTo(10L);
        assertThat(Money.ofVendorDecimal(new BigDecimal("1234.56")).paise()).isEqualTo(123_456L);
    }

    @Test
    @DisplayName("ordering is by paise, so sorting money never depends on its rendering")
    void orderingIsByPaise() {
        assertThat(Money.ofPaise(100L)).isGreaterThan(Money.ofPaise(99L));
        assertThat(Money.ofPaise(-100L)).isLessThan(Money.ZERO);
        assertThat(Money.ofPaise(100L)).isEqualByComparingTo(Money.ofPaise(100L));
    }
}
