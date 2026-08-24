package com.thinq.fms.integration.profile;

import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;

import java.util.List;
import java.util.Optional;

/**
 * What FMS reads from Profile, and the whole of it.
 *
 * <p>FMS reads the proven-account list and <b>never mutates it</b>. Add, delete and set-primary
 * are Profile's (lld-backend.md §1.3), so no method here writes.
 *
 * <h2>Why there is no caching method on this interface</h2>
 *
 * <p>Profile PR-28 requires the list be read at the moment of each decision rather than cached
 * for a journey, and the reason is a real sequence: a trader adds an account, its verification
 * resolves while they are still in the app, and a cached list would tell them it is unverified
 * until they navigate away and back. The reverse case matters more — an account whose
 * verification was withdrawn must stop being a legal destination immediately, not at the end of
 * a session.
 *
 * <p>So a caller wanting "the verified list" gets it from the source every time. Adding a cached
 * variant here would make the unsafe path the convenient one.
 *
 * <h2>Implementation status</h2>
 *
 * <p><b>This interface has no implementation in this pass, deliberately.</b> The semantics above
 * are fixed by the PRD and the LLD, but no artifact in this repository specifies Profile's HTTP
 * contract — no paths, no request or response shapes, no error vocabulary. Writing a client
 * against an invented wire format would produce code that compiles, reviews well, and cannot
 * work. The contract is an outstanding input; see the Stage 8 handoff notes.
 */
public interface ProfileClient {

    /**
     * The trader's bank accounts as Profile holds them right now.
     *
     * <p>Includes unverified accounts, so a caller can tell "you have no accounts" from "your
     * account is still being verified" — REQ-505 requires the blocker named, and those two
     * blockers have different answers.
     *
     * @return an empty list when the trader has none, never null
     * @throws VendorUnavailableException when Profile cannot be reached. Callers must not fall
     *     back to a stored list: proceeding on a stale list is how a withdrawal reaches an
     *     account whose verification was withdrawn
     */
    List<VerifiedBankAccount> accountsOf(AccountRef account);

    /**
     * One account, if Profile still holds it for this trader.
     *
     * <p>Used at the point a withdrawal is requested, against the reference pinned under
     * Rule W12. Returns empty when the reference is unknown to Profile or belongs to someone
     * else — the two are deliberately indistinguishable to the caller, because telling them
     * apart would let a caller probe for other traders' account references.
     */
    Optional<VerifiedBankAccount> accountOf(AccountRef account, String reference);

    /**
     * The trader's primary account, which is the default source and destination (REQ-706).
     *
     * @return empty when none is set, which is a legitimate state for a new account rather than
     *     an error
     */
    Optional<VerifiedBankAccount> primaryAccountOf(AccountRef account);
}
