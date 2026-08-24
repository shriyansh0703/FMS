package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link PayinAttemptRepository} over {@code fms_payin_attempt} (V22).
 *
 * <p>Plain JDBC rather than JPA, matching the rest of this service. The entity is hand-written and
 * mutable with its own {@code version} column, so there is no provider to hand that to; writing the
 * statements out keeps the optimistic-lock comparison visible at the point it happens instead of
 * behind a mapping annotation.
 *
 * <p><b>Deliberately not a scanned component.</b> The API test contexts exclude
 * {@code DataSourceAutoConfiguration} on purpose — they test the web layer with stubbed
 * collaborators and no database — so a {@code @Repository} here is constructed in contexts that
 * have no {@code JdbcClient} to give it, and every one of those tests fails on context load. It is
 * constructed explicitly by whatever assembles the payin module, alongside the orchestrator, which
 * is not a scanned bean either.
 *
 * <p><b>Every read takes an account.</b> Authorisation is per row and enforced in the WHERE clause,
 * not by a caller remembering to check afterwards — {@link #findByGatewayRef} is the single
 * exception, and it is the one the gateway callback uses, which has no session to scope by.
 */
public class JdbcPayinAttemptRepository implements PayinAttemptRepository {

    private static final String COLUMNS = """
            id, account_id, amount_paise, route, state, gateway_payment_ref,
            outcome_code, source_masked, started_at, resolved_at, version""";

    private final JdbcClient db;
    private final ZoneId zone;

    public JdbcPayinAttemptRepository(JdbcClient db, ZoneId zone) {
        this.db = Objects.requireNonNull(db, "db");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * Insert a new attempt or update an existing one.
     *
     * <p>The update is a compare-and-set against the version the row carried when this instance was
     * loaded. A zero-row result means someone else wrote first, and it is raised rather than
     * ignored: silently discarding the write is precisely the lost update the column exists to
     * prevent, and on a money row the lost write could be a confirmation.
     */
    @Override
    public PayinAttempt save(PayinAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");

        if (attempt.id() == 0L) {
            long id = this.db.sql("""
                            INSERT INTO fms_payin_attempt
                                (account_id, amount_paise, route, state, gateway_payment_ref,
                                 outcome_code, source_masked, started_at, resolved_at, version)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            RETURNING id""")
                    .params(bindable(attempt))
                    .query(Long.class)
                    .single();

            PayinAttempt inserted = PayinAttempt.rehydrate(id, attempt.account(), attempt.amount(),
                    attempt.route(), attempt.startedAt(), attempt.state(), attempt.version(),
                    attempt.gatewayPaymentRef().orElse(null), attempt.outcome().orElse(null),
                    attempt.sourceMasked().orElse(null), attempt.resolvedAt().orElse(null));
            return inserted;
        }

        int written = this.db.sql("""
                        UPDATE fms_payin_attempt
                           SET state = ?, gateway_payment_ref = ?, outcome_code = ?,
                               source_masked = ?, resolved_at = ?, version = ?
                         WHERE id = ? AND version = ?""")
                .params(attempt.state().name(),
                        attempt.gatewayPaymentRef().orElse(null),
                        attempt.outcome().map(Enum::name).orElse(null),
                        attempt.sourceMasked().orElse(null),
                        timestamp(attempt.resolvedAt().orElse(null)),
                        attempt.version(),
                        attempt.id(),
                        attempt.loadedVersion())
                .update();

        if (written == 0) {
            throw new FmsInvariantException("payin_attempt_stale_write",
                    "attempt " + attempt.id() + " was modified by another writer since it was read"
                            + " (expected version " + attempt.loadedVersion() + ")");
        }
        attempt.writtenAt(attempt.version());
        return attempt;
    }

    @Override
    public Optional<PayinAttempt> findFor(AccountRef account, long id) {
        return this.db.sql("SELECT " + COLUMNS
                        + " FROM fms_payin_attempt WHERE account_id = ? AND id = ?")
                .params(account.ucc(), id)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<PayinAttempt> findByGatewayRef(String gatewayPaymentRef) {
        Objects.requireNonNull(gatewayPaymentRef, "gatewayPaymentRef");
        return this.db.sql("SELECT " + COLUMNS
                        + " FROM fms_payin_attempt WHERE gateway_payment_ref = ?")
                .params(gatewayPaymentRef)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<PayinAttempt> lastConfirmedFor(AccountRef account) {
        // Rule A1 opens the amount field on what the trader last added, so this is the most recent
        // CONFIRMED attempt — not the most recent attempt that happens to be confirmed now, and
        // deliberately not a REVERSED one, which is money that did not stay.
        return this.db.sql("SELECT " + COLUMNS + """
                         FROM fms_payin_attempt
                        WHERE account_id = ? AND state = 'CONFIRMED'
                        ORDER BY started_at DESC
                        LIMIT 1""")
                .params(account.ucc())
                .query(this::map)
                .optional();
    }

    @Override
    public List<PayinAttempt> inPeriod(AccountRef account, LocalDate from, LocalDate to) {
        // Inclusive of both ends in the account's own zone. A half-open window on a date range is
        // the classic way to lose the last day's deposits from a statement.
        return this.db.sql("SELECT " + COLUMNS + """
                         FROM fms_payin_attempt
                        WHERE account_id = ?
                          AND started_at >= ? AND started_at < ?
                        ORDER BY started_at DESC""")
                .params(account.ucc(),
                        Timestamp.from(from.atStartOfDay(this.zone).toInstant()),
                        Timestamp.from(to.plusDays(1).atStartOfDay(this.zone).toInstant()))
                .query(this::map)
                .list();
    }

    private Object[] bindable(PayinAttempt a) {
        return new Object[]{
                a.account().ucc(),
                a.amount().paise(),
                a.route().name(),
                a.state().name(),
                a.gatewayPaymentRef().orElse(null),
                a.outcome().map(Enum::name).orElse(null),
                a.sourceMasked().orElse(null),
                timestamp(a.startedAt()),
                timestamp(a.resolvedAt().orElse(null)),
                a.version()};
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private PayinAttempt map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp resolved = rs.getTimestamp("resolved_at");
        String outcome = rs.getString("outcome_code");

        return PayinAttempt.rehydrate(
                rs.getLong("id"),
                AccountRef.of(rs.getString("account_id")),
                Money.ofPaise(rs.getLong("amount_paise")),
                PaymentRoute.valueOf(rs.getString("route")),
                rs.getTimestamp("started_at").toInstant(),
                PayinState.valueOf(rs.getString("state")),
                rs.getInt("version"),
                rs.getString("gateway_payment_ref"),
                outcome == null ? null : PayinOutcome.valueOf(outcome),
                rs.getString("source_masked"),
                resolved == null ? null : resolved.toInstant());
    }
}
