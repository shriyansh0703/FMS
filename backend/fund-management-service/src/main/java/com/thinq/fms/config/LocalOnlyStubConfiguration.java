package com.thinq.fms.config;

import com.thinq.fms.derivation.*;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.ledgerview.LedgerEntrySource;
import com.thinq.fms.movement.payout.PayoutOrchestrator;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Beans that FABRICATE FINANCIAL DATA so the service can start on a developer's machine.
 *
 * <p><b>Read this before using it.</b> Every figure these beans return is invented. They exist
 * because the service cannot start without a {@code ProfileClient}, a {@code BalanceDerivationService}
 * and the rest, and the real implementations are blocked on work outside this repository — TASK-11
 * on Noren's transport for margin, an unbuilt Profile integration for bank accounts, and EB-6 for the
 * settlement calendar. Without something in those slots there is no way to exercise the wiring at
 * all.
 *
 * <p><b>Arrival dates are no longer stubbed here.</b> The real
 * {@code ArrivalDateCalculator} is wired against {@code ConfiguredTradingCalendar}, which computes
 * from configured holidays and honestly reports the date as uncomputable when none are configured.
 * That is a better local experience than a fabricated date, because it exercises the real path.
 *
 * <p><b>What this is for:</b> checking that the module assembles, that migrations apply, that an
 * endpoint responds, that a message reaches the outbox. <b>What it is emphatically not for:</b> any
 * judgement about a number. A withdrawable figure from here is a constant someone typed.
 *
 * <p><b>Why it cannot reach production.</b> It is gated on the {@code local} profile, and
 * {@code LocalOnlyStubConfigurationTest} asserts that none of these beans exists without it. A
 * deployment that does not set the profile gets the real absence — the application fails to start,
 * loudly, naming the bean it lacks. That failure is the correct behaviour and must not be
 * "fixed" by activating this profile.
 */
@Configuration
@Profile("local")
public class LocalOnlyStubConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalOnlyStubConfiguration.class);

    /** One invented account, marked as such in the field a trader would see. */
    private static final VerifiedBankAccount FAKE_ACCOUNT =
            new VerifiedBankAccount("local-stub-acc", "••••0000", "STUB BANK — NOT REAL", true, true);

    public LocalOnlyStubConfiguration() {
        log.warn("=====================================================================");
        log.warn("  LOCAL STUB PROFILE ACTIVE — every financial figure is FABRICATED.");
        log.warn("  Bank accounts, margin figures and arrival dates are invented values.");
        log.warn("  If you are seeing this outside a developer machine, stop the service.");
        log.warn("=====================================================================");
    }

    /**
     * A single verified account, so the funding path is exercisable.
     *
     * <p>The bank name says STUB BANK rather than something plausible, on purpose: a screenshot of
     * this reaching anyone should be self-evidently fake rather than quietly wrong.
     */
    @Bean
    public ProfileClient localStubProfileClient() {
        return new ProfileClient() {
            @Override
            public List<VerifiedBankAccount> accountsOf(AccountRef account) {
                return List.of(FAKE_ACCOUNT);
            }

            @Override
            public Optional<VerifiedBankAccount> accountOf(AccountRef account, String reference) {
                return Optional.of(FAKE_ACCOUNT);
            }

            @Override
            public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef account) {
                return Optional.of(FAKE_ACCOUNT);
            }
        };
    }

    /**
     * A fixed withdrawable figure.
     *
     * <p>Returns RECONCILED with a derivation built from constants. It is deliberately a round
     * ₹10,000 with every other term zero — a figure nobody could mistake for a computed one.
     */
    @Bean
    public BalanceDerivationService localStubDerivation(Clock fmsClock) {
        WithdrawableCalculator calculator = new WithdrawableCalculator();
        return (account, context) -> {
            Derivation derivation = calculator.compute(new WithdrawableInputs(
                    Money.ofPaise(1_000_000L), Money.ZERO, Money.ZERO,
                    Money.ZERO, Money.ZERO, Money.ZERO));
            return new DerivationResult(WithdrawableVerdict.RECONCILED, derivation,
                    derivation.withdrawable(), Instant.now(fmsClock),
                    MarginSourceKind.FRONT_OFFICE);
        };
    }

    /**
     * A payout rail that never settles anything.
     *
     * <p><b>Deliberately the least capable stub here.</b> Every other one invents a value; this one
     * refuses to move money at all, because a fake rail reporting PAID would write a settled
     * withdrawal into the ledger and tell a trader their money had been sent. It throws the
     * vendor-unavailable exception the real rail throws when TechExcel cannot be reached, which
     * routes to REQ-619's "banking rail unavailable" outcome: the request stays open, stays
     * cancellable, and nothing is recorded as sent.
     *
     * <p>That is a real path through the code, so the wiring is genuinely exercised — and the one
     * outcome that cannot mislead anyone about where money is.
     */
    @Bean
    public com.thinq.fms.movement.payout.PayoutRail localStubPayoutRail() {
        return new com.thinq.fms.movement.payout.PayoutRail() {
            @Override
            public com.thinq.fms.movement.payout.InstructionResult instruct(
                    com.thinq.fms.movement.payout.PaymentInstruction instruction) {
                throw new com.thinq.fms.platform.error.VendorUnavailableException("techexcel-stub",
                        "the local stub rail never settles; no money moves on a developer machine");
            }

            @Override
            public Optional<com.thinq.fms.movement.payout.InstructionResult> statusOf(
                    com.thinq.fms.movement.payout.InstructionKey key, LocalDate runDate) {
                return Optional.empty();
            }
        };
    }

    /**
     * An empty ledger.
     *
     * <p>Empty rather than invented rows: a transaction list with fabricated movements in it is far
     * more misleading than one that is plainly empty, and the empty case is a real state the code
     * has to handle anyway (Rule L7).
     */
    @Bean
    public LedgerEntrySource localStubLedger() {
        return (account, from, to) -> List.of();
    }
}
