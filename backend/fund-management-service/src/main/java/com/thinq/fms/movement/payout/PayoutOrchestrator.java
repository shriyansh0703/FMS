package com.thinq.fms.movement.payout;

import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.derivation.DerivationResult;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.platform.error.AmountExceedsWithdrawableException;
import com.thinq.fms.platform.error.DestinationNotVerifiedException;
import com.thinq.fms.platform.error.RequestNotCancellableException;
import com.thinq.fms.platform.error.WithdrawableUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Creating and cancelling withdrawal requests (REQ-301, REQ-302, REQ-305).
 *
 * <h2>What this class does not do</h2>
 *
 * <p><b>It does not check for an existing open request before inserting.</b> Rule W4 is enforced
 * by V21's partial unique index and translated by the repository. A read-then-write here would be
 * a race dressed as validation: two requests arriving together would both find nothing and both
 * proceed, and the trader would commit the same money twice.
 *
 * <p><b>It reserves nothing.</b> Rule W3: a request holds no money aside. It is settled at end of
 * day against whatever is actually available then, which is why the amount at request and the
 * amount at settlement are stamped separately (Rule W11) and why the shrink warning exists.
 *
 * <h2>Order of checks</h2>
 *
 * <p>Derivation first, destination second, insert last. The derivation is the expensive call and
 * could reasonably go second — but a trader with nothing withdrawable should be told that rather
 * than told their bank account is unverified, and the more fundamental refusal should win.
 */
public final class PayoutOrchestrator {

    private final com.thinq.fms.messaging.MessageOutbox outbox;

    private final PayoutRequestRepository requests;
    private final BalanceDerivationService derivation;
    private final ProfileClient profile;
    private final FmsReferenceGenerator references;
    private final ArrivalDateQuoter arrivalDates;
    private final Clock clock;

    public PayoutOrchestrator(PayoutRequestRepository requests,
                              BalanceDerivationService derivation,
                              ProfileClient profile,
                              FmsReferenceGenerator references,
                              ArrivalDateQuoter arrivalDates,
                              Clock clock,
                              com.thinq.fms.messaging.MessageOutbox outbox) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.derivation = Objects.requireNonNull(derivation, "derivation");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.references = Objects.requireNonNull(references, "references");
        this.arrivalDates = Objects.requireNonNull(arrivalDates, "arrivalDates");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Create the account's single open withdrawal request.
     *
     * @throws WithdrawableUnavailableException when the derivation and RMS disagree, or a source
     *     could not be reached. No withdrawal may be requested against a figure this system cannot
     *     stand behind
     * @throws AmountExceedsWithdrawableException carrying the figure, so the client can explain
     *     the refusal without re-fetching
     * @throws DestinationNotVerifiedException when Profile does not currently hold the destination
     *     as verified for this trader
     * @throws com.thinq.fms.platform.error.RequestAlreadyOpenException from the unique index
     */
    public PayoutRequest request(AccountRef account, Money amount, String destinationRef) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(destinationRef, "destinationRef");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a withdrawal is for a positive amount; got " + amount);
        }

        // 1. What may leave, and whether this system can stand behind the figure.
        DerivationResult result = this.derivation.derive(
                account, BalanceDerivationService.DerivationContext.PAYOUT_REQUEST);

        Money withdrawable = result.withdrawable().orElseThrow(() ->
                new WithdrawableUnavailableException(result.verdict().name(),
                        "the withdrawable figure is " + result.verdict()
                                + "; no withdrawal may be requested against it"));

        if (amount.compareTo(withdrawable) > 0) {
            throw new AmountExceedsWithdrawableException(amount, withdrawable);
        }

        // 2. The destination, read live. Profile PR-28 forbids a cached list: an account whose
        //    verification was withdrawn must stop being a legal destination immediately.
        VerifiedBankAccount destination = this.profile.accountOf(account, destinationRef)
                .filter(VerifiedBankAccount::verified)
                .orElseThrow(() -> new DestinationNotVerifiedException(
                        "destination is not a verified account for this trader at this instant"));

        // 3. Insert. Rule W4 is the index's to enforce, not ours.
        Instant now = this.clock.instant();
        PayoutRequest request = new PayoutRequest(
                0L,
                account,
                amount,
                // Rule W12 pins the destination now. A later change to the trader's accounts never
                // redirects a request already in flight.
                destination.reference(),
                destination.masked(),
                this.references.next(),
                withdrawable,
                this.arrivalDates.quoteFor(now),
                now,
                PayoutState.ACCEPTED,
                0);

        return this.requests.save(request);
    }

    /**
     * Cancel a request that has not yet been instructed (REQ-305, REQ-619).
     *
     * <p>Cancellation stays available in {@code QUEUED_FOR_RUN}, which is easy to lose when that
     * state is added as an outage path: a trader whose payout was deferred by a rail outage has
     * <i>more</i> reason to want it stopped, not less.
     *
     * @throws RequestNotCancellableException stating why. The two cases mean opposite things to a
     *     trader — money already instructed is on its way, an already terminal request needs no
     *     action — so a bare refusal would leave them unsure whether to expect the money
     */
    public PayoutRequest cancel(AccountRef account, long requestId) {
        Objects.requireNonNull(account, "account");

        PayoutRequest request = this.requests.findFor(account, requestId)
                // Not found and not yours are the same answer, deliberately. Distinguishing them
                // would let a caller probe for other traders' request ids.
                .orElseThrow(() -> new RequestNotCancellableException("NOT_FOUND",
                        "no such request for this account"));

        if (request.state() == PayoutState.INSTRUCTED) {
            throw RequestNotCancellableException.alreadyInstructed(requestId);
        }
        if (request.state().isTerminal()) {
            throw RequestNotCancellableException.alreadyTerminal(requestId, request.state());
        }

        request.cancel(this.clock.instant());
        PayoutRequest cancelled = this.requests.save(request);

        // REQ-616: email only, and queued in the same unit of work as the cancellation (REQ-622).
        // No SMS and no WhatsApp — the trader did this themselves and is looking at the screen, so
        // anything louder than a receipt is noise.
        this.outbox.write(java.util.List.of(new com.thinq.fms.messaging.MessageIntent(
                0L, account, "WITHDRAWAL_CANCELLED",
                com.thinq.fms.integration.communication.MessageChannel.EMAIL,
                "WITHDRAWAL_CANCELLED", "PAYOUT-" + cancelled.id(), this.clock.instant())));

        return cancelled;
    }

    /** The account's open request, for display. */
    public Optional<PayoutRequest> openRequest(AccountRef account) {
        return this.requests.openFor(account);
    }

    /**
     * Supplies the FMS reference stamped on each request.
     *
     * <p>Rule C8: this value and the bank's own transfer reference never coincide, and V21's
     * {@code fms_payout_refs_differ} constraint refuses a row where they do.
     */
    public interface FmsReferenceGenerator {
        String next();
    }

    /**
     * Quotes the date the money should arrive (REQ-303).
     *
     * <p>Computed against the settlement calendar. Where the calendar is unavailable the quote
     * fails rather than defaulting (OA-5) — a guessed arrival date is a promise this system cannot
     * keep, and REQ-303 exists to compare the quote against the achieved date.
     */
    public interface ArrivalDateQuoter {
        LocalDate quoteFor(Instant requestedAt);
    }
}
