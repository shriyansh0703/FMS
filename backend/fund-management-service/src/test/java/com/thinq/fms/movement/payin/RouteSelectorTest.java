package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule A12 and REQ-701/702.
 *
 * <p>The rule the PRD is most explicit about is the one easiest to get wrong: the cap is daily and
 * measured against what is already spent, <b>not</b> per transaction. Enforcing it per transaction
 * passes the same amount twice and defers the refusal to the trader's own bank, which is the
 * failure REQ-701 exists to prevent.
 */
class RouteSelectorTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");

    /** The configured values from the PRD's payment-routes table. */
    private static final Map<PaymentRoute, RouteCap> CONFIG = config();

    @Test
    @DisplayName("the cap is measured against what is already spent today, not per transaction")
    void capIsDailyAndNotPerTransaction() {
        // ₹1,50,000 already sent on UPI today, whose cap is ₹2,00,000. A second ₹1,50,000 fits
        // the cap taken per transaction and does NOT fit the remaining headroom. This is the
        // exact case the PRD calls out.
        StubLedger ledger = new StubLedger();
        ledger.set(PaymentRoute.UPI, rupees(150_000));

        RouteSelector.Selection s = new RouteSelector(ledger, CONFIG).select(ACCOUNT, rupees(150_000));

        assertThat(s.isSelected()).isTrue();
        assertThat(s.selected().route())
                .as("UPI has only ₹50,000 left, so this must not go out on UPI")
                .isNotEqualTo(PaymentRoute.UPI);
    }

    @Test
    @DisplayName("an amount within headroom takes the most preferred route")
    void withinHeadroomTakesTheFirstChoice() {
        RouteSelector.Selection s = new RouteSelector(new StubLedger(), CONFIG)
                .select(ACCOUNT, rupees(5_000));

        assertThat(s.selected().route()).isEqualTo(PaymentRoute.UPI);
        assertThat(s.selected().wasSwitched())
                .as("nothing was switched, so nothing should be disclosed as switched")
                .isFalse();
    }

    @Test
    @DisplayName("Rule A12's automatic re-route reports the route it moved from")
    void automaticRerouteDisclosesTheSwitch() {
        // Above UPI's ₹2,00,000 ceiling, within net banking's ₹10,00,000.
        RouteSelector.Selection s = new RouteSelector(new StubLedger(), CONFIG)
                .select(ACCOUNT, rupees(500_000));

        assertThat(s.selected().route()).isEqualTo(PaymentRoute.NET_BANKING);
        // Rule A12: the route changes automatically and SAYS SO. A silent switch would leave the
        // trader expecting a UPI payment and seeing a net-banking one.
        assertThat(s.selected().wasSwitched()).isTrue();
        assertThat(s.selected().switchedFrom()).isEqualTo(PaymentRoute.UPI);
    }

    @Test
    @DisplayName("an uncapped route carries what the capped ones cannot")
    void uncappedRouteCarriesTheRest() {
        // Above both capped ceilings. NEFT has no cap at all, and empty headroom means unbounded
        // rather than zero — reading it as zero would refuse every NEFT payment.
        RouteSelector.Selection s = new RouteSelector(new StubLedger(), CONFIG)
                .select(ACCOUNT, rupees(2_500_000));

        assertThat(s.selected().route()).isEqualTo(PaymentRoute.NEFT);
        assertThat(s.selected().remainingHeadroom()).isEmpty();
    }

    @Test
    @DisplayName("with no route available, the refusal carries the headroom figures")
    void refusalStatesRemainingHeadroom() {
        // Only capped routes configured, both exhausted. REQ-701 requires the remaining headroom
        // stated rather than a generic refusal — a trader told "that did not work" learns nothing
        // about what would.
        Map<PaymentRoute, RouteCap> cappedOnly = new EnumMap<>(PaymentRoute.class);
        cappedOnly.put(PaymentRoute.UPI, CONFIG.get(PaymentRoute.UPI));
        cappedOnly.put(PaymentRoute.NET_BANKING, CONFIG.get(PaymentRoute.NET_BANKING));

        StubLedger ledger = new StubLedger();
        ledger.set(PaymentRoute.UPI, rupees(195_000));
        ledger.set(PaymentRoute.NET_BANKING, rupees(999_000));

        RouteSelector.Selection s = new RouteSelector(ledger, cappedOnly).select(ACCOUNT, rupees(50_000));

        assertThat(s.isSelected()).isFalse();
        assertThat(s.unavailable().headroomByRoute())
                .containsEntry(PaymentRoute.UPI, rupees(5_000))
                .containsEntry(PaymentRoute.NET_BANKING, rupees(1_000));
        assertThat(s.unavailable().bestHeadroom()).contains(rupees(5_000));
    }

    @Test
    @DisplayName("Rule A9d offers only alternatives that can actually carry the amount")
    void alternativesMustBeAbleToWork() {
        // "A recovery action must be able to work." Offering a route without the headroom turns
        // Try Again into a button that promises a payment and delivers another refusal.
        StubLedger ledger = new StubLedger();
        ledger.set(PaymentRoute.NET_BANKING, rupees(999_000));

        var alternatives = new RouteSelector(ledger, CONFIG)
                .alternativesFor(ACCOUNT, rupees(50_000), PaymentRoute.UPI);

        assertThat(alternatives)
                .doesNotContain(PaymentRoute.UPI)          // the one that just failed
                .doesNotContain(PaymentRoute.NET_BANKING)  // only ₹1,000 left
                .containsExactly(PaymentRoute.NEFT);
    }

    @Test
    @DisplayName("headroom floors at zero without borrowing Rule B9's helper")
    void headroomFloorsAtZeroAndFlagsBeingOverCap() {
        // A cap lowered below what has already gone out. Showing "−₹50,000 remaining" is worse
        // than showing none, but the anomaly must still be detectable rather than zeroed away.
        RouteCap upi = CONFIG.get(PaymentRoute.UPI);

        assertThat(upi.remainingAfter(rupees(250_000))).contains(Money.ZERO);
        assertThat(upi.isOverCap(rupees(250_000))).isTrue();
        assertThat(upi.isOverCap(rupees(150_000))).isFalse();
    }

    @Test
    @DisplayName("an unconfigured route is never selected")
    void unconfiguredRouteIsNotExecutable() {
        // A missing configuration entry means "not executable", not "uncapped". Reading it the
        // other way would send money down a rail nobody set up.
        Map<PaymentRoute, RouteCap> onlyNeft = Map.of(PaymentRoute.NEFT, CONFIG.get(PaymentRoute.NEFT));

        RouteSelector.Selection s = new RouteSelector(new StubLedger(), onlyNeft).select(ACCOUNT, rupees(100));

        assertThat(s.selected().route()).isEqualTo(PaymentRoute.NEFT);
        assertThat(s.selected().wasSwitched())
                .as("NEFT was the first configured choice, so nothing was switched from")
                .isFalse();
    }

    // ---- harness ----

    private static Money rupees(long rupees) {
        return Money.ofPaise(rupees * 100L);
    }

    private static Map<PaymentRoute, RouteCap> config() {
        Map<PaymentRoute, RouteCap> m = new LinkedHashMap<>();
        m.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI, Optional.of(rupees(200_000)), Money.ZERO));
        m.put(PaymentRoute.NET_BANKING,
                new RouteCap(PaymentRoute.NET_BANKING, Optional.of(rupees(1_000_000)), Money.ZERO));
        m.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT, Optional.empty(), Money.ZERO));
        return m;
    }

    /** In-memory ledger. The selector is pure apart from this read, which is why a stub suffices. */
    private static final class StubLedger implements RouteCapLedger {
        private final Map<PaymentRoute, Money> sent = new EnumMap<>(PaymentRoute.class);

        void set(PaymentRoute route, Money amount) {
            this.sent.put(route, amount);
        }

        @Override
        public Optional<Money> remainingToday(AccountRef account, PaymentRoute route) {
            RouteCap cap = CONFIG.get(route);
            return cap == null ? Optional.empty()
                    : cap.remainingAfter(this.sent.getOrDefault(route, Money.ZERO));
        }

        @Override
        public void record(AccountRef account, PaymentRoute route, Money amount) {
            this.sent.merge(route, amount, Money::plus);
        }

        @Override
        public Optional<Money> remainingOn(AccountRef account, PaymentRoute route, LocalDate day) {
            return remainingToday(account, route);
        }
    }
}
