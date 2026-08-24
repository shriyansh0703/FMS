package com.thinq.fms.movement.payout;

import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.RequestAlreadyOpenException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link PayoutRequestRepository} over {@code fms_payout_request} (V21).
 *
 * <p>Plain JDBC, matching {@code JdbcPayinAttemptRepository} and the rest of this service, and not
 * a scanned component for the same reason: the API test contexts run without a {@code DataSource}
 * by design.
 *
 * <p><b>Rule W4 is not enforced here, but it is translated here.</b> {@link #openFor} answers the
 * question; the rule itself lives in the partial unique index
 * {@code fms_payout_one_open_per_account}. A service-level check would be a race — two requests
 * arriving together both read no open request and both proceed — so the index is the only thing
 * that cannot be raced.
 *
 * <p>What this class owes the rule is turning its violation into the domain exception
 * {@link PayoutRequestRepository#save} promises. That translation was missing, and the consequence
 * was not cosmetic: {@code DuplicateKeyException} escaped to the error boundary, hit the catch-all,
 * and a second withdrawal submission answered <b>500 internal_error</b> where the published
 * specification declares <b>409 request_already_open</b>. Every test of Rule W4 ran against a fake
 * repository throwing the domain exception directly, so nothing exercised the path production uses.
 */
public class JdbcPayoutRequestRepository implements PayoutRequestRepository {

    /** The open states, matching the index predicate. Kept together so they cannot drift apart. */
    private static final String OPEN_STATES = "'ACCEPTED', 'QUEUED_FOR_RUN', 'INSTRUCTED'";

    /**
     * V21's Rule W4 index, as Postgres renders it in a constraint-violation message.
     *
     * <p><b>The quotes are part of the constant, and that is the point.</b> The structural read —
     * {@code PSQLException.getServerErrorMessage().getConstraint()} — is not available: the driver
     * is {@code runtime} scope precisely so application code does not compile against it, and
     * promoting it to reach one field would couple this repository to the driver to save a string
     * comparison. So the name is matched in the message text, quoted, because an unquoted match
     * would also fire on any future index whose name merely begins with this one.
     *
     * <p>The match is guarded rather than trusted: {@code JdbcPayoutRequestRepositoryTest} drives
     * both a Rule W4 violation and a violation of this table's other unique index against a real
     * server, so a driver that changed its message format fails a test instead of turning Rule W4
     * back into a 500.
     */
    private static final String ONE_OPEN_PER_ACCOUNT = "\"fms_payout_one_open_per_account\"";

    private static final String COLUMNS = """
            id, account_id, amount_paise, state, destination_ref, destination_masked,
            withdrawable_at_request_paise, withdrawable_at_settle_paise, arrival_date_quoted,
            credited_on, bank_reference, fms_reference, settlement_reason_code,
            settlement_reason_text, amount_sent_paise, requested_at, closed_at, version""";

    private final JdbcClient db;

    public JdbcPayoutRequestRepository(JdbcClient db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    @Override
    public PayoutRequest save(PayoutRequest request) {
        Objects.requireNonNull(request, "request");

        if (request.id() == 0L) {
            long id = insert(request);

            return findFor(request.account(), id).orElseThrow(() -> new FmsInvariantException(
                    "payout_request_vanished_after_insert",
                    "request " + id + " was inserted and could not be read back"));
        }

        int written = this.db.sql("""
                        UPDATE fms_payout_request
                           SET state = ?, withdrawable_at_settle_paise = ?, credited_on = ?,
                               bank_reference = ?, settlement_reason_code = ?,
                               settlement_reason_text = ?, amount_sent_paise = ?, closed_at = ?,
                               version = ?
                         WHERE id = ? AND version = ?""")
                .params(request.state().name(),
                        paiseOrNull(request.withdrawableAtSettleOrNull()),
                        dateOrNull(request.creditedOnOrNull()),
                        request.bankReferenceOrNull(),
                        nameOrNull(request.settlementReasonCodeOrNull()),
                        request.settlementReasonTextOrNull(),
                        paiseOrNull(request.amountSentOrNull()),
                        timestampOrNull(request.closedAtOrNull()),
                        request.version(),
                        request.id(),
                        request.loadedVersion())
                .update();

        if (written == 0) {
            throw new FmsInvariantException("payout_request_stale_write",
                    "request " + request.id() + " was modified by another writer since it was read"
                            + " (expected version " + request.loadedVersion() + ")");
        }
        request.writtenAt(request.version());
        return request;
    }

    @Override
    public Optional<PayoutRequest> openFor(AccountRef account) {
        return this.db.sql("SELECT " + COLUMNS + " FROM fms_payout_request"
                        + " WHERE account_id = ? AND state IN (" + OPEN_STATES + ")")
                .params(account.ucc())
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<PayoutRequest> findFor(AccountRef account, long id) {
        return this.db.sql("SELECT " + COLUMNS
                        + " FROM fms_payout_request WHERE account_id = ? AND id = ?")
                .params(account.ucc(), id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<PayoutRequest> openRequestsForRun(LocalDate runDate) {
        // The end-of-day run takes everything still open, oldest first. INSTRUCTED is deliberately
        // included: an instruction issued and not yet resolved is exactly what the run re-reads
        // before deciding whether to reissue (lld-backend.md §6.3).
        return this.db.sql("SELECT " + COLUMNS + " FROM fms_payout_request"
                        + " WHERE state IN (" + OPEN_STATES + ")"
                        + " ORDER BY requested_at ASC")
                .query(this::map)
                .list();
    }

    /**
     * The insert, with Rule W4's violation translated into the exception {@link #save}'s contract
     * promises.
     *
     * <p><b>Keyed on the index name rather than on "a duplicate key happened".</b> This table
     * carries a second unique index, {@code fms_payout_fms_reference}, and reporting its violation
     * as {@code request_already_open} would tell a trader they hold an open withdrawal when what
     * actually occurred is the reference generator issuing the same number twice — an invariant
     * failure that must page somebody, not a 409 the client is invited to render as an ordinary
     * refusal. An unrecognised duplicate is rethrown untouched and reaches the error boundary's
     * catch-all, which is where a failure nobody has classified belongs. See
     * {@link #ONE_OPEN_PER_ACCOUNT} for why the name is matched in the message text.
     */
    private long insert(PayoutRequest request) {
        try {
            return this.db.sql("""
                            INSERT INTO fms_payout_request
                                (account_id, amount_paise, state, destination_ref, destination_masked,
                                 withdrawable_at_request_paise, withdrawable_at_settle_paise,
                                 arrival_date_quoted, credited_on, bank_reference, fms_reference,
                                 settlement_reason_code, settlement_reason_text, amount_sent_paise,
                                 requested_at, closed_at, version)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            RETURNING id""")
                    .params(request.account().ucc(),
                            request.amount().paise(),
                            request.state().name(),
                            request.destinationRef(),
                            request.destinationMasked(),
                            request.withdrawableAtRequest().paise(),
                            paiseOrNull(request.withdrawableAtSettleOrNull()),
                            Date.valueOf(request.arrivalDateQuoted()),
                            dateOrNull(request.creditedOnOrNull()),
                            request.bankReferenceOrNull(),
                            request.fmsReference(),
                            nameOrNull(request.settlementReasonCodeOrNull()),
                            request.settlementReasonTextOrNull(),
                            paiseOrNull(request.amountSentOrNull()),
                            Timestamp.from(request.requestedAt()),
                            timestampOrNull(request.closedAtOrNull()),
                            request.version())
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(ONE_OPEN_PER_ACCOUNT)) {
                throw new RequestAlreadyOpenException(
                        "this account already has an open withdrawal request");
            }
            throw e;
        }
    }

    private static Long paiseOrNull(Money money) {
        return money == null ? null : money.paise();
    }

    private static String nameOrNull(SettlementReasonCode code) {
        return code == null ? null : code.name();
    }

    private static Date dateOrNull(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private PayoutRequest map(ResultSet rs, int rowNum) throws SQLException {
        return PayoutRequest.rehydrate(
                rs.getLong("id"),
                AccountRef.of(rs.getString("account_id")),
                Money.ofPaise(rs.getLong("amount_paise")),
                rs.getString("destination_ref"),
                rs.getString("destination_masked"),
                rs.getString("fms_reference"),
                Money.ofPaise(rs.getLong("withdrawable_at_request_paise")),
                rs.getDate("arrival_date_quoted").toLocalDate(),
                rs.getTimestamp("requested_at").toInstant(),
                PayoutState.valueOf(rs.getString("state")),
                rs.getInt("version"),
                moneyOrNull(rs, "withdrawable_at_settle_paise"),
                moneyOrNull(rs, "amount_sent_paise"),
                reasonOrNull(rs.getString("settlement_reason_code")),
                rs.getString("settlement_reason_text"),
                rs.getString("bank_reference"),
                rs.getDate("credited_on") == null ? null : rs.getDate("credited_on").toLocalDate(),
                rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant());
    }

    private static Money moneyOrNull(ResultSet rs, String column) throws SQLException {
        long paise = rs.getLong(column);
        return rs.wasNull() ? null : Money.ofPaise(paise);
    }

    private static SettlementReasonCode reasonOrNull(String code) {
        return code == null ? null : SettlementReasonCode.valueOf(code);
    }
}
