package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.VendorUnavailableException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The one path by which money leaves an account (lld-backend.md §7.6).
 *
 * <p>Three systems could execute a payout — Noren's {@code WithdrawFunds}, TechExcel's
 * {@code Payout_Request_Addition}, Juspay's payout orders (hld.md R8). Exactly one
 * implementation of this interface may be registered, asserted at startup rather than assumed,
 * because two live rails would instruct independently and Rule W9's combine-before-instruct
 * step would protect nothing.
 */
public interface PayoutRail {

    /**
     * Instruct the rail to send money.
     *
     * <p><b>Returns {@link InstructionResult}, not a settled outcome.</b> A rail may hold an
     * instruction pending authorisation, and TechExcel does exactly that — so a caller that
     * assumed a terminal outcome would close a request the rail has not acted on yet. See
     * {@link InstructionResult.PendingAuthorisation}.
     *
     * <p><b>Callers must consult {@link #statusOf} first on any re-run.</b> A timeout from this
     * method does not mean the rail did not act — see
     * {@link com.thinq.fms.integration.AbstractVendorGateway} — so treating a failure as
     * grounds to reissue is how one payout becomes two.
     *
     * @throws VendorUnavailableException when the rail cannot be reached. The caller moves the
     *     request to {@code QUEUED_FOR_RUN} rather than failing it: the trader still wants the
     *     money, and the next run should try again.
     */
    InstructionResult instruct(PaymentInstruction instruction);

    /**
     * What the rail did with a previously issued instruction, looked up by its key.
     *
     * <p>This exists so §6.3's end-of-day run can read before it reissues. That read is not
     * redundant and must not be removed as an optimisation: TechExcel's duplication validation
     * answers {@code Input_Value_Validation}, the same code it uses for an input-value
     * rejection, so a refusal cannot be interpreted as "already paid" (OA-7).
     *
     * @return empty when the rail has no record of the key, which is the only safe reading of
     *     "not found" — it means nothing was sent under it. A record that exists but is not yet
     *     authorised comes back as {@link InstructionResult.PendingAuthorisation}, which is a
     *     different thing entirely and must not be collapsed into empty
     * @throws VendorUnavailableException when the rail cannot be reached. The caller must NOT
     *     fall back to instructing blind.
     */
    Optional<InstructionResult> statusOf(InstructionKey key, LocalDate runDate);
}
