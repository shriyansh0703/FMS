package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.RequestAlreadyOpenException;
import com.thinq.fms.platform.money.AccountRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for withdrawal requests (lld-backend.md §6.4).
 *
 * <p>Every method takes an {@link AccountRef}. That is authorisation, not convenience:
 * §4.3 requires it enforced per object in the service layer and never inferred from a path, and a
 * repository method that could be called without an account is one a caller can forget to scope.
 */
public interface PayoutRequestRepository {

    /**
     * The single open request for an account, if any.
     *
     * <p><b>Not for pre-checking before an insert.</b> Rule W4 is enforced by V21's partial unique
     * index; reading first and then writing is a race dressed as validation. This exists to show a
     * trader their open request, not to decide whether they may create one.
     *
     * @return empty when there is none. Never throws for absence
     */
    Optional<PayoutRequest> openFor(AccountRef account);

    /**
     * Persist a new request or an updated one.
     *
     * <p><b>Insert versus update is decided by the id.</b> A request with {@code id == 0} has not
     * been persisted and is inserted; any other id is an update. The orchestrator constructs a new
     * request with id 0 and relies on this.
     *
     * <p><b>The returned instance carries the assigned id, and is not necessarily the argument.</b>
     * {@code PayoutRequest.id} is final, so an implementation inserting a new row must construct a
     * fresh instance rather than mutating the one it was given. Callers must use the return value
     * — discarding it leaves them holding a request whose id is still 0.
     *
     * @throws RequestAlreadyOpenException translated from the unique-index violation on
     *     {@code fms_payout_one_open_per_account}. The caller does not pre-check
     */
    PayoutRequest save(PayoutRequest request);

    /**
     * One request by id, scoped to its owner.
     *
     * @return empty both when the id does not exist and when it belongs to someone else. The two
     *     are deliberately indistinguishable — §4.3 requires another trader's movement to answer
     *     404 rather than 403, because confirming existence would itself leak
     */
    Optional<PayoutRequest> findFor(AccountRef account, long id);

    /**
     * Requests eligible for a run, oldest first.
     *
     * <p>Ordered by request time so a re-run processes in the same order as the run it repeats.
     * An unordered scan would make a partially completed run non-repeatable, and §6.3's recovery
     * depends on repeating it.
     *
     * @return empty list, never null
     */
    List<PayoutRequest> openRequestsForRun(LocalDate runDate);
}
