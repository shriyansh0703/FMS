package com.thinq.fms.api;

import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.derivation.Derivation;
import com.thinq.fms.derivation.DerivationResult;
import com.thinq.fms.derivation.MarginSourceKind;
import com.thinq.fms.derivation.WithdrawableCalculator;
import com.thinq.fms.derivation.WithdrawableInputs;
import com.thinq.fms.derivation.WithdrawableVerdict;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.movement.payin.RouteCap;
import com.thinq.fms.movement.payin.RouteCapLedger;
import com.thinq.fms.movement.payout.InstructionKey;
import com.thinq.fms.movement.payout.InstructionResult;
import com.thinq.fms.movement.payout.PaymentInstruction;
import com.thinq.fms.movement.payout.PayoutOrchestrator;
import com.thinq.fms.movement.payout.PayoutRail;
import com.thinq.fms.movement.payout.PayoutRequest;
import com.thinq.fms.movement.payout.PayoutRequestRepository;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.platform.error.RequestAlreadyOpenException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory collaborators for the API tests.
 *
 * <p>The domain layer is already covered by its own unit tests; what these exercise is the edge —
 * status codes, the error body, the account coming from the principal rather than the request, and
 * the generated specification. Wiring real vendor gateways in would test the network instead.
 *
 * <p><b>`MarginSource` has no implementation</b> (TASK-11 is halted on two missing vendor
 * contracts), so `BalanceDerivationService` is stubbed here. That the controllers can be built and
 * tested regardless is the point of depending on the interface.
 */
@TestConfiguration
public class ApiTestConfiguration {

    public static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    public static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");

    /** Mutable so a test can decide what the account may withdraw. */
    public static volatile Money withdrawable = Money.ofPaise(1_000_000L);
    public static volatile WithdrawableVerdict verdict = WithdrawableVerdict.RECONCILED;
    public static volatile boolean destinationVerified = true;

    public static void reset() {
        withdrawable = Money.ofPaise(1_000_000L);
        verdict = WithdrawableVerdict.RECONCILED;
        destinationVerified = true;
    }

    /**
     * Exactly one payout rail, because {@code PayoutRailConfiguration} refuses to start the
     * context without one — and refusing is correct. A service that booted with no rail would
     * accept withdrawal requests it could never settle, and the trader would find out at end of
     * day. The assertion caught this test context on its first run.
     *
     * <p>It instructs nothing: these tests exercise the request and cancel endpoints, neither of
     * which reaches the rail. Anything calling it fails loudly rather than silently succeeding.
     */
    /**
     * A budget large enough not to interfere.
     *
     * <p>These tests exercise validation, status codes and error bodies; the rate limit is a
     * separate concern with its own test.
     *
     * <p>{@code @Primary} rather than relying on {@code @ConditionalOnMissingBean} on the shipped
     * bean: that condition is evaluated in bean-definition order on a scanned {@code @Configuration}
     * and does not see this one, which is the same ordering trap the Stage 11 review recorded
     * against {@code FundsModuleConfiguration}. {@code @Primary} does not depend on ordering. Sharing the shipped budget across a class of POSTs against
     * one account made them fail on the eleventh request, which measured the limiter rather than the
     * behaviour under test.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public PerAccountRateLimit permissiveRateLimit() {
        var generous = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(100_000)
                .limitRefreshPeriod(java.time.Duration.ofMinutes(1))
                .timeoutDuration(java.time.Duration.ZERO)
                .build();
        return new PerAccountRateLimit(generous, generous, generous);
    }

    @Bean
    public PayoutRail payoutRail() {
        return new PayoutRail() {
            @Override
            public InstructionResult instruct(PaymentInstruction instruction) {
                throw new UnsupportedOperationException(
                        "the API tests must not instruct a payout; that is the end-of-day run's path");
            }

            @Override
            public Optional<InstructionResult> statusOf(InstructionKey key, LocalDate runDate) {
                return Optional.empty();
            }
        };
    }

    @Bean
    public InMemoryPayoutRepository payoutRepository() {
        return new InMemoryPayoutRepository();
    }

    @Bean
    public BalanceDerivationService derivationService() {
        return (account, context) -> {
            if (verdict != WithdrawableVerdict.RECONCILED) {
                return new DerivationResult(verdict, null, null, NOW, MarginSourceKind.FRONT_OFFICE);
            }
            Derivation d = new WithdrawableCalculator().compute(new WithdrawableInputs(
                    withdrawable, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO));
            return new DerivationResult(WithdrawableVerdict.RECONCILED, d, withdrawable, NOW,
                    MarginSourceKind.FRONT_OFFICE);
        };
    }

    @Bean
    public ProfileClient profileClient() {
        return new ProfileClient() {
            private VerifiedBankAccount account() {
                return new VerifiedBankAccount("acc-4471", "••••4471", "HDFC", true, destinationVerified);
            }

            @Override
            public List<VerifiedBankAccount> accountsOf(AccountRef a) {
                return List.of(account());
            }

            @Override
            public Optional<VerifiedBankAccount> accountOf(AccountRef a, String reference) {
                return "acc-4471".equals(reference) ? Optional.of(account()) : Optional.empty();
            }

            @Override
            public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef a) {
                return Optional.of(account());
            }
        };
    }

    @Bean
    public PayoutOrchestrator payoutOrchestrator(InMemoryPayoutRepository repository,
                                                 BalanceDerivationService derivation,
                                                 ProfileClient profile) {
        return new PayoutOrchestrator(repository, derivation, profile,
                () -> "FMS-TEST-" + System.nanoTime(),
                at -> LocalDate.of(2026, 8, 24),
                Clock.fixed(NOW, ZoneOffset.UTC), new com.thinq.fms.messaging.RecordingOutbox());
    }

    /**
     * Ledger entries the transaction tests read. Mutable so a test can decide what the back office
     * returns, including nothing at all — Rule L7's empty period is a case worth exercising.
     */
    public static final List<com.thinq.fms.ledgerview.LedgerEntry> LEDGER = new ArrayList<>();

