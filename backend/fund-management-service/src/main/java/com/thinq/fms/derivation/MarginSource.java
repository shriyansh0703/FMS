package com.thinq.fms.derivation;

import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Instant;

/**
 * One system's answer to the margin questions, per lld-backend.md §6.1.
 *
 * <p>Two implementations exist because the HLD's hard cutover means the same question has a
 * different authority depending on the clock: Noren's RMS while the market is open, TechExcel
 * after end of day. {@code MarginSourceSelector} picks; nothing else branches on the time.
 *
 * <p><b>This interface deliberately excludes payout.</b> Noren offers {@code WithdrawFunds},
 * and putting it here would force the back-office implementation to expose a payout capability
 * it must not have. Moving money out is {@link com.thinq.fms.movement.payout.PayoutRail},
 * which is a separate interface precisely so exactly one implementation of it can be
 * registered (lld-backend.md §5, §7.6).
 */
public interface MarginSource {

    /**
     * The margin figures for one account.
     *
     * @throws VendorUnavailableException when the source cannot be reached. Never returns a
     *     partially populated result — a missing figure makes the answer unavailable rather
     *     than making it zero, because zero is a number a trader would act on.
     */
    MarginFigures margin(AccountRef account);

    /**
     * What this source says may actually leave the account.
     *
     * <p><b>RMS's answer is the authority</b> (hld.md §8.0). Rule B4's six terms explain this
     * figure; they do not override it. Where the derivation and this figure disagree, the
     * withdrawable figure is presented as unavailable and no withdrawal may be requested —
     * neither system is silently picked as the winner.
     *
     * @throws VendorUnavailableException when the source cannot be reached
     */
    Money withdrawableAuthority(AccountRef account);

    /**
     * When the figures above were computed by the source.
     *
     * <p>Never null. REQ-107 must state how current every margin figure is, and a figure
     * without its instant cannot satisfy that — so the absence of a timestamp is a failure of
     * this method rather than a null the caller handles.
     */
    Instant computedAt();

    MarginSourceKind kind();
}
