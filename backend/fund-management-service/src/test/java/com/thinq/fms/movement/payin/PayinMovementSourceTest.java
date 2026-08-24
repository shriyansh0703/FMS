package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.ledgerview.TransactionEntry;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which payin attempts belong in the movements view, and — the part that matters — which do not.
 *
 * <p>This source exists because the ledger cannot show a failed or in-flight deposit. The risk it
 * introduces is the mirror image: a <b>confirmed</b> payin is in the ledger, so returning it from
 * here too would show the trader one deposit twice. That is a worse defect than the one this class
 * was built to fix, and it is silent — both rows are individually correct.
 */
class PayinMovementSourceTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    private final List<PayinAttempt> attempts = new ArrayList<>();
    private PayinMovementSource source;

    @BeforeEach
    void setUp() {
        this.attempts.clear();
        this.source = new PayinMovementSource(new StubRepo(this.attempts), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("a confirmed payin is NOT returned — the ledger already has it")
    void confirmedAttemptsAreExcluded() {
        this.attempts.add(attempt(1L, PayinState.CONFIRMED));
        this.attempts.add(attempt(2L, PayinState.AT_GATEWAY));

        assertThat(read()).extracting(TransactionEntry::voucherNo)
                .as("a confirmed deposit appearing here as well would be shown twice")
                .containsExactly("PAYIN-2");
    }

    @Test
    @DisplayName("a reversed payin is NOT returned — both its entries are in the ledger")
    void reversedAttemptsAreExcluded() {
        // Rule A10 keeps the original and adds a compensating entry, so the ledger carries two
        // rows for the event. Adding the attempt would make three rows for two things.
        this.attempts.add(attempt(1L, PayinState.REVERSED));

        assertThat(read()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PayinState.class,
            names = {"INITIATED", "AT_GATEWAY", "AWAITING_BANK", "FAILED", "CANCELLED"})
    @DisplayName("everything the ledger cannot carry is returned")
    void unpostedAttemptsAreReturned(PayinState state) {
        // Rule L8: failed and cancelled movements stay in the history — they are the entries a
        // trader most often needs to discuss.
        this.attempts.add(attempt(1L, state));

        assertThat(read()).hasSize(1);
        assertThat(read().get(0).statusIfAny()).contains(state.name());
    }

    @Test
    @DisplayName("the exclusion follows affectsBalance, not a hand-written state list")
    void exclusionFollowsAffectsBalance() {
        // A future state that credits an account would be excluded automatically. A list of state
        // names would have to be remembered, and the failure of forgetting is a double-shown
        // deposit rather than a compile error.
        for (PayinState state : PayinState.values()) {
            this.attempts.clear();
            this.attempts.add(attempt(1L, state));

            boolean returned = !read().isEmpty();
            boolean expected = !state.affectsBalance() && state != PayinState.REVERSED;
            assertThat(returned).as("%s", state).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("an unposted movement carries no running balance and is user-caused")
    void unpostedMovementsAreShapedCorrectly() {
        this.attempts.add(attempt(7L, PayinState.AWAITING_BANK));

        TransactionEntry e = read().get(0);

        // It moved no money, so it changed no balance. Reporting the ledger's balance here would
        // attach a figure this row played no part in.
        assertThat(e.runningBalance()).isEqualTo(Money.ZERO);
        assertThat(e.credit()).isTrue();
        // Rule L4: the trader started this, whatever became of it.
        assertThat(e.userCaused()).isTrue();
        assertThat(e.isInFlight()).isTrue();
        assertThat(e.description().secondaryDetail()).isEqualTo("PAYIN-7");
    }

    @Test
    @DisplayName("the copy key names the specific outcome, not a generic failure")
    void copyKeyNamesTheOutcome() {
        // Rule A9a's six outcomes are not interchangeable and Rule A9c requires whose problem it
        // is to be named. A single FAILED key would collapse them.
        PayinAttempt declined = attempt(1L, PayinState.AT_GATEWAY);
        declined.recordOutcome(PayinOutcome.BANK_DECLINED, NOW);
        this.attempts.add(declined);

        assertThat(read().get(0).description().copyKey()).isEqualTo("PAYIN_BANK_DECLINED");
    }

    // ---- harness ----

    private List<TransactionEntry> read() {
        return this.source.read(ACCOUNT, TODAY.minusDays(30), TODAY);
    }

    private static PayinAttempt attempt(long id, PayinState state) {
        return new PayinAttempt(id, ACCOUNT, Money.ofPaise(250_000L), PaymentRoute.UPI,
                NOW, state, 0);
    }

    private record StubRepo(List<PayinAttempt> all) implements PayinAttemptRepository {
        @Override
        public PayinAttempt save(PayinAttempt a) {
            return a;
        }

        @Override
        public Optional<PayinAttempt> findFor(AccountRef account, long id) {
            return Optional.empty();
        }

        @Override
        public Optional<PayinAttempt> findByGatewayRef(String ref) {
            return Optional.empty();
        }

        @Override
        public Optional<PayinAttempt> lastConfirmedFor(AccountRef account) {
            return Optional.empty();
        }

        @Override
        public List<PayinAttempt> inPeriod(AccountRef account, LocalDate from, LocalDate to) {
            return List.copyOf(this.all);
        }
    }
}
