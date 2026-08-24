package com.thinq.fms.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Executes the constraints that carry business rules, against a real server.
 *
 * <p>These replace the eyeballed output of {@code src/test/resources/db/constraint-verification.sql}
 * with pass or fail. That script argued against a container framework on the grounds of dependency
 * weight and build time, and it was a reasonable trade while it held — but it required a human to
 * read its output and notice that a block printed a row count where it should have printed an
 * error, and a check nobody runs is a check that is not being performed. The script stays for
 * manual use; these run on every build.
 *
 * <p>Each test asserts the constraint <b>refuses</b> the row it exists to refuse. A test that only
 * inserted valid rows would pass with every index dropped.
 */
class SchemaConstraintTest extends PostgresTestSupport {

    private static final AtomicLong ACCOUNTS = new AtomicLong(1);

    /** A distinct account per test, so one test's rows cannot satisfy another's uniqueness. */
    private static String account() {
        return "UCC" + ACCOUNTS.getAndIncrement();
    }

    @Test
    @DisplayName("Rule W4 — a second open withdrawal request for the same account is refused")
    void ruleW4RefusesASecondOpenRequest() {
        String account = account();
        insertPayout(account, "ACCEPTED");

        assertThatThrownBy(() -> insertPayout(account, "ACCEPTED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fms_payout_one_open_per_account");
    }

    @Test
    @DisplayName("Rule W4 — the index covers every open state, not only ACCEPTED")
    void ruleW4CoversEveryOpenState() {
        // The predicate lists three states. An index naming only ACCEPTED would pass the test above
        // and still permit two open requests, which is the defect the partial index exists to stop.
        for (String open : new String[]{"QUEUED_FOR_RUN", "INSTRUCTED"}) {
            String account = account();
            insertPayout(account, open);

            assertThatThrownBy(() -> insertPayout(account, open))
                    .as("a second request in %s must be refused", open)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("Rule W4 — a closed request does not block the next one")
    void ruleW4LetsAClosedRequestBeFollowedByANewOne() {
        // The other half of the rule. An unconditional unique index would permit one withdrawal per
        // account ever, which is a worse bug than the one it prevents and would not show up in any
        // test that only tried to insert duplicates.
        String account = account();
        insertPayout(account, "PAID");

        assertThatCode(() -> insertPayout(account, "ACCEPTED")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the payout state vocabulary refuses a state the index predicate does not list")
    void payoutStateVocabularyIsClosed() {
        // The predicate above is only half the rule: an unlisted state would slip past the partial
        // index silently. This is what stops that.
        assertThatThrownBy(() -> insertPayout(account(), "PENDING_SOMETHING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rule A6 — two attempts cannot share a gateway payment reference")
    void ruleA6RefusesADuplicateGatewayReference() {
        // Namespaced away from the id-derived references real attempts use. Minting
        // "FMS-PAYIN-<counter>" here collided with "FMS-PAYIN-<id>" rows created by the repository
        // tests once the whole suite shared one database — the reference looked local to this class
        // and was not.
        String reference = "CONSTRAINT-TEST-REF-" + ACCOUNTS.getAndIncrement();
        insertPayin(account(), "CONFIRMED", reference);

        assertThatThrownBy(() -> insertPayin(account(), "CONFIRMED", reference))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fms_payin_gateway_ref");
    }

    @Test
    @DisplayName("Rule A6 — the uniqueness is partial, so many attempts may have no reference yet")
    void ruleA6PermitsManyAttemptsWithoutAReference() {
        // A unique index over a nullable column without the WHERE clause would treat two nulls as
        // distinct on PostgreSQL and appear to work, then behave differently on a server configured
        // otherwise. Asserting the permissive direction pins the intent.
        assertThatCode(() -> {
            insertPayin(account(), "INITIATED", null);
            insertPayin(account(), "INITIATED", null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a payin amount must be positive")
    void payinAmountMustBePositive() {
        assertThatThrownBy(() -> db.sql("""
                        INSERT INTO fms_payin_attempt (account_id, amount_paise, route, state)
                        VALUES (?, ?, 'UPI', 'INITIATED')""")
                .params(account(), 0L).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("one message intent per account, template, channel and occurrence")
    void messageIntentIsUniquePerOccurrence() {
        // This one shipped through two reviews. The index was over a nullable asserted_ref, which
        // made every intent distinct and let the same message be queued repeatedly.
        String account = account();
        insertIntent(account, "MARGIN_SHORTFALL", "SMS", "SHORTFALL-1");

        assertThatThrownBy(() -> insertIntent(account, "MARGIN_SHORTFALL", "SMS", "SHORTFALL-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fms_intent_once");
    }

    @Test
    @DisplayName("the channel is compared exactly, so one canonical form must be written")
    void theChannelIsComparedExactly() {
        // fms_intent_once compares the stored strings, so 'SMS' and 'sms' are two intents and the
        // same message would go twice. Nothing in the schema prevents that — the guarantee rests on
        // every writer using one form, which JdbcMessageOutbox does by storing MessageChannel.name().
        // Pinned here so a future writer that stores the wire form fails this rather than production.
        String account = account();
        insertIntent(account, "MARGIN_SHORTFALL", "SMS", "SHORTFALL-1");

        assertThatCode(() -> insertIntent(account, "MARGIN_SHORTFALL", "sms", "SHORTFALL-1"))
                .as("case difference defeats the index — this is why one canonical form is written")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a different channel or occurrence is a different intent")
    void messageIntentDistinguishesChannelAndOccurrence() {
        String account = account();
        insertIntent(account, "MARGIN_SHORTFALL", "SMS", "SHORTFALL-1");

        assertThatCode(() -> {
            insertIntent(account, "MARGIN_SHORTFALL", "EMAIL", "SHORTFALL-1");
            insertIntent(account, "MARGIN_SHORTFALL", "SMS", "SHORTFALL-2");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty occurrence reference is refused rather than stored")
    void emptyAssertedReferenceIsRefused() {
        assertThatThrownBy(() -> insertIntent(account(), "MARGIN_SHORTFALL", "sms", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rule C8 — a bank reference identical to our own reference is refused")
    void ruleC8RefusesABankReferenceEqualToOurs() {
        // Giving a trader our reference sends them to a bank the value means nothing to.
        String account = account();
        String ours = "FMS-W-" + ACCOUNTS.getAndIncrement();

        assertThatThrownBy(() -> db.sql("""
                        INSERT INTO fms_payout_request
                            (account_id, amount_paise, state, destination_ref, destination_masked,
                             withdrawable_at_request_paise, arrival_date_quoted, fms_reference,
                             bank_reference)
                        VALUES (?, 100000, 'PAID', 'acc-1', '4471', 500000, ?, ?, ?)""")
                .params(account, LocalDate.of(2026, 8, 24), ours, ours).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("every migration V21 to V26 applied")
    void everyMigrationApplied() {
        // Flyway ran in the base class. If a migration were unapplied, every test above would fail
        // with a missing relation instead of a constraint violation, which is a confusing way to
        // discover it.
        Integer applied = db.sql(
                        "SELECT count(*) FROM flyway_schema_history WHERE success = true")
                .query(Integer.class).single();

        assertThat(applied).as("all seven migrations applied").isGreaterThanOrEqualTo(7);
    }

    // ---- fixtures ----

    private void insertPayout(String account, String state) {
        db.sql("""
                        INSERT INTO fms_payout_request
                            (account_id, amount_paise, state, destination_ref, destination_masked,
                             withdrawable_at_request_paise, arrival_date_quoted, fms_reference)
                        VALUES (?, 100000, ?, 'acc-1', '4471', 500000, ?, ?)""")
                .params(account, state, LocalDate.of(2026, 8, 24),
                        "FMS-W-" + ACCOUNTS.getAndIncrement())
                .update();
    }

    private void insertPayin(String account, String state, String gatewayRef) {
        db.sql("""
                        INSERT INTO fms_payin_attempt
                            (account_id, amount_paise, route, state, gateway_payment_ref)
                        VALUES (?, 500000, 'UPI', ?, ?)""")
                .params(account, state, gatewayRef)
                .update();
    }

    private void insertIntent(String account, String templateKey, String channel, String ref) {
        db.sql("""
                        INSERT INTO fms_message_intent
                            (account_id, template_key, channel, asserted_state, asserted_ref,
                             scheduled_for)
                        VALUES (?, ?, ?, 'ASSERTED', ?, now())""")
                .params(account, templateKey, channel, ref)
                .update();
    }
}
