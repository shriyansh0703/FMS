package com.thinq.fms.persistence;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.ledgerview.*;
import com.thinq.fms.movement.payin.*;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property F-31 existed for: a deposit appears exactly once, whichever side of the confirmation
 * boundary it is on.
 *
 * <p>Both halves were already covered in isolation — {@code PayinMovementSourceTest} for the filter,
 * {@code TransactionQueryServiceTest} against a hand-built stub movement — and neither could catch a
 * mistake in the join between them. Two tests that each pass while the wiring is wrong is precisely
 * the shape of coverage that reads as complete and is not, so this drives real attempts through a
 * real repository and reads them back through the real query service.
 */
class PayinLedgerNoDoubleCountTest extends PostgresTestSupport {

    private static final AtomicLong SEQ = new AtomicLong(20_000);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);
    private static final Instant NOW = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private JdbcPayinAttemptRepository repository;
    private AccountRef account;
    private List<LedgerEntry> ledgerRows;
    private TransactionQueryService transactions;

    @BeforeEach
    void setUp() {
        this.repository = new JdbcPayinAttemptRepository(db, ZoneOffset.UTC);
        this.account = AccountRef.of("UCC" + SEQ.getAndIncrement());
        this.ledgerRows = new ArrayList<>();

        // The real source, over the real repository — not a stand-in movement.
        InFlightMovementSource inFlight = new PayinMovementSource(this.repository, ZoneOffset.UTC);
        LedgerEntrySource ledger = (a, from, to) -> List.copyOf(this.ledgerRows);

        this.transactions = new TransactionQueryService(ledger, inFlight,
                ConfiguredEntryDescriptionMapper.withDefaults(), StatementCopy.withDefaults(), CLOCK);
    }

    @Test
    @DisplayName("a payin still at the gateway appears exactly once, from the attempt source")
    void aPayinAtTheGatewayAppearsExactlyOnce() {
        startedAttempt();

        List<TransactionEntry> entries = movements();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).kind()).isEqualTo(EntryKind.PAYIN);
    }

    @Test
    @DisplayName("a confirmed payin the ledger has posted appears exactly once, from the ledger")
    void aConfirmedPayinAppearsExactlyOnce() {
        // The case that would double-count. The attempt is CONFIRMED and the ledger carries its
        // entry; if the source did not exclude confirmed attempts, the trader would see the same
        // deposit twice and the running balance would be wrong by the amount of it.
        PayinAttempt attempt = startedAttempt();
        confirm(attempt);
        this.ledgerRows.add(depositEntry("V-1", 500_000L));

        List<TransactionEntry> entries = movements();

        assertThat(entries).as("one deposit, one row").hasSize(1);
        assertThat(entries.get(0).voucherNo()).isEqualTo("V-1");
    }

    @Test
    @DisplayName("a failed payin stays visible and is not counted as money — Rule L8")
    void aFailedPayinStaysVisible() {
        PayinAttempt attempt = startedAttempt();
        attempt.recordOutcome(PayinOutcome.BANK_DECLINED, NOW.plusSeconds(30));
        this.repository.save(attempt);

        List<TransactionEntry> entries = movements();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).kind()).isEqualTo(EntryKind.PAYIN);
        assertThat(this.ledgerRows).as("nothing posted to the ledger for a failed deposit").isEmpty();
    }

    @Test
    @DisplayName("a reversed payin shows as one reversed movement, not two rows and not none")
    void aReversedPayinShowsAsOneReversedMovement() {
        // Rule A10: a reversal is never a deletion, and Rule L5a keeps the "where is my money" view
        // to movements the trader made. The compensating entry is a REVERSAL, which that view
        // excludes — so what must survive is the PAIRING: the original deposit has to come back
        // marked as reversed. Without it the trader sees money arriving that is no longer there.
        PayinAttempt attempt = startedAttempt();
        confirm(attempt);
        attempt.reverse(NOW.plusSeconds(120));
        this.repository.save(attempt);

        this.ledgerRows.add(depositEntry("V-1", 500_000L));
        this.ledgerRows.add(withdrawalEntry("V-2", 500_000L));

        List<TransactionEntry> entries = movements();

        assertThat(entries).as("the attempt contributes nothing once it is REVERSED").hasSize(1);
        TransactionEntry deposit = entries.get(0);
        assertThat(deposit.voucherNo()).isEqualTo("V-1");
        assertThat(deposit.reversedBy())
                .as("the deposit must be shown as reversed, not as money still held")
                .isEqualTo("V-2");

        // The compensating entry itself is still there in the full account explanation.
        assertThat(this.transactions.list(this.account, TransactionView.ALL_ENTRIES,
                        new TransactionPeriod(TODAY.minusDays(7), TODAY)).entries())
                .extracting(TransactionEntry::voucherNo)
                .containsExactlyInAnyOrder("V-1", "V-2");
    }

    @Test
    @DisplayName("many attempts in many states each appear exactly once")
    void manyAttemptsEachAppearExactlyOnce() {
        // One of each, together, because the filter is a predicate over states and a mistake in it
        // shows up as a count that is right for one state and wrong for another.
        startedAttempt();

        PayinAttempt confirmed = startedAttempt();
        confirm(confirmed);
        this.ledgerRows.add(depositEntry("V-1", 500_000L));

        PayinAttempt failed = startedAttempt();
        failed.recordOutcome(PayinOutcome.BANK_DECLINED, NOW.plusSeconds(30));
        this.repository.save(failed);

        PayinAttempt awaiting = startedAttempt();
        awaiting.recordOutcome(PayinOutcome.AWAITING_BANK, NOW.plusSeconds(30));
        this.repository.save(awaiting);

        // in flight, failed, awaiting, and the ledger's posted deposit
        assertThat(movements()).hasSize(4);
    }

    // ---- fixtures ----

    private List<TransactionEntry> movements() {
        return this.transactions.list(this.account, TransactionView.MOVEMENTS,
                new TransactionPeriod(TODAY.minusDays(7), TODAY)).entries();
    }

    private PayinAttempt startedAttempt() {
        PayinAttempt attempt = this.repository.save(new PayinAttempt(0L, this.account,
                Money.ofPaise(500_000L), PaymentRoute.UPI, NOW, PayinState.INITIATED, 0));
        String reference = "FMS-PAYIN-" + attempt.id();
        attempt.willUseGatewayReference(reference);
        attempt.sentToGateway(reference);
        return this.repository.save(attempt);
    }

    private void confirm(PayinAttempt attempt) {
        attempt.recordOutcome(PayinOutcome.CONFIRMED, NOW.plusSeconds(30));
        this.repository.save(attempt);
    }

    private LedgerEntry depositEntry(String voucher, long paise) {
        return new LedgerEntry(voucher, "NSE_CASH", TODAY.minusDays(1), Money.ZERO,
                Money.ofPaise(paise), Money.ofPaise(paise), "Funds received", "R", null, null,
                null, false, null, null);
    }

    private LedgerEntry withdrawalEntry(String voucher, long paise) {
        return new LedgerEntry(voucher, "NSE_CASH", TODAY.minusDays(1), Money.ofPaise(paise),
                Money.ZERO, Money.ZERO, "Reversal of V-1 funds received", "P", null, null,
                null, false, null, null);
    }
}
