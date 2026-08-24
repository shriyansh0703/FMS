package com.thinq.fms.persistence;

import com.thinq.fms.movement.payin.*;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The daily route caps, including the concurrency the interface requires of {@code record}. */
class JdbcRouteCapLedgerTest extends PostgresTestSupport {

    private static final AtomicLong SEQ = new AtomicLong(30_000);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private JdbcRouteCapLedger ledger;
    private AccountRef account;

    @BeforeEach
    void setUp() {
        Map<PaymentRoute, RouteCap> caps = new EnumMap<>(PaymentRoute.class);
        caps.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(Money.ofPaise(10_000_000L)), Money.ZERO));
        caps.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO));

        this.ledger = new JdbcRouteCapLedger(db, caps, CLOCK, ZoneOffset.UTC);
        this.account = AccountRef.of("UCC" + SEQ.getAndIncrement());
    }

    @Test
    @DisplayName("an unused route has its whole cap available")
    void anUnusedRouteHasItsWholeCap() {
        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.UPI))
                .contains(Money.ofPaise(10_000_000L));
    }

    @Test
    @DisplayName("an uncapped route reports empty, which is unbounded and not zero")
    void anUncappedRouteReportsEmpty() {
        // A caller reading empty as zero would refuse every NEFT payment, which is why the
        // interface says so twice and why this is asserted rather than assumed.
        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.NEFT)).isEmpty();

        this.ledger.record(this.account, PaymentRoute.NEFT, Money.ofPaise(50_000_000L));
        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.NEFT))
                .as("recording usage does not give an uncapped route a cap").isEmpty();
    }

    @Test
    @DisplayName("a route with no configuration at all reports unbounded rather than throwing")
    void anUnconfiguredRouteReportsUnbounded() {
        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.NET_BANKING)).isEmpty();
    }

    @Test
    @DisplayName("recorded usage reduces the headroom by exactly what was sent")
    void recordedUsageReducesHeadroom() {
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(3_000_000L));

        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.UPI))
                .contains(Money.ofPaise(7_000_000L));
    }

    @Test
    @DisplayName("repeated records accumulate rather than overwrite")
    void repeatedRecordsAccumulate() {
        // An upsert that replaced instead of adding would silently reset the day's usage on every
        // payment, so the cap would never be reached however much was sent.
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(3_000_000L));
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(2_000_000L));

        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.UPI))
                .contains(Money.ofPaise(5_000_000L));
    }

    @Test
    @DisplayName("usage on one day does not consume another day's headroom")
    void usageIsPerDay() {
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(4_000_000L));

        assertThat(this.ledger.remainingOn(this.account, PaymentRoute.UPI, TODAY))
                .contains(Money.ofPaise(6_000_000L));
        assertThat(this.ledger.remainingOn(this.account, PaymentRoute.UPI, TODAY.plusDays(1)))
                .as("tomorrow starts fresh").contains(Money.ofPaise(10_000_000L));
    }

    @Test
    @DisplayName("one account's usage does not consume another's headroom")
    void usageIsPerAccount() {
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(4_000_000L));

        assertThat(this.ledger.remainingToday(AccountRef.of("UCC" + SEQ.getAndIncrement()),
                PaymentRoute.UPI)).contains(Money.ofPaise(10_000_000L));
    }

    @Test
    @DisplayName("one route's usage does not consume another route's headroom")
    void usageIsPerRoute() {
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(4_000_000L));

        assertThat(this.ledger.remainingOn(this.account, PaymentRoute.NET_BANKING, TODAY))
                .as("NET_BANKING is unconfigured here, so unbounded — not reduced by UPI").isEmpty();
    }

    @Test
    @DisplayName("headroom floors at zero when usage already exceeds a lowered cap")
    void headroomFloorsAtZero() {
        // A cap lowered after money went out under the old one leaves usage legitimately above it.
        // Negative headroom would render to a trader as a negative limit.
        this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(12_000_000L));

        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.UPI))
                .contains(Money.ZERO);
    }

    @Test
    @DisplayName("negative usage is refused rather than crediting headroom back")
    void negativeUsageIsRefused() {
        assertThatThrownBy(() ->
                this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("concurrent records for the same account, route and day all land")
    void concurrentRecordsAllLand() throws Exception {
        // The reason record() is one statement. A read-modify-write would let simultaneous
        // payments each read the old total and both pass a cap only one fits under — and the
        // trader keeps the difference. Twenty threads, each recording 100,000 paise: if any
        // update is lost the total is short and the assertion fails.
        int threads = 20;
        long each = 100_000L;
        var pool = Executors.newFixedThreadPool(threads);
        var startLine = new CountDownLatch(1);

        try {
            var running = new ArrayList<Future<?>>();
            for (int i = 0; i < threads; i++) {
                running.add(pool.submit(() -> {
                    startLine.await();
                    this.ledger.record(this.account, PaymentRoute.UPI, Money.ofPaise(each));
                    return null;
                }));
            }
            startLine.countDown();
            for (Future<?> f : running) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(this.ledger.remainingToday(this.account, PaymentRoute.UPI))
                .as("every concurrent record must be reflected; a lost one leaves headroom too high")
                .contains(Money.ofPaise(10_000_000L - threads * each));
    }
}
