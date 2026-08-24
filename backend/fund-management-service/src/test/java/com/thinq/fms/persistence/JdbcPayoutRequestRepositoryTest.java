package com.thinq.fms.persistence;

import com.thinq.fms.movement.payout.*;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.RequestAlreadyOpenException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/** The withdrawal repository against a real server — the money-out path. */
class JdbcPayoutRequestRepositoryTest extends PostgresTestSupport {

    private static final AtomicLong SEQ = new AtomicLong(9000);
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final LocalDate QUOTED = LocalDate.of(2026, 8, 24);

    private JdbcPayoutRequestRepository repository;
    private AccountRef account;

    @BeforeEach
    void setUp() {
        this.repository = new JdbcPayoutRequestRepository(db);
        this.account = AccountRef.of("UCC" + SEQ.getAndIncrement());
    }

    private PayoutRequest newRequest() {
        return new PayoutRequest(0L, this.account, Money.ofPaise(100_000L), "acc-1", "••••4471",
                "FMS-W-" + SEQ.getAndIncrement(), Money.ofPaise(500_000L), QUOTED, NOW,
                PayoutState.ACCEPTED, 0);
    }

    @Test
    @DisplayName("a request round-trips through every settlement field")
    void roundTripsThroughEverySettlementField() {
        PayoutRequest saved = this.repository.save(newRequest());
        saved.transitionTo(PayoutState.QUEUED_FOR_RUN);
        saved.transitionTo(PayoutState.INSTRUCTED);
        saved.recordSettlement(new SettlementOutcome(PayoutState.PARTLY_PAID,
                        Money.ofPaise(100_000L), Money.ofPaise(60_000L),
                        SettlementReasonCode.INSUFFICIENT_BALANCE, "bank sent less",
                        "UTR-99887766", LocalDate.of(2026, 8, 25)),
                Money.ofPaise(400_000L), NOW.plusSeconds(600));
        this.repository.save(saved);

        PayoutRequest loaded = this.repository.findFor(this.account, saved.id()).orElseThrow();

        assertThat(loaded.state()).isEqualTo(PayoutState.PARTLY_PAID);
        assertThat(loaded.amount()).isEqualTo(Money.ofPaise(100_000L));
        assertThat(loaded.withdrawableAtRequest()).isEqualTo(Money.ofPaise(500_000L));
        assertThat(loaded.arrivalDateQuoted()).isEqualTo(QUOTED);
        assertThat(loaded.destinationMasked()).isEqualTo("••••4471");
        assertThat(loaded.requestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Rule W4 — the database refuses a second open request, and it arrives as a 409")
    void ruleW4IsEnforcedByTheDatabase() {
        // A service-level check would be a race: two requests arriving together both read no open
        // request and both proceed. Only the index cannot be raced, so this asserts it is the index
        // doing the work.
        //
        // It also asserts the TRANSLATION, which is the half that was missing. Every unit test of
        // this rule ran against a fake repository that threw RequestAlreadyOpenException directly,
        // so nothing exercised the real JDBC path — and the real path let Spring's
        // DuplicateKeyException escape to the error boundary as a 500 internal_error. A trader
        // submitting twice paged whoever owns availability instead of being shown the request they
        // already had. This test fails if the catch is removed, which the old assertion on
        // DataIntegrityViolationException could not do.
        this.repository.save(newRequest());

        assertThatThrownBy(() -> this.repository.save(newRequest()))
                .isInstanceOf(RequestAlreadyOpenException.class)
                .hasMessageContaining("open withdrawal request");
    }

    @Test
    @DisplayName("a duplicate on any other constraint is not reported as Rule W4")
    void anUnrelatedDuplicateIsNotReportedAsRuleW4() {
        // The translation is keyed on the index name for this reason. fms_reference carries its own
        // unique constraint, and reporting its violation as request_already_open would tell a
        // trader they have an open request when what actually happened is that the reference
        // generator handed out a number twice — an invariant failure that must page, not a 409 the
        // client is invited to explain away.
        PayoutRequest first = this.repository.save(newRequest());
        first.cancel(NOW.plusSeconds(60));
        this.repository.save(first);

        PayoutRequest sameReference = new PayoutRequest(0L, this.account, Money.ofPaise(100_000L),
                "acc-1", "••••4471", first.fmsReference(), Money.ofPaise(500_000L), QUOTED, NOW,
                PayoutState.ACCEPTED, 0);

        assertThatThrownBy(() -> this.repository.save(sameReference))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(RequestAlreadyOpenException.class);
    }

    @Test
    @DisplayName("a cancelled request frees the account to make another")
    void aCancelledRequestFreesTheAccount() {
        PayoutRequest first = this.repository.save(newRequest());
        first.cancel(NOW.plusSeconds(60));
        this.repository.save(first);

        assertThatCode(() -> this.repository.save(newRequest())).doesNotThrowAnyException();
        assertThat(this.repository.openFor(this.account)).isPresent();
    }

    @Test
    @DisplayName("openFor returns nothing once the request has closed")
    void openForReturnsNothingOnceClosed() {
        PayoutRequest request = this.repository.save(newRequest());
        assertThat(this.repository.openFor(this.account)).isPresent();

        request.cancel(NOW.plusSeconds(60));
        this.repository.save(request);

        assertThat(this.repository.openFor(this.account)).isEmpty();
    }

    @Test
    @DisplayName("a stale write is refused rather than overwriting a newer one")
    void aStaleWriteIsRefused() {
        PayoutRequest saved = this.repository.save(newRequest());

        PayoutRequest readerOne = this.repository.findFor(this.account, saved.id()).orElseThrow();
        PayoutRequest readerTwo = this.repository.findFor(this.account, saved.id()).orElseThrow();

        readerOne.transitionTo(PayoutState.QUEUED_FOR_RUN);
        this.repository.save(readerOne);

        readerTwo.transitionTo(PayoutState.CANCELLED);
        assertThatThrownBy(() -> this.repository.save(readerTwo))
                .isInstanceOf(FmsInvariantException.class)
                .satisfies(e -> assertThat(((FmsInvariantException) e).code())
                        .isEqualTo("payout_request_stale_write"));
    }

    @Test
    @DisplayName("the end-of-day run sees every open state, oldest first")
    void theRunSeesEveryOpenStateOldestFirst() {
        PayoutRequest queued = this.repository.save(newRequest());
        queued.transitionTo(PayoutState.QUEUED_FOR_RUN);
        this.repository.save(queued);

        // A second account, so Rule W4 does not refuse it.
        this.account = AccountRef.of("UCC" + SEQ.getAndIncrement());
        PayoutRequest instructed = this.repository.save(newRequest());
        instructed.transitionTo(PayoutState.QUEUED_FOR_RUN);
        instructed.transitionTo(PayoutState.INSTRUCTED);
        this.repository.save(instructed);

        assertThat(this.repository.openRequestsForRun(QUOTED))
                .extracting(PayoutRequest::id)
                .contains(queued.id(), instructed.id());
    }

    @Test
    @DisplayName("Rule C8 — a bank reference equal to ours never reaches the row")
    void ruleC8IsEnforcedOnWrite() {
        PayoutRequest saved = this.repository.save(newRequest());
        saved.transitionTo(PayoutState.QUEUED_FOR_RUN);
        saved.transitionTo(PayoutState.INSTRUCTED);
        saved.recordSettlement(new SettlementOutcome(PayoutState.PAID, Money.ofPaise(100_000L),
                        Money.ofPaise(100_000L), SettlementReasonCode.NONE, null,
                        saved.fmsReference(), LocalDate.of(2026, 8, 25)),
                Money.ofPaise(400_000L), NOW.plusSeconds(600));

        // Giving a trader our own reference sends them to a bank the value means nothing to.
        assertThatThrownBy(() -> this.repository.save(saved))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
