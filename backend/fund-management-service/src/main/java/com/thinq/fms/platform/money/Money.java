package com.thinq.fms.platform.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount, held as an integer number of paise.
 *
 * <p>This is the only monetary type in the Fund Management System. Nothing else may
 * represent money — not {@code double}, not {@code float}, not a decimal string parsed
 * late, and not a bare {@code long} passed between layers where its unit is implied.
 *
 * <p><b>Why paise and not BigDecimal.</b> The ratified event taxonomy states money is an
 * integer in paise (rule R5), and Rule B4 makes it structural rather than stylistic: the
 * withdrawable derivation must reconcile to its figure exactly, and REQ-102 shows every
 * term of that derivation to the trader. A representation that cannot hold a sum exactly
 * turns a rounding artefact into a visible contradiction between the terms and the total,
 * which both of Balances &amp; Margin's Flow 2 error paths treat as a correctness failure
 * severe enough to block withdrawal.
 *
 * <p>A {@code long} of paise carries ±9.2 × 10^16 rupees, which is not a constraint worth
 * revisiting. It is also strictly stronger than {@code BigDecimal} here: a fractional
 * paisa is unrepresentable rather than merely discouraged.
 *
 * <p><b>Where BigDecimal is still correct.</b> Only at the vendor boundary, and only
 * constructed from a {@code String} or from this type's own paise value — never from a
 * {@code double} literal. See {@link #toVendorDecimal()} and {@link #ofVendorDecimal}.
 */
public record Money(long paise) implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100L);

    public static Money ofPaise(long paise) {
        return new Money(paise);
    }


    public Money plus(Money other) {
        return new Money(Math.addExact(this.paise, other.paise));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(this.paise, other.paise));
    }

    public Money negated() {
        return new Money(Math.negateExact(this.paise));
    }

    /**
     * This amount, or zero if it is negative.
     *
     * <p>Rule B9's single exception. Negative values are legitimate throughout this system
     * — a ledger balance may be negative, a committed-margin component may be negative —
     * and are never clamped. The withdrawable figure alone floors at zero, because it
     * answers "what can reach my bank today" and there is no negative answer to that.
     *
     * <p>Deliberately named so that a caller reaching for it outside that one context has
     * to think about why.
     */
    public Money flooredAtZero() {
        return this.paise < 0L ? ZERO : this;
    }

    public boolean isNegative() {
        return this.paise < 0L;
    }

    public boolean isZero() {
        return this.paise == 0L;
    }

    public boolean isPositive() {
        return this.paise > 0L;
    }

    /**
     * Rupees with exactly two decimal places, for a vendor that exchanges decimal strings.
     *
     * <p>TechExcel's {@code Payout_Request_Addition} types {@code Amount} as a string with
     * precision 20,2. This is the only direction in which money leaves this type, and the
     * conversion happens once, at the anti-corruption layer — never in a service, and
     * never at the point a value is rendered.
     */
    public BigDecimal toVendorDecimal() {
        return BigDecimal.valueOf(this.paise).divide(PAISE_PER_RUPEE, 2, RoundingMode.UNNECESSARY);
    }

    /**
     * Rupees from a vendor's decimal, converted to paise on ingest.
     *
     * @throws ArithmeticException if the value carries sub-paise precision. A vendor
     *     returning more precision than a paisa is a contract violation this must surface
     *     rather than round away — rounding money silently is the failure this whole type
     *     exists to prevent.
     */
    public static Money ofVendorDecimal(BigDecimal rupees) {
        Objects.requireNonNull(rupees, "rupees");
        return new Money(rupees.multiply(PAISE_PER_RUPEE).setScale(0, RoundingMode.UNNECESSARY).longValueExact());
    }

    /**
     * Rupees from a vendor's decimal <i>string</i>.
     *
     * <p>Takes a {@code String} rather than a {@code double} on purpose:
     * {@code new BigDecimal(0.1)} captures binary floating-point imprecision,
     * {@code new BigDecimal("0.1")} does not. There is no overload taking a double, and
     * there must never be one.
     */
    public static Money ofVendorDecimal(String rupees) {
        return ofVendorDecimal(new BigDecimal(Objects.requireNonNull(rupees, "rupees")));
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.paise, other.paise);
    }

    /**
     * Diagnostic only. Never render this to a trader — user-facing formatting is the
     * client's, from the paise value, per the frontend design's rule that the only
     * formatter lives at its API boundary.
     */
    @Override
    public String toString() {
        return "Money[" + this.paise + "p]";
    }
}
