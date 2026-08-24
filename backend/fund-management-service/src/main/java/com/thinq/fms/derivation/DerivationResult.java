package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a derivation: the figure where there is one, the explanation, and the provenance
 * REQ-107 requires rendered.
 *
 * @param verdict      whether the derivation and RMS agree. {@code RECONCILED} is the only verdict
 *                     under which {@code derivation} carries a usable figure
 * @param derivation   Rule B4's terms and total, present unless the inputs could not be assembled
 * @param rmsAuthority RMS's own answer, present when RMS was reachable
 * @param computedAt   when the figures were computed by their source. REQ-107 renders this
 * @param computedBy   which source answered. REQ-107 renders this too, so a figure stepping at the
 *                     market-open boundary reads as a scheduled handover rather than a data error
 */
public record DerivationResult(
        WithdrawableVerdict verdict,
        Derivation derivation,
        Money rmsAuthority,
        Instant computedAt,
        MarginSourceKind computedBy) {

    public DerivationResult {
        Objects.requireNonNull(verdict, "verdict");
        if (verdict == WithdrawableVerdict.RECONCILED && derivation == null) {
            throw new IllegalArgumentException("a RECONCILED result must carry its derivation");
        }
    }

    /**
     * The figure a trader may act on.
     *
     * <p>Empty unless the verdict is {@code RECONCILED}. Returning RMS's figure on a
     * {@code DIVERGENT} verdict would silently resolve a disagreement the design says must not be
     * resolved silently.
     */
    public Optional<Money> withdrawable() {
        return this.verdict == WithdrawableVerdict.RECONCILED
                ? Optional.of(this.derivation.withdrawable())
                : Optional.empty();
    }

    public Optional<Derivation> derivationIfPresent() {
        return Optional.ofNullable(this.derivation);
    }

    public boolean isActionable() {
        return this.verdict == WithdrawableVerdict.RECONCILED;
    }

    /** An unavailable result, for a source outage or an unnominated calendar (OA-5). */
    public static DerivationResult unavailable(Instant at, MarginSourceKind by) {
        return new DerivationResult(WithdrawableVerdict.UNAVAILABLE, null, null, at, by);
    }
}