    /** Movements the ledger does not carry: in flight, failed, cancelled. */
    public static final List<com.thinq.fms.ledgerview.TransactionEntry> IN_FLIGHT = new ArrayList<>();

    @Bean
    public com.thinq.fms.ledgerview.EntryDescriptionMapper entryDescriptions() {
        return com.thinq.fms.ledgerview.ConfiguredEntryDescriptionMapper.withDefaults();
    }

    @Bean
    public com.thinq.fms.ledgerview.StatementCopy statementCopy() {
        return com.thinq.fms.ledgerview.StatementCopy.withDefaults();
    }

    @Bean
    public com.thinq.fms.ledgerview.StatementCsvWriter statementCsvWriter() {
        return new com.thinq.fms.ledgerview.StatementCsvWriter();
    }

    /**
     * The query service over a stubbed ledger.
     *
     * <p>Subclassed rather than mocked so the real reversal pairing, view filtering, ordering and
     * Rule L7 handling all run — those are the logic under test. Only the vendor call is replaced.
     */
    /**
     * The production query service over a stubbed source. Only the vendor read is replaced, so
     * describing, reversal pairing, view filtering and Rule L7's empty handling are the real ones.
     */
    @Bean
    public com.thinq.fms.ledgerview.TransactionQueryService transactionQueryService(
            com.thinq.fms.ledgerview.EntryDescriptionMapper descriptions) {
        return new com.thinq.fms.ledgerview.TransactionQueryService(
                (account, from, to) -> List.copyOf(LEDGER),
                (account, from, to) -> List.copyOf(IN_FLIGHT),
                descriptions, com.thinq.fms.ledgerview.StatementCopy.withDefaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Bean
    public Map<PaymentRoute, RouteCap> routeConfiguration() {
        Map<PaymentRoute, RouteCap> m = new EnumMap<>(PaymentRoute.class);
        m.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(Money.ofPaise(20_000_000L)), Money.ZERO));
        m.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO));
        return m;
    }

    @Bean
    public RouteCapLedger routeCapLedger(Map<PaymentRoute, RouteCap> configuration) {
        return new RouteCapLedger() {
            @Override
            public Optional<Money> remainingToday(AccountRef account, PaymentRoute route) {
                RouteCap cap = configuration.get(route);
                return cap == null ? Optional.empty() : cap.remainingAfter(Money.ofPaise(5_000_000L));
            }

            @Override
            public void record(AccountRef account, PaymentRoute route, Money sent) {
            }

            @Override
            public Optional<Money> remainingOn(AccountRef a, PaymentRoute r, LocalDate d) {
                return remainingToday(a, r);
            }
        };
    }

    /** Enough of a repository to exercise the edge, including the Rule W4 constraint violation. */
    public static class InMemoryPayoutRepository implements PayoutRequestRepository {
        private final List<PayoutRequest> stored = new ArrayList<>();
        private long nextId = 1L;

        public void clear() {
            this.stored.clear();
        }

        @Override
        public Optional<PayoutRequest> openFor(AccountRef account) {
            return this.stored.stream()
                    .filter(r -> r.account().equals(account) && r.isOpen()).findFirst();
        }

        @Override
        public PayoutRequest save(PayoutRequest request) {
            if (request.id() != 0L) {
                return request;   // update in place; the entity is mutable by design
            }
            // Stands in for the partial unique index. The real guarantee is V21's, and the
            // orchestrator does not pre-check — so this must refuse, or the edge test would pass
            // against a repository more permissive than the database.
            if (openFor(request.account()).isPresent()) {
                throw new RequestAlreadyOpenException("fms_payout_one_open_per_account");
            }
            PayoutRequest persisted = new PayoutRequest(this.nextId++, request.account(),
                    request.amount(), request.destinationRef(), request.destinationMasked(),
                    request.fmsReference(), request.withdrawableAtRequest(),
                    request.arrivalDateQuoted(), request.requestedAt(), PayoutState.ACCEPTED, 0);
            this.stored.add(persisted);
            return persisted;
        }

        @Override
        public Optional<PayoutRequest> findFor(AccountRef account, long id) {
            return this.stored.stream()
                    .filter(r -> r.id() == id && r.account().equals(account)).findFirst();
        }

        @Override
        public List<PayoutRequest> openRequestsForRun(LocalDate runDate) {
            return this.stored.stream().filter(PayoutRequest::isOpen).toList();
        }
    }
}
