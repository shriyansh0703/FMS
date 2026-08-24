package com.thinq.fms.persistence;

import com.thinq.fms.integration.juspay.JuspayOrder;
import com.thinq.fms.integration.juspay.PayinGateway;
import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.movement.payin.*;
import com.thinq.fms.platform.error.VendorUnavailableException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-30 and F-35, against a real database.
 *
 * <p>F-30 was that a gateway call which timed out left the attempt with no gateway reference, so
 * the confirmation arriving afterwards was refused and money that reached the firm was discarded —
 * Rule A7. The fix commits the reference before the vendor call.
 *
 * <p>F-35 was the observation that the fix depends on that first write being <b>durable</b> before
 * the call, and that nothing proved it: against a stub repository whose {@code save} is a list
 * write, the ordering cannot fail however the surrounding code is arranged. These tests close that.
 * They read the row back after {@code start} has thrown, so if {@code start} is ever wrapped in a
 * single transaction spanning the vendor call, the rollback removes the row and this fails — which
 * is the whole point, because that change looks like an improvement and would silently restore
 * F-30 in a worse form.
 */
class PayinDurabilityTest extends PostgresTestSupport {

    private static final AtomicLong ACCOUNTS = new AtomicLong(5000);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneOffset.UTC);

    private JdbcPayinAttemptRepository repository;
    private AccountRef account;

    @BeforeEach
    void setUp() {
        this.repository = new JdbcPayinAttemptRepository(db, ZoneOffset.UTC);
        this.account = AccountRef.of("UCC" + ACCOUNTS.getAndIncrement());
    }

    @Test
    @DisplayName("a gateway timeout leaves a committed, addressable row")
    void aGatewayTimeoutLeavesACommittedAddressableRow() {
        PayinOrchestrator orchestrator = orchestrator(timingOut());

        assertThatThrownBy(() -> orchestrator.start(this.account, Money.ofPaise(500_000L), null))
                .isInstanceOf(VendorUnavailableException.class);

        // Read straight from the database rather than through the orchestrator. This is what a
        // separate process handling the callback would see.
        Long rows = db.sql("SELECT count(*) FROM fms_payin_attempt WHERE account_id = ?")
                .params(this.account.ucc()).query(Long.class).single();
        assertThat(rows).as("the attempt survived the failed call").isEqualTo(1L);

        String reference = db.sql(
                        "SELECT gateway_payment_ref FROM fms_payin_attempt WHERE account_id = ?")
                .params(this.account.ucc()).query(String.class).single();
        assertThat(reference)
                .as("committed before the gateway was called, so a confirmation can find it")
                .isNotNull()
                .startsWith("FMS-PAYIN-");
    }

    @Test
    @DisplayName("the confirmation that follows a timeout still lands — Rule A7 end to end")
    void theConfirmationThatFollowsATimeoutStillLands() {
        PayinOrchestrator orchestrator = orchestrator(timingOut());

        assertThatThrownBy(() -> orchestrator.start(this.account, Money.ofPaise(500_000L), null))
                .isInstanceOf(VendorUnavailableException.class);

        String reference = db.sql(
                        "SELECT gateway_payment_ref FROM fms_payin_attempt WHERE account_id = ?")
                .params(this.account.ucc()).query(String.class).single();

        // Juspay did create the order, and now confirms it.
        orchestrator.onGatewayConfirmation(reference, PayinOutcome.CONFIRMED, com.thinq.fms.integration.juspay.VerifiedGatewayCallback.notFromAGatewayCallback("test drives the outcome directly"));

        String state = db.sql("SELECT state FROM fms_payin_attempt WHERE account_id = ?")
                .params(this.account.ucc()).query(String.class).single();
        assertThat(state)
                .as("money that reached the firm is not discarded because the call timed out")
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a normal start commits the reference and reaches the gateway")
    void aNormalStartCommitsTheReferenceAndReachesTheGateway() {
        PayinOrchestrator orchestrator = orchestrator(echoing());

        PayinOrchestrator.StartedPayin started = orchestrator.start(this.account, Money.ofPaise(500_000L), null);

        PayinAttempt stored = this.repository
                .findFor(this.account, started.attempt().id()).orElseThrow();
        assertThat(stored.state()).isEqualTo(PayinState.AT_GATEWAY);
        assertThat(stored.gatewayPaymentRef()).contains("FMS-PAYIN-" + stored.id());
    }

    // ---- collaborators ----

    private PayinGateway timingOut() {
        return new PayinGateway() {
            @Override
            public JuspayOrder createOrder(String orderId, AccountRef a, Money m, String u) {
                throw new VendorUnavailableException("juspay",
                        "juspay did not answer create_order in time");
            }

            @Override
            public JuspayOrder statusOf(String orderId, AccountRef a) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private PayinGateway echoing() {
        return new PayinGateway() {
            @Override
            public JuspayOrder createOrder(String orderId, AccountRef a, Money m, String u) {
                return new JuspayOrder(orderId, "juspay-1", null, m, "NEW",
                        PayinOutcome.AWAITING_BANK, "https://pay.example/1", false);
            }

            @Override
            public JuspayOrder statusOf(String orderId, AccountRef a) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private PayinOrchestrator orchestrator(PayinGateway gateway) {
        Map<PaymentRoute, RouteCap> config = new EnumMap<>(PaymentRoute.class);
        config.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(Money.ofPaise(20_000_000L)), Money.ZERO));

        RouteCapLedger caps = new RouteCapLedger() {
            @Override
            public Optional<Money> remainingToday(AccountRef a, PaymentRoute r) {
                return Optional.of(Money.ofPaise(20_000_000L));
            }

            @Override
            public void record(AccountRef a, PaymentRoute r, Money s) {
            }

            @Override
            public Optional<Money> remainingOn(AccountRef a, PaymentRoute r, LocalDate d) {
                return remainingToday(a, r);
            }
        };

        VerifiedBankAccount bank =
                new VerifiedBankAccount("acc-1", "••••4471", "HDFC", true, true);
        ProfileClient profile = new ProfileClient() {
            @Override
            public List<VerifiedBankAccount> accountsOf(AccountRef a) {
                return List.of(bank);
            }

            @Override
            public Optional<VerifiedBankAccount> accountOf(AccountRef a, String r) {
                return Optional.of(bank);
            }

            @Override
            public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef a) {
                return Optional.of(bank);
            }
        };

        return new PayinOrchestrator(this.repository, new RouteSelector(caps, config), caps,
                gateway, profile, CLOCK, new com.thinq.fms.messaging.RecordingOutbox(),
                new com.thinq.fms.messaging.MessageLadder(ZoneOffset.UTC),
                com.thinq.fms.messaging.MessagePreferences.noOptIn());
    }
}
