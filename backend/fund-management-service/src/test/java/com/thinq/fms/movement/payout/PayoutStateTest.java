package com.thinq.fms.movement.payout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.thinq.fms.migration.Migrations;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transition table, and the agreement between this enum and V21.
 *
 * <p>The last two tests are the interesting ones. Rule W4 is enforced by a partial unique index
 * whose predicate names the open states, and that predicate is only equivalent to "one open
 * request per account" while it and this enum say the same thing. Nothing but a test can hold
 * two files in different languages in step, so these read the migration.
 */
class PayoutStateTest {

    private static final String V21_FILE = "V21__fms_payout_request.sql";

    @Test
    @DisplayName("every state is either terminal or open, never both and never neither")
    void everyStateIsExactlyOneOfTerminalOrOpen() {
        // A state that is both would be an open request nothing can follow — a trader blocked
        // from withdrawing forever by a request that can never close.
        for (PayoutState s : PayoutState.values()) {
            assertThat(s.isTerminal() ^ s.isOpen())
                    .as("%s must be exactly one of terminal or open", s)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("RETURNED is the only state reachable from a terminal one")
    void returnedIsTheOnlyPostTerminalTransition() {
        // Rule W7 and Rule L2: a bank refusing money after it was sent does not delete the
        // original, it adds a compensating entry. That is why the machine allows exactly one
        // transition out of a terminal state, and allowing a second would let a paid request be
        // re-instructed.
        for (PayoutState from : PayoutState.values()) {
            if (!from.isTerminal()) {
                continue;
            }
            assertThat(from.allowedNext())
                    .as("terminal state %s", from)
                    .isSubsetOf(EnumSet.of(PayoutState.RETURNED));
        }
    }

    @Test
    @DisplayName("a paid request can never be re-queued for a later run")
    void aPaidRequestIsNeverRequeued() {
        // The transition that would pay a trader twice.
        assertThat(PayoutState.PAID.canTransitionTo(PayoutState.QUEUED_FOR_RUN)).isFalse();
        assertThat(PayoutState.PARTLY_PAID.canTransitionTo(PayoutState.QUEUED_FOR_RUN)).isFalse();
        assertThat(PayoutState.PAID.canTransitionTo(PayoutState.INSTRUCTED)).isFalse();
        assertThat(PayoutState.PARTLY_PAID.canTransitionTo(PayoutState.INSTRUCTED)).isFalse();
    }

    @Test
    @DisplayName("cancellation is permitted while open and refused once terminal")
    void cancellationIsPermittedOnlyWhileOpen() {
        // REQ-619 keeps cancellation available in QUEUED_FOR_RUN, which is easy to lose when
        // that state is added later as an outage path.
        assertThat(PayoutState.ACCEPTED.canTransitionTo(PayoutState.CANCELLED)).isTrue();
        assertThat(PayoutState.QUEUED_FOR_RUN.canTransitionTo(PayoutState.CANCELLED)).isTrue();

        // INSTRUCTED is open but the money is already with the rail; cancelling there would
        // claim to stop something this system no longer controls.
        assertThat(PayoutState.INSTRUCTED.canTransitionTo(PayoutState.CANCELLED)).isFalse();
        assertThat(PayoutState.PAID.canTransitionTo(PayoutState.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("only ACCEPTED may begin a request's life")
    void onlyAcceptedIsALegalInitialState() {
        for (PayoutState s : PayoutState.values()) {
            assertThat(PayoutState.isLegalInitialState(s)).isEqualTo(s == PayoutState.ACCEPTED);
        }
    }

    @Test
    @DisplayName("V21's state CHECK constraint lists exactly this enum's states")
    void migrationVocabularyMatchesTheEnum() {
        Set<String> inSql = valuesOf("fms_payout_state_vocabulary");
        Set<String> inJava = Arrays.stream(PayoutState.values())
                .map(Enum::name).collect(Collectors.toCollection(TreeSet::new));

        // Drift either way is a defect. A state in Java and not in SQL fails at insert; a state
        // in SQL and not in Java is a row nothing can read back.
        assertThat(inSql)
                .as("V21's fms_payout_state_vocabulary must match PayoutState exactly")
                .isEqualTo(inJava);
    }

    @Test
    @DisplayName("V21's Rule W4 index predicate lists exactly the open states")
    void migrationOpenStatePredicateMatchesIsOpen() {
        Set<String> inPredicate = openStatesInIndexPredicate();
        Set<String> openInJava = Arrays.stream(PayoutState.values())
                .filter(PayoutState::isOpen)
                .map(Enum::name).collect(Collectors.toCollection(TreeSet::new));

        // This is the test that protects Rule W4. If someone adds an open state to the enum and
        // forgets the index predicate, the index silently stops covering it and a trader can
        // hold two live withdrawals — with no error anywhere. The failure is invisible in
        // production and obvious here.
        assertThat(inPredicate)
                .as("fms_payout_one_open_per_account must cover exactly the states PayoutState "
                        + "reports as open, or Rule W4 stops being enforced for the difference")
                .isEqualTo(openInJava);
    }

    /** Delegates to the shared reader, which VocabularyDriftTest uses for the other five. */
    private static Set<String> valuesOf(String constraintName) {
        return Migrations.checkValues(V21_FILE, constraintName);
    }

    private static Set<String> openStatesInIndexPredicate() {
        return Migrations.indexPredicateValues(V21_FILE, "fms_payout_one_open_per_account");
    }
}
