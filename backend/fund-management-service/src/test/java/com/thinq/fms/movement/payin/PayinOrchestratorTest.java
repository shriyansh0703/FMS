package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.NoRouteAvailableException;
import com.thinq.fms.platform.error.NoVerifiedSourceException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rules A5, A6, A7, A9b, A9d and A10.
 *
 * <p>The two that matter most are opposites of each other. <b>Rule A6</b> says a repeat
 * confirmation must change nothing — the gateway retries on anything that does not look like
 * success, so the second call has to be a no-op rather than a second credit. <b>Rule A7</b> says a
 * late confirmation must still land — money that reached the firm is never discarded because the
 * trader stopped watching. Getting either backwards is a money error.
 */
class PayinOrchestratorTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private StubRepo repo;
    private com.thinq.fms.messaging.RecordingOutbox outbox;
    private StubCaps caps;
    private boolean sourceVerified = true;

    @BeforeEach
    void setUp() {
        this.outbox = new com.thinq.fms.messaging.RecordingOutbox();
        this.repo = new StubRepo();
        this.caps = new StubCaps();
        this.sourceVerified = true;
    }

    @Test
    @DisplayName("Rule A5: an in-flight attempt affects no balance")
    void inFlightAffectsNoBalance() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);

        // Visible and attributable, and contributing to nothing. A payment in flight is not money
        // the account has.
        assertThat(started.attempt().state()).isEqualTo(PayinState.AT_GATEWAY);
        assertThat(started.attempt().affectsBalance()).isFalse();
        assertThat(this.caps.recorded).isEmpty();
    }

    @Test
    @DisplayName("Rule A6: a repeat confirmation changes nothing and is not an error")
    void repeatConfirmationIsANoOp() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        String ref = started.attempt().gatewayPaymentRef().orElseThrow();
        var orch = orchestrator();

        assertThat(orch.onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"))).isTrue();
        // The gateway retries on anything that does not look like success. Returning normally
        // having changed nothing is what stops the retry becoming a second credit.
        assertThat(orch.onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"))).isFalse();
        assertThat(orch.onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"))).isFalse();

        assertThat(this.caps.recorded)
                .as("headroom is consumed once, however many confirmations arrive")
                .hasSize(1);
    }

    @Test
    @DisplayName("Rule A7: a confirmation arriving after the trader gave up still lands")
    void lateConfirmationStillLands() {
        // The attempt sat unanswered and was recorded as awaiting. The money then arrived.
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        String ref = started.attempt().gatewayPaymentRef().orElseThrow();
        var orch = orchestrator();

        orch.onGatewayConfirmation(ref, PayinOutcome.AWAITING_BANK, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));
        assertThat(this.repo.stored.get(0).state()).isEqualTo(PayinState.AWAITING_BANK);

        assertThat(orch.onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"))).isTrue();
        assertThat(this.repo.stored.get(0).state()).isEqualTo(PayinState.CONFIRMED);
    }

    @Test
    @DisplayName("Rule A9b: an unknown outcome is awaiting, never failed, and offers no retry")
    void unknownOutcomeIsAwaitingNotFailed() {
        // The recovery is the opposite of a failure's: wait, and specifically do not retry.
        // Offering a retry here is how one payment becomes two.
        assertThat(PayinOutcome.UNKNOWN.isAwaitingResolution()).isTrue();
        assertThat(PayinOutcome.UNKNOWN.mayRetry()).isFalse();
        assertThat(PayinOutcome.AWAITING_BANK.mayRetry()).isFalse();
        assertThat(PayinState.forOutcome(PayinOutcome.UNKNOWN)).isEqualTo(PayinState.AWAITING_BANK);
    }

    @ParameterizedTest
    @EnumSource(value = PayinOutcome.class,
            names = {"BANK_DECLINED", "INSUFFICIENT_FUNDS_AT_BANK", "ABOVE_BANK_LIMIT", "SERVICE_UNREACHABLE"})
    @DisplayName("every genuine failure consumes no headroom")
    void failuresConsumeNoHeadroom(PayinOutcome outcome) {
        // Consuming headroom on failure would refuse a trader's retry against a limit they never
        // actually used.
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        orchestrator().onGatewayConfirmation(started.attempt().gatewayPaymentRef().orElseThrow(), outcome, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(this.caps.recorded).isEmpty();
        assertThat(this.repo.stored.get(0).state()).isEqualTo(PayinState.FAILED);
    }

    @Test
    @DisplayName("a confirmation for a reference this system never issued is refused")
    void unknownReferenceIsRefused() {
        // Not a repeat and not a late arrival: recording it would credit money against nothing.
        assertThatThrownBy(() -> orchestrator().onGatewayConfirmation("who-sent-this", PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly")))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_confirmation_unknown_reference"));
    }

    @Test
    @DisplayName("a failed attempt cannot later be reported as confirmed")
    void failedCannotBecomeConfirmed() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        String ref = started.attempt().gatewayPaymentRef().orElseThrow();
        orchestrator().onGatewayConfirmation(ref, PayinOutcome.BANK_DECLINED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        // Rule A7 covers a late arrival on an unresolved attempt, not a contradiction. Crediting
        // an attempt already reported as failed would create money from a reporting error.
        assertThatThrownBy(() -> orchestrator().onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly")))
                .isInstanceOf(FmsInvariantException.class);
    }

    @Test
    @DisplayName("REQ-203: a trader with no verified source cannot fund")
    void noVerifiedSourceIsRefused() {
        this.sourceVerified = false;

        assertThatThrownBy(() -> orchestrator().start(ACCOUNT, rupees(5_000), null))
                .isInstanceOf(NoVerifiedSourceException.class);
        assertThat(this.repo.stored).as("no attempt is created for a refusal").isEmpty();
    }

    @Test
    @DisplayName("REQ-701: no headroom means a refusal carrying the figures, and no attempt")
    void noHeadroomRefusesWithFigures() {
        this.caps.remaining.put(PaymentRoute.UPI, Money.ZERO);
        this.caps.remaining.put(PaymentRoute.NEFT, Money.ZERO);

        assertThatThrownBy(() -> orchestrator().start(ACCOUNT, rupees(5_000), null))
                .isInstanceOf(NoRouteAvailableException.class)
                .satisfies(e -> assertThat(((NoRouteAvailableException) e).detail().headroomByRoute())
                        .isNotEmpty());
        // Nothing was sent to a gateway, so a trader who lowers the amount is not penalised.
        assertThat(this.repo.stored).isEmpty();
    }

    @Test
    @DisplayName("Rule A10: a confirmed payin is reversed, never deleted")
    void reversalKeepsBothEntries() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        String ref = started.attempt().gatewayPaymentRef().orElseThrow();
        orchestrator().onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        var reversed = orchestrator().reverse(ACCOUNT, started.attempt().id());

        assertThat(reversed.state()).isEqualTo(PayinState.REVERSED);
        assertThat(this.repo.stored).as("the row is still there — Rule A10 forbids deletion").hasSize(1);
        // The account may fall into debit as a result. That is a debt for the health module, not
        // a reason to refuse the reversal and leave a wrong balance standing.
        assertThat(reversed.affectsBalance()).isFalse();
    }

    @Test
    @DisplayName("an unconfirmed payin cannot be reversed")
    void onlyConfirmedCanBeReversed() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);

        assertThatThrownBy(() -> orchestrator().reverse(ACCOUNT, started.attempt().id()))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_not_reversible"));
    }

    @Test
    @DisplayName("Rule A9d: an alternative is offered only when it can actually carry the amount")
    void alternativesMustBeAbleToWork() {
        this.caps.remaining.put(PaymentRoute.UPI, rupees(100));

        var alternatives = orchestrator().alternativesAfterFailure(ACCOUNT, rupees(5_000), PaymentRoute.NEFT);

        assertThat(alternatives).doesNotContain(PaymentRoute.NEFT).doesNotContain(PaymentRoute.UPI);
    }

    @Test
    @DisplayName("REQ-201: the last successful deposit, or empty on a first deposit")
    void lastSuccessfulDeposit() {
        assertThat(orchestrator().lastSuccessfulDeposit(ACCOUNT))
                .as("Rule A1's first-deposit case returns nothing rather than a number they never chose")
                .isEmpty();

        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        orchestrator().onGatewayConfirmation(started.attempt().gatewayPaymentRef().orElseThrow(),
                PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(orchestrator().lastSuccessfulDeposit(ACCOUNT)).contains(rupees(5_000));
    }

    // ---- harness ----

    private static Money rupees(long r) {
        return Money.ofPaise(r * 100L);
    }

    @Test
    @DisplayName("a gateway timeout still leaves the attempt addressable, and a late confirmation lands")
    void aGatewayTimeoutStillLeavesTheAttemptAddressable() {
        // The failure this guards is a silent one: createOrder throws, so the caller sees an
        // outage and assumes nothing happened — but a timeout abandons the wait, not the call, so
        // Juspay may already hold the order and settle it. Money reaches the firm and the system
        // has no row it can be attached to.
        //
        // Asserting only that start() throws would pass with the bug present. What has to be
        // asserted is that the confirmation arriving afterwards is accepted, because that is the
        // money.
        PayinOrchestrator orchestrator = orchestrator(new TimingOutGateway());

        assertThatThrownBy(() -> orchestrator.start(ACCOUNT, rupees(5_000), null))
                .isInstanceOf(VendorUnavailableException.class);

        assertThat(this.repo.stored).as("the attempt is recorded even though the call failed").hasSize(1);
        PayinAttempt orphaned = this.repo.stored.get(0);
        assertThat(orphaned.state()).isEqualTo(PayinState.INITIATED);
        assertThat(orphaned.gatewayPaymentRef())
                .as("the reference must be on the row before the gateway is called")
                .contains("FMS-PAYIN-" + orphaned.id());

        // Juspay did create the order. Its confirmation arrives now — Rule A7.
        orchestrator.onGatewayConfirmation("FMS-PAYIN-" + orphaned.id(), PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(orphaned.state())
                .as("money that reached the firm is not discarded because the call timed out")
                .isEqualTo(PayinState.CONFIRMED);
    }

    @Test
    @DisplayName("a gateway answering for a different reference is refused rather than stored")
    void aGatewayAnsweringForADifferentReferenceIsRefused() {
        // JuspayGateway.toOrder echoes the order id it was passed, so these agree today. The check
        // exists because that is invisible from here: if the response ever becomes the source, the
        // stored reference would stop matching the one confirmations arrive under, and every payin
        // would fail the way F-30 did — permanently rather than only on timeout.
        PayinOrchestrator orchestrator = orchestrator(new RelabellingGateway());

        assertThatThrownBy(() -> orchestrator.start(ACCOUNT, rupees(5_000), null))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_reference_mismatch"));
    }

    // ---- REQ-611, REQ-614, REQ-622: the messages an event queues ----

    @Test
    @DisplayName("starting a payin queues the 30-minute chase and nothing else")
    void startingAPayinQueuesTheChase() {
        orchestrator().start(ACCOUNT, rupees(5_000), null);

        assertThat(this.outbox.templateKeys()).containsExactly("PAYIN_PENDING_CHASE");
        assertThat(this.outbox.written().get(0).assertedState())
                .as("dropped if the payin resolves inside the window").isEqualTo("PAYIN_UNRESOLVED");
    }

    @Test
    @DisplayName("a confirmed payin queues its confirmation")
    void aConfirmedPayinQueuesItsConfirmation() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        orchestrator().onGatewayConfirmation(
                started.attempt().gatewayPaymentRef().orElseThrow(), PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(this.outbox.templateKeys()).contains("PAYIN_CONFIRMED");
    }

    @Test
    @DisplayName("a failed payin queues the message for its own outcome, not a generic one")
    void aFailedPayinQueuesItsOwnOutcome() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        orchestrator().onGatewayConfirmation(
                started.attempt().gatewayPaymentRef().orElseThrow(), PayinOutcome.BANK_DECLINED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(this.outbox.templateKeys()).contains("PAYIN_BANK_DECLINED");
        assertThat(this.outbox.templateKeys()).doesNotContain("PAYIN_CONFIRMED");
    }

    @Test
    @DisplayName("an unresolved outcome announces nothing — Rule A9b holds it open")
    void anUnresolvedOutcomeAnnouncesNothing() {
        // The chase covers this window. Announcing AWAITING_BANK as an outcome is what Rule A9b
        // forbids: it reads as a result when the payment is still moving.
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        orchestrator().onGatewayConfirmation(
                started.attempt().gatewayPaymentRef().orElseThrow(), PayinOutcome.AWAITING_BANK, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(this.outbox.templateKeys()).containsExactly("PAYIN_PENDING_CHASE");
    }

    @Test
    @DisplayName("a repeat confirmation queues nothing further — Rule A6")
    void aRepeatConfirmationQueuesNothingFurther() {
        var started = orchestrator().start(ACCOUNT, rupees(5_000), null);
        String ref = started.attempt().gatewayPaymentRef().orElseThrow();
        orchestrator().onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));
        orchestrator().onGatewayConfirmation(ref, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        assertThat(this.outbox.templateKeys())
                .as("one credit, one message")
                .containsExactly("PAYIN_PENDING_CHASE", "PAYIN_CONFIRMED");
    }

    private PayinOrchestrator orchestrator() {
        Map<PaymentRoute, RouteCap> config = new EnumMap<>(PaymentRoute.class);
        config.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(rupees(200_000)), Money.ZERO));
        config.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO));

        return new PayinOrchestrator(this.repo, new RouteSelector(this.caps, config), this.caps,
                new StubGateway(), profile(), CLOCK, this.outbox,
                new com.thinq.fms.messaging.MessageLadder(ZoneOffset.UTC),
                com.thinq.fms.messaging.MessagePreferences.noOptIn());
    }

    private PayinOrchestrator orchestrator(com.thinq.fms.integration.juspay.PayinGateway gateway) {
        Map<PaymentRoute, RouteCap> config = new EnumMap<>(PaymentRoute.class);
        config.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(rupees(200_000)), Money.ZERO));
        config.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO));

        return new PayinOrchestrator(this.repo, new RouteSelector(this.caps, config), this.caps,
                gateway, profile(), CLOCK, this.outbox,
                new com.thinq.fms.messaging.MessageLadder(ZoneOffset.UTC),
                com.thinq.fms.messaging.MessagePreferences.noOptIn());
    }

    /** Fails the way a real timeout does — after the vendor may already have acted. */
    private static final class TimingOutGateway
            implements com.thinq.fms.integration.juspay.PayinGateway {
        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder createOrder(
                String orderId, AccountRef account, Money amount, String returnUrl) {
            throw new VendorUnavailableException("juspay", "juspay did not answer create_order in time");
        }

        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder statusOf(String orderId, AccountRef account) {
            throw new UnsupportedOperationException();
        }
    }

    /** Answers under an order id other than the one it was given. */
    private static final class RelabellingGateway
            implements com.thinq.fms.integration.juspay.PayinGateway {
        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder createOrder(
                String orderId, AccountRef account, Money amount, String returnUrl) {
            return new com.thinq.fms.integration.juspay.JuspayOrder("juspay-assigned-9", "juspay-1",
                    null, amount, "NEW", PayinOutcome.AWAITING_BANK, "https://pay.example/1", false);
        }

        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder statusOf(String orderId, AccountRef account) {
            throw new UnsupportedOperationException();
        }
    }

    private ProfileClient profile() {
        VerifiedBankAccount account =
                new VerifiedBankAccount("acc-1", "••••4471", "HDFC", true, this.sourceVerified);
        return new ProfileClient() {
            @Override
            public List<VerifiedBankAccount> accountsOf(AccountRef a) {
                return List.of(account);
            }

            @Override
            public Optional<VerifiedBankAccount> accountOf(AccountRef a, String r) {
                return Optional.of(account);
            }

            @Override
            public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef a) {
                return Optional.of(account);
            }
        };
    }

    /**
     * Returns the order id it was given, which is what a real gateway echoes.
     *
     * <p>Two lines, because {@code PayinGateway} is the one capability the orchestrator needs.
     * Before that seam existed this had to subclass {@code JuspayGateway}, which is final for good
     * reason — it holds an HTTP client and a circuit breaker.
     */
    private static final class StubGateway implements com.thinq.fms.integration.juspay.PayinGateway {
        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder createOrder(
                String orderId, AccountRef account, Money amount, String returnUrl) {
            return new com.thinq.fms.integration.juspay.JuspayOrder(orderId, "juspay-1", null,
                    amount, "NEW", PayinOutcome.AWAITING_BANK, "https://pay.example/1", false);
        }

        @Override
        public com.thinq.fms.integration.juspay.JuspayOrder statusOf(String orderId, AccountRef account) {
            throw new UnsupportedOperationException("these tests apply outcomes directly");
        }
    }

    private static final class StubCaps implements RouteCapLedger {
        final Map<PaymentRoute, Money> remaining = new EnumMap<>(PaymentRoute.class);
        final List<Money> recorded = new ArrayList<>();

        @Override
        public Optional<Money> remainingToday(AccountRef a, PaymentRoute r) {
            if (r == PaymentRoute.NEFT && !this.remaining.containsKey(r)) {
                return Optional.empty();
            }
            return Optional.ofNullable(this.remaining.getOrDefault(r, rupees(200_000)));
        }

        @Override
        public void record(AccountRef a, PaymentRoute r, Money sent) {
            this.recorded.add(sent);
        }

        @Override
        public Optional<Money> remainingOn(AccountRef a, PaymentRoute r, LocalDate d) {
            return remainingToday(a, r);
        }
    }

    private static final class StubRepo implements PayinAttemptRepository {
        final List<PayinAttempt> stored = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public PayinAttempt save(PayinAttempt attempt) {
            if (attempt.id() != 0L) {
                return attempt;
            }
            PayinAttempt persisted = new PayinAttempt(this.nextId++, attempt.account(),
                    attempt.amount(), attempt.route(), attempt.startedAt(), attempt.state(), 0);
            this.stored.add(persisted);
            return persisted;
        }

        @Override
        public Optional<PayinAttempt> findFor(AccountRef account, long id) {
            return this.stored.stream()
                    .filter(a -> a.id() == id && a.account().equals(account)).findFirst();
        }

        @Override
        public Optional<PayinAttempt> findByGatewayRef(String ref) {
            return this.stored.stream()
                    .filter(a -> a.gatewayPaymentRef().map(ref::equals).orElse(false)).findFirst();
        }

        @Override
        public Optional<PayinAttempt> lastConfirmedFor(AccountRef account) {
            return this.stored.stream()
                    .filter(a -> a.account().equals(account) && a.state() == PayinState.CONFIRMED)
                    .reduce((first, second) -> second);
        }

        @Override
        public List<PayinAttempt> inPeriod(AccountRef account, LocalDate from, LocalDate to) {
            return this.stored.stream().filter(a -> a.account().equals(account)).toList();
        }
    }
}
