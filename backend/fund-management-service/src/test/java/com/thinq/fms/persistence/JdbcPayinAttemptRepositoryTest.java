package com.thinq.fms.persistence;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.movement.payin.*;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/** The repository against a real server, including the paths a stub cannot reach. */
class JdbcPayinAttemptRepositoryTest extends PostgresTestSupport {

    private static final AtomicLong ACCOUNTS = new AtomicLong(1000);
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");

    private JdbcPayinAttemptRepository repository;
    private AccountRef account;

    @BeforeEach
    void setUp() {
        this.repository = new JdbcPayinAttemptRepository(db, ZoneOffset.UTC);
        this.account = AccountRef.of("UCC" + ACCOUNTS.getAndIncrement());
    }

    private PayinAttempt newAttempt() {
        return new PayinAttempt(0L, this.account, Money.ofPaise(500_000L),
                PaymentRoute.UPI, NOW, PayinState.INITIATED, 0);
    }

    @Test
    @DisplayName("an attempt round-trips through every mutable field")
    void roundTripsThroughEveryMutableField() {
        PayinAttempt saved = this.repository.save(newAttempt());
        saved.willUseGatewayReference("FMS-PAYIN-" + saved.id());
        saved.sentToGateway("FMS-PAYIN-" + saved.id());
        saved.recordOutcome(PayinOutcome.CONFIRMED, NOW.plusSeconds(30));
        saved.recordSourceMasked("4471");
        this.repository.save(saved);

        PayinAttempt loaded = this.repository.findFor(this.account, saved.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.account()).isEqualTo(this.account);
        assertThat(loaded.amount()).isEqualTo(Money.ofPaise(500_000L));
        assertThat(loaded.route()).isEqualTo(PaymentRoute.UPI);
        assertThat(loaded.state()).isEqualTo(PayinState.CONFIRMED);
        assertThat(loaded.gatewayPaymentRef()).contains("FMS-PAYIN-" + saved.id());
        assertThat(loaded.outcome()).contains(PayinOutcome.CONFIRMED);
        assertThat(loaded.sourceMasked()).contains("4471");
        assertThat(loaded.resolvedAt()).contains(NOW.plusSeconds(30));
        assertThat(loaded.startedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a loaded attempt does not have to walk the state machine to come back")
    void aLoadedAttemptDoesNotReplayItsTransitions() {
        // Rehydration is not a transition. A CONFIRMED row must load as CONFIRMED without
        // INITIATED being asserted first, or every restart would throw on its own data.
        PayinAttempt saved = this.repository.save(newAttempt());
        saved.willUseGatewayReference("FMS-PAYIN-" + saved.id());
        saved.sentToGateway("FMS-PAYIN-" + saved.id());
        saved.recordOutcome(PayinOutcome.CONFIRMED, NOW);
        this.repository.save(saved);

        PayinAttempt loaded = this.repository.findFor(this.account, saved.id()).orElseThrow();

        assertThatCode(() -> loaded.reverse(NOW.plusSeconds(60))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a stale write is refused rather than silently overwriting a newer one")
    void aStaleWriteIsRefused() {
        // The lost update this column exists to prevent: two readers, both mutate, the second
        // write would otherwise erase the first. On this table the erased write could be a
        // confirmation, so it throws.
        PayinAttempt saved = this.repository.save(newAttempt());
        saved.willUseGatewayReference("FMS-PAYIN-" + saved.id());
        this.repository.save(saved);

        PayinAttempt readerOne = this.repository.findFor(this.account, saved.id()).orElseThrow();
        PayinAttempt readerTwo = this.repository.findFor(this.account, saved.id()).orElseThrow();

        readerOne.sentToGateway("FMS-PAYIN-" + saved.id());
        this.repository.save(readerOne);

        readerTwo.sentToGateway("FMS-PAYIN-" + saved.id());
        assertThatThrownBy(() -> this.repository.save(readerTwo))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payin_attempt_stale_write"));
    }

    @Test
    @DisplayName("a successful write re-anchors the version, so the same instance can write again")
    void aSuccessfulWriteReAnchorsTheVersion() {
        PayinAttempt saved = this.repository.save(newAttempt());
        saved.willUseGatewayReference("FMS-PAYIN-" + saved.id());
        this.repository.save(saved);
        saved.sentToGateway("FMS-PAYIN-" + saved.id());

        assertThatCode(() -> this.repository.save(saved)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rule A6 reaches the repository — a duplicate reference is refused by the database")
    void ruleA6IsEnforcedThroughTheRepository() {
        PayinAttempt first = this.repository.save(newAttempt());
        String shared = "FMS-PAYIN-SHARED-" + first.id();
        first.willUseGatewayReference(shared);
        this.repository.save(first);

        PayinAttempt second = this.repository.save(newAttempt());
        second.willUseGatewayReference(shared);

        assertThatThrownBy(() -> this.repository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findFor is scoped by account, so another account's attempt is not returned")
    void findForIsScopedByAccount() {
        PayinAttempt mine = this.repository.save(newAttempt());

        assertThat(this.repository.findFor(AccountRef.of("UCCOTHER"), mine.id())).isEmpty();
        assertThat(this.repository.findFor(this.account, mine.id())).isPresent();
    }

    @Test
    @DisplayName("lastConfirmedFor ignores attempts that are not confirmed")
    void lastConfirmedForIgnoresUnconfirmedAttempts() {
        this.repository.save(newAttempt());
        assertThat(this.repository.lastConfirmedFor(this.account)).isEmpty();

        PayinAttempt confirmed = this.repository.save(newAttempt());
        confirmed.willUseGatewayReference("FMS-PAYIN-" + confirmed.id());
        confirmed.sentToGateway("FMS-PAYIN-" + confirmed.id());
        confirmed.recordOutcome(PayinOutcome.CONFIRMED, NOW);
        this.repository.save(confirmed);

        assertThat(this.repository.lastConfirmedFor(this.account))
                .get().extracting(PayinAttempt::id).isEqualTo(confirmed.id());
    }

    @Test
    @DisplayName("inPeriod includes both end dates")
    void inPeriodIncludesBothEndDates() {
        // A half-open window is how the last day's deposits disappear from a statement.
        this.repository.save(newAttempt());

        LocalDate day = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
        assertThat(this.repository.inPeriod(this.account, day, day)).hasSize(1);
        assertThat(this.repository.inPeriod(this.account, day.plusDays(1), day.plusDays(2))).isEmpty();
    }
}
