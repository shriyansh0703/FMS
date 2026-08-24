package com.thinq.fms.movement.payout;

import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.derivation.Derivation;
import com.thinq.fms.derivation.DerivationResult;
import com.thinq.fms.derivation.MarginSourceKind;
import com.thinq.fms.derivation.WithdrawableVerdict;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.platform.error.AmountExceedsWithdrawableException;
import com.thinq.fms.platform.error.DestinationNotVerifiedException;
import com.thinq.fms.platform.error.RequestAlreadyOpenException;
import com.thinq.fms.platform.error.RequestNotCancellableException;
import com.thinq.fms.platform.error.WithdrawableUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The money-out path: Rules W3, W4, W11 and W12, plus REQ-305's cancellation rules.
 *
 * <p>The orchestrator depends only on abstractions, which is what lets this test run with stubs
 * while the concrete {@code MarginSource} is still blocked on missing vendor contracts. That is
 * the DIP point earning its keep rather than being decorative.
 */
class PayoutOrchestratorTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");

    /** Fresh per test: JUnit builds a new instance for each method. */
    private final com.thinq.fms.messaging.RecordingOutbox outbox =
            new com.thinq.fms.messaging.RecordingOutbox();
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final VerifiedBankAccount VERIFIED =
            new VerifiedBankAccount("acc-1", "••••4471", "HDFC", true, true);

    @Test
    @DisplayName("a request within the withdrawable figure is accepted and stamps Rule W11's figure")
    void requestWithinWithdrawableIsAccepted() {
        StubRepo repo = new StubRepo();
        PayoutRequest r = orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo)
                .request(ACCOUNT, rupees(4_000), "acc-1");

        assertThat(r.state()).isEqualTo(PayoutState.ACCEPTED);
        assertThat(r.amount()).isEqualTo(rupees(4_000));
        // Rule W11: what was true at the decision, so "why did I receive less?" has an answer
        // months later rather than requiring a reconstruction nobody can do.
        assertThat(r.withdrawableAtRequest()).isEqualTo(rupees(10_000));
        // Rule W12: the destination is pinned now, in its masked form.
        assertThat(r.destinationRef()).isEqualTo("acc-1");
        assertThat(r.destinationMasked()).isEqualTo("••••4471");
    }

    @Test
    @DisplayName("Rule W4 is left to the index; the orchestrator never pre-checks by reading")
    void ruleW4IsTheIndexsToEnforce() {
        // A read-then-write would be a race dressed as validation: two requests arriving together
        // would both find nothing open and both proceed. The test asserts the orchestrator does
        // not consult openFor at all on the create path.
        StubRepo repo = new StubRepo();
        orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo)
                .request(ACCOUNT, rupees(1_000), "acc-1");

        assertThat(repo.openForCalls)
                .as("consulting openFor before inserting would be a pre-check, and a race")
                .isZero();
    }

    @Test
    @DisplayName("the unique-index violation surfaces as request_already_open")
    void constraintViolationSurfacesAsAlreadyOpen() {
        StubRepo repo = new StubRepo();
        repo.failNextSaveWithAlreadyOpen = true;

        assertThatThrownBy(() -> orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo)
                .request(ACCOUNT, rupees(1_000), "acc-1"))
                .isInstanceOf(RequestAlreadyOpenException.class);
    }

    @Test
    @DisplayName("more than the withdrawable figure is refused, and the refusal carries the figure")
    void amountAboveWithdrawableIsRefusedWithTheFigure() {
        assertThatThrownBy(() -> orchestrator(reconciled(rupees(3_000)), profileWith(VERIFIED), new StubRepo())
                .request(ACCOUNT, rupees(4_000), "acc-1"))
                .isInstanceOf(AmountExceedsWithdrawableException.class)
                .satisfies(e -> {
                    var ex = (AmountExceedsWithdrawableException) e;
                    // REQ-102: the client explains the refusal without a second round trip.
                    assertThat(ex.withdrawable()).isEqualTo(rupees(3_000));
                    assertThat(ex.requested()).isEqualTo(rupees(4_000));
                });
    }

    @Test
    @DisplayName("a DIVERGENT verdict blocks the request rather than picking a winner")
    void divergentVerdictBlocksTheRequest() {
        // The HLD is explicit that neither RMS nor the derivation is silently picked. Picking RMS
        // would show a figure Rule B4 cannot explain; picking the derivation would let a trader
        // request money RMS will refuse at settlement.
        DerivationResult divergent = new DerivationResult(
                WithdrawableVerdict.DIVERGENT, null, rupees(9_000), NOW, MarginSourceKind.FRONT_OFFICE);

        assertThatThrownBy(() -> orchestrator(divergent, profileWith(VERIFIED), new StubRepo())
                .request(ACCOUNT, rupees(1_000), "acc-1"))
                .isInstanceOf(WithdrawableUnavailableException.class)
                .satisfies(e -> assertThat(((WithdrawableUnavailableException) e).verdict())
                        .isEqualTo("DIVERGENT"));
    }

    @Test
    @DisplayName("an unverified destination is refused even when the amount is fine")
    void unverifiedDestinationIsRefused() {
        VerifiedBankAccount unverified =
                new VerifiedBankAccount("acc-2", "••••9910", "ICICI", false, false);

        assertThatThrownBy(() -> orchestrator(reconciled(rupees(10_000)), profileWith(unverified), new StubRepo())
                .request(ACCOUNT, rupees(1_000), "acc-2"))
                .isInstanceOf(DestinationNotVerifiedException.class);
    }

    @Test
    @DisplayName("the destination is read live rather than from a cached list")
    void destinationIsReadAtTheMomentOfRequest() {
        // Profile PR-28. An account whose verification was withdrawn must stop being a legal
        // destination immediately, not at the end of the trader's session.
        CountingProfile profile = profileWith(VERIFIED);
        orchestrator(reconciled(rupees(10_000)), profile, new StubRepo())
                .request(ACCOUNT, rupees(1_000), "acc-1");

        assertThat(profile.lookups).isEqualTo(1);
    }

    @Test
    @DisplayName("nothing withdrawable is reported before an unverified bank account")
    void theMoreFundamentalRefusalWins() {
        // Both checks would fail here. A trader with nothing withdrawable should be told that,
        // not sent to fix a bank account that would not have helped.
        VerifiedBankAccount unverified =
                new VerifiedBankAccount("acc-2", "••••9910", "ICICI", false, false);

        assertThatThrownBy(() -> orchestrator(reconciled(Money.ZERO), profileWith(unverified), new StubRepo())
                .request(ACCOUNT, rupees(1_000), "acc-2"))
                .isInstanceOf(AmountExceedsWithdrawableException.class);
    }

    @Test
    @DisplayName("cancellation is allowed while accepted or queued, and refused once instructed")
    void cancellationFollowsTheStateMachine() {
        // REQ-619 keeps cancellation available in QUEUED_FOR_RUN: a trader whose payout was
        // deferred by a rail outage has more reason to want it stopped, not less.
        assertThat(cancelFrom(PayoutState.ACCEPTED).state()).isEqualTo(PayoutState.CANCELLED);
        assertThat(cancelFrom(PayoutState.QUEUED_FOR_RUN).state()).isEqualTo(PayoutState.CANCELLED);

        assertThatThrownBy(() -> cancelFrom(PayoutState.INSTRUCTED))
                .isInstanceOf(RequestNotCancellableException.class)
                .satisfies(e -> assertThat(((RequestNotCancellableException) e).reasonCode())
                        .isEqualTo("ALREADY_INSTRUCTED"));

        assertThatThrownBy(() -> cancelFrom(PayoutState.PAID))
                .isInstanceOf(RequestNotCancellableException.class)
                .satisfies(e -> assertThat(((RequestNotCancellableException) e).reasonCode())
                        .isEqualTo("ALREADY_TERMINAL"));
    }

    @Test
    @DisplayName("another trader's request is not found rather than forbidden")
    void anotherTradersRequestIsNotFound() {
        // §4.3: confirming existence would itself leak, so not-yours and not-there answer alike.
        StubRepo repo = new StubRepo();
        repo.stored = seed(PayoutState.ACCEPTED);

        assertThatThrownBy(() -> orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo)
                .cancel(AccountRef.of("SOMEONEELSE"), 1L))
                .isInstanceOf(RequestNotCancellableException.class)
                .satisfies(e -> assertThat(((RequestNotCancellableException) e).reasonCode())
                        .isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("an illegal transition pages rather than being reported to the trader")
    void illegalTransitionIsAnInvariantFailure() {
        // A forbidden transition means this system reached a state its own rules say is
        // impossible. Continuing would move money on a false premise, so it stops.
        PayoutRequest paid = seed(PayoutState.PAID);
        assertThatThrownBy(() -> paid.transitionTo(PayoutState.INSTRUCTED))
                .isInstanceOf(com.thinq.fms.platform.error.FmsInvariantException.class)
                .satisfies(e -> assertThat(
                        ((com.thinq.fms.platform.error.FmsInvariantException) e).pagesOnCall()).isTrue());
    }

    // ---- harness ----

    private PayoutRequest cancelFrom(PayoutState state) {
        StubRepo repo = new StubRepo();
        repo.stored = seed(state);
        return orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo).cancel(ACCOUNT, 1L);
    }

    private static PayoutRequest seed(PayoutState state) {
        return new PayoutRequest(1L, ACCOUNT, rupees(1_000), "acc-1", "••••4471",
                "FMS-1", rupees(10_000), LocalDate.of(2026, 8, 22), NOW, state, 0);
    }

    private static Money rupees(long r) {
        return Money.ofPaise(r * 100L);
    }

    private static DerivationResult reconciled(Money withdrawable) {
        Derivation d = new com.thinq.fms.derivation.WithdrawableCalculator().compute(
                new com.thinq.fms.derivation.WithdrawableInputs(
                        withdrawable, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO));
        return new DerivationResult(WithdrawableVerdict.RECONCILED, d, withdrawable, NOW,
                MarginSourceKind.FRONT_OFFICE);
    }

    private PayoutOrchestrator orchestrator(DerivationResult result, ProfileClient profile, StubRepo repo) {
        return new PayoutOrchestrator(repo, (a, c) -> result, profile,
                () -> "FMS-TEST-1", at -> LocalDate.of(2026, 8, 22), CLOCK, this.outbox);
    }

    @Test
    @DisplayName("cancelling queues the email confirmation and nothing louder")
    void cancellingQueuesTheEmailConfirmation() {
        // REQ-616: email only. An SMS for an action the trader just performed on screen is noise,
        // and Rule C2 reserves SMS for states that need to reach everyone.
        StubRepo repo = new StubRepo();
        var orchestrator = orchestrator(reconciled(rupees(10_000)), profileWith(VERIFIED), repo);
        var request = orchestrator.request(ACCOUNT, rupees(4_000), "acc-1");
        orchestrator.cancel(ACCOUNT, request.id());

        assertThat(this.outbox.templateKeys()).containsExactly("WITHDRAWAL_CANCELLED");
        assertThat(this.outbox.written().get(0).channel())
                .isEqualTo(com.thinq.fms.integration.communication.MessageChannel.EMAIL);
    }

    private static CountingProfile profileWith(VerifiedBankAccount account) {
        return new CountingProfile(account);
    }

    private static final class CountingProfile implements ProfileClient {
        private final VerifiedBankAccount account;
        int lookups;

        CountingProfile(VerifiedBankAccount account) {
            this.account = account;
        }

        @Override
        public List<VerifiedBankAccount> accountsOf(AccountRef a) {
            return List.of(this.account);
        }

        @Override
        public Optional<VerifiedBankAccount> accountOf(AccountRef a, String reference) {
            this.lookups++;
            return this.account.reference().equals(reference) ? Optional.of(this.account) : Optional.empty();
        }

        @Override
        public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef a) {
            return Optional.of(this.account);
        }
    }

    private static final class StubRepo implements PayoutRequestRepository {
        PayoutRequest stored;
        int openForCalls;
        boolean failNextSaveWithAlreadyOpen;

        @Override
        public Optional<PayoutRequest> openFor(AccountRef account) {
            this.openForCalls++;
            return Optional.ofNullable(this.stored).filter(PayoutRequest::isOpen);
        }

        @Override
        public PayoutRequest save(PayoutRequest request) {
            if (this.failNextSaveWithAlreadyOpen) {
                throw new RequestAlreadyOpenException("fms_payout_one_open_per_account");
            }
            this.stored = request;
            return request;
        }

        @Override
        public Optional<PayoutRequest> findFor(AccountRef account, long id) {
            return Optional.ofNullable(this.stored)
                    .filter(r -> r.id() == id && r.account().equals(account));
        }

        @Override
        public List<PayoutRequest> openRequestsForRun(LocalDate runDate) {
            return new ArrayList<>();
        }
    }
}
