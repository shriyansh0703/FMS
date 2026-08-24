package com.thinq.fms.movement.payin;

import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link RouteCapLedger} over {@code fms_route_cap_usage} (V23).
 *
 * <p><b>The upsert is the whole design.</b> The interface requires {@link #record} to be atomic
 * against concurrent calls for the same account, route and day, and V23's primary key on
 * {@code (account_id, route, usage_date)} is what makes that possible in one statement. A
 * read-modify-write would let two simultaneous payments each read the old total and both pass a cap
 * only one of them fits under — the money equivalent of a lost update, except the trader keeps the
 * difference.
 *
 * <p>Not a scanned component, for the reason recorded on the two repositories: the API test
 * contexts run without a {@code DataSource} by design.
 */
public class JdbcRouteCapLedger implements RouteCapLedger {

    private final JdbcClient db;
    private final Map<PaymentRoute, RouteCap> configuration;
    private final Clock clock;
    private final ZoneId zone;

    public JdbcRouteCapLedger(JdbcClient db,
                              Map<PaymentRoute, RouteCap> configuration,
                              Clock clock,
                              ZoneId zone) {
        this.db = Objects.requireNonNull(db, "db");
        this.configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public Optional<Money> remainingToday(AccountRef account, PaymentRoute route) {
        return remainingOn(account, route, LocalDate.now(this.clock.withZone(this.zone)));
    }

    @Override
    public Optional<Money> remainingOn(AccountRef account, PaymentRoute route, LocalDate day) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(day, "day");

        RouteCap cap = this.configuration.get(route);
        if (cap == null || cap.dailyCap().isEmpty()) {
            // Empty means unbounded, not zero. A caller reading empty as zero would refuse every
            // NEFT payment, which is why the interface says so twice.
            return Optional.empty();
        }

        long used = this.db.sql("""
                        SELECT COALESCE(sent_paise, 0) FROM fms_route_cap_usage
                         WHERE account_id = ? AND route = ? AND usage_date = ?""")
                .params(account.ucc(), route.name(), Date.valueOf(day))
                .query(Long.class)
                .optional()
                .orElse(0L);

        // Floors at zero rather than reporting a negative headroom. A cap lowered after money was
        // already sent under the old one leaves usage above the new cap legitimately, and a
        // negative figure would render as one.
        return Optional.of(cap.dailyCap().get().minus(Money.ofPaise(used)).flooredAtZero());
    }

    @Override
    public void record(AccountRef account, PaymentRoute route, Money sent) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(sent, "sent");

        if (sent.isNegative()) {
            throw new IllegalArgumentException("recorded usage cannot be negative; got " + sent);
        }

        // One statement, so two concurrent confirmations for the same account, route and day
        // accumulate rather than overwrite. The addition happens in the database, against the row
        // the conflict locked, not against a value this process read earlier.
        this.db.sql("""
                        INSERT INTO fms_route_cap_usage (account_id, route, usage_date, sent_paise)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (account_id, route, usage_date)
                        DO UPDATE SET sent_paise = fms_route_cap_usage.sent_paise + EXCLUDED.sent_paise""")
                .params(account.ucc(), route.name(),
                        Date.valueOf(LocalDate.now(this.clock.withZone(this.zone))), sent.paise())
                .update();
    }
}
