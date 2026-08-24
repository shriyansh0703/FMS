package com.thinq.fms.migration;

import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.derivation.MarginSourceKind;
import com.thinq.fms.derivation.WithdrawableVerdict;
import com.thinq.fms.integration.communication.DeliveryStatus;
import com.thinq.fms.integration.communication.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every vocabulary declared in both a Java enum and a SQL {@code CHECK}, held in step.
 *
 * <p>Drift is silent in both directions. A value in Java and not in SQL fails at insert, in
 * production, on the first row that uses it. A value in SQL and not in Java is a row that comes
 * back and cannot be read. Neither shows up in a review of either file alone, because each looks
 * correct by itself.
 *
 * <p>{@code PayoutStateTest} holds the sixth — the payout state machine — alongside the Rule W4
 * index predicate that depends on it.
 */
class VocabularyDriftTest {

    private static final String V25 = "V25__fms_derivation_snapshot.sql";
    private static final String V26 = "V26__fms_message_delivery.sql";

    @Test
    @DisplayName("V25's source vocabulary matches MarginSourceKind")
    void marginSourceKindMatchesV25() {
        assertThat(Migrations.checkValues(V25, "fms_snapshot_source_vocabulary"))
                .isEqualTo(namesOf(MarginSourceKind.values()));
    }

    @Test
    @DisplayName("V25's reconciliation vocabulary matches WithdrawableVerdict")
    void withdrawableVerdictMatchesV25() {
        // REQ-107 renders this alongside the figure, so a value the database refuses is a
        // provenance line the trader never sees.
        assertThat(Migrations.checkValues(V25, "fms_snapshot_reconciliation_vocabulary"))
                .isEqualTo(namesOf(WithdrawableVerdict.values()));
    }

    @Test
    @DisplayName("V25's context vocabulary matches DerivationContext")
    void derivationContextMatchesV25() {
        assertThat(Migrations.checkValues(V25, "fms_snapshot_context_vocabulary"))
                .isEqualTo(namesOf(BalanceDerivationService.DerivationContext.values()));
    }

    @Test
    @DisplayName("V26's status vocabulary matches DeliveryStatus's ten wire values")
    void deliveryStatusMatchesV26() {
        Set<String> inJava = Arrays.stream(DeliveryStatus.values())
                .map(DeliveryStatus::wireValue)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(Migrations.checkValues(V26, "fms_msg_status_vocabulary"))
                .as("the Communication Service's vocabulary is theirs; drift here is a contract change")
                .isEqualTo(inJava).hasSize(10);
    }

    @Test
    @DisplayName("V26 admits exactly the granted channels — WHATSAPP is modelled but not permitted")
    void channelConstraintExcludesWhatsappDeliberately() {
        // This is the one row where the enum and the constraint are INTENDED to differ, and that
        // is precisely why it needs a test. OA-2 leaves the WhatsApp grant unconfirmed, so a
        // delivery row must not be storable for a message this system cannot submit.
        //
        // When the grant arrives, this test fails — which is the point. It is the reminder to
        // change the constraint, and it fails at build time rather than as a message nobody can
        // send.
        Set<String> permitted = Migrations.checkValues(V26, "fms_msg_channel_vocabulary");

        assertThat(permitted).containsExactlyInAnyOrder(
                MessageChannel.SMS.wireValue(), MessageChannel.EMAIL.wireValue());
        assertThat(permitted)
                .as("OA-2: remove this assertion and add whatsapp to V26 when the grant is confirmed")
                .doesNotContain(MessageChannel.WHATSAPP.wireValue());
    }

    private static Set<String> namesOf(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
