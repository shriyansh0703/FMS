package com.thinq.fms.qa;

import com.thinq.fms.api.AuthenticatedAccount;
import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.integration.techexcel.TechExcelErrorCode;
import com.thinq.fms.messaging.MessageSpec;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;
import com.thinq.fms.settings.DebitInterestRate;
import com.thinq.fms.settings.FundsSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalogue sections TC-SEC, TC-COM, TC-CFG and TC-INT, value contracts —
 * {@code docs/qa/test-cases.md}.
 *
 * <p>These are the types that carry the system's identity and disclosure rules: what an account
 * identifier may contain, what a masked bank account may not, which channels exist, and how a
 * vendor error code is read. {@code AccountRef} was at 70% with its rejection branch untested,
 * which is the branch that keeps a PAN out of an account identity.
 */
class PlatformContractTest {

    // ------------------------------------------------------------------------- AccountRef (R4)

    @ParameterizedTest
    @ValueSource(strings = {"A", "UCC0001", "abc123", "12345678901234567890"})
    @DisplayName("TC-SEC-021 — a well-formed UCC is accepted")
    void aWellFormedUccIsAccepted(String ucc) {
        assertThat(AccountRef.of(ucc).ucc()).isEqualTo(ucc);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                          // empty
            " ",                         // whitespace
            "UCC 0001",                  // an embedded space
            "UCC-0001",                  // punctuation
            "123456789012345678901",     // twenty-one characters, one over the back office's width
            "UCC_0001",
            "ücc0001"})
    @DisplayName("TC-SEC-022 — a malformed account identifier is refused at construction")
    void aMalformedAccountIdentifierIsRefused(String ucc) {
        // Parse, don't validate: an AccountRef that exists is well-formed, so nothing downstream
        // re-checks it. That only holds if the constructor actually refuses.
        assertThatThrownBy(() -> AccountRef.of(ucc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-SEC-023 — the refusal does not echo the value it refused")
    void theRefusalDoesNotEchoTheValue() {
        // A rejected identifier may well be the regulated value R4 forbids carrying. Echoing it
        // into an exception message puts it in a log.
        assertThatThrownBy(() -> AccountRef.of("ABCDE1234F-SECRET-PAN"))
                .hasMessageNotContaining("ABCDE1234F")
                .hasMessageContaining("length");
    }

    @Test
    @DisplayName("TC-SEC-024 — a null account identifier is refused")
    void aNullAccountIdentifierIsRefused() {
        assertThatThrownBy(() -> AccountRef.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-SEC-025 — an account renders as its UCC and nothing else")
    void anAccountRendersAsItsUccAndNothingElse() {
        assertThat(AccountRef.of("UCC0001")).hasToString("UCC0001");
    }

    @Test
    @DisplayName("TC-SEC-025a — the type bounds charset and length, and cannot itself exclude a PAN")
    void theTypeCannotItselfExcludeARegulatedIdentifier() {
        // FINDING QA-01, recorded rather than asserted away. The class documentation says this is
        // "a UCC code and nothing else … deliberately not a PAN", but a PAN is ten alphanumeric
        // characters and the pattern admits one to twenty. So the type bounds the shape and R4's
        // actual protection rests on the gateway putting a UCC in the subject claim — which is a
        // property of the deployment, not of this constructor.
        //
        // Pinned here so the limitation is visible: if the rule ever becomes structural, this
        // test fails and says why.
        assertThatCode(() -> AccountRef.of("ABCDE1234F")).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------- AuthenticatedAccount (LLD §4.3)

    @Test
    @DisplayName("TC-SEC-026 — the account comes from the principal's subject, never from the request")
    void theAccountComesFromThePrincipal() {
        Principal principal = () -> "UCC0001";

        assertThat(AuthenticatedAccount.of(principal)).isEqualTo(AccountRef.of("UCC0001"));
    }

    @Test
    @DisplayName("TC-SEC-027 — a principal with no subject is a misconfigured gateway, not a 401")
    void aPrincipalWithNoSubjectIsAnOutage() {
        // The platform gateway rejects an absent or expired token before this system sees the
        // request. Reaching here without a subject means the gateway is not doing that, which is
        // an outage to fix rather than a refusal to render.
        assertThatThrownBy(() -> AuthenticatedAccount.of(() -> ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway");
        assertThatThrownBy(() -> AuthenticatedAccount.of(() -> "   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AuthenticatedAccount.of(() -> null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-SEC-028 — an absent principal is refused rather than defaulted")
    void anAbsentPrincipalIsRefused() {
        assertThatThrownBy(() -> AuthenticatedAccount.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-SEC-029 — a subject that is not a well-formed UCC does not become an account")
    void aSubjectThatIsNotAUccDoesNotBecomeAnAccount() {
        assertThatThrownBy(() -> AuthenticatedAccount.of(() -> "not a ucc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------- VerifiedBankAccount (PR-31, PR-32)

    @Test
    @DisplayName("TC-SEC-030 — a masked account number carrying too many digits is refused")
    void anUnmaskedAccountNumberIsRefused() {
        // Profile masks server-side. A value with a full number in it would be persisted onto the
        // payout request and rendered into a message months later, so it is refused here rather
        // than redacted later.
        assertThatThrownBy(() -> new VerifiedBankAccount(
                "acc-1", "50100234567891", "HDFC Bank", true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many digits");
    }

    @Test
    @DisplayName("TC-SEC-031 — the ordinary masked form is accepted")
    void theOrdinaryMaskedFormIsAccepted() {
        assertThatCode(() -> new VerifiedBankAccount("acc-1", "••••4471", "HDFC Bank", true, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-SEC-032 — six digits is the boundary and is still accepted")
    void sixDigitsIsTheBoundary() {
        assertThatCode(() -> new VerifiedBankAccount("acc-1", "••123456", "HDFC", false, true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new VerifiedBankAccount("acc-1", "••1234567", "HDFC", false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-SEC-033 — an account without its reference or masked form is refused")
    void anAccountWithoutItsReferenceOrMaskIsRefused() {
        assertThatThrownBy(() -> new VerifiedBankAccount(null, "••••4471", "HDFC", true, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifiedBankAccount("acc-1", null, "HDFC", true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-SEC-034 — verified and primary are independent facts")
    void verifiedAndPrimaryAreIndependent() {
        // REQ-706 defaults to the primary account; REQ-203 permits only a verified one. A primary
        // account whose verification was withdrawn must fail the second check, not pass on the
        // first.
        VerifiedBankAccount primaryUnverified =
                new VerifiedBankAccount("acc-1", "••••4471", "HDFC", true, false);

        assertThat(primaryUnverified.primary()).isTrue();
        assertThat(primaryUnverified.verified()).isFalse();
    }

    // -------------------------------------------------------------------- MessageChannel (C1/C2)

    @ParameterizedTest
    @EnumSource(MessageChannel.class)
    @DisplayName("TC-COM-046 — every channel round-trips through its wire value")
    void everyChannelRoundTrips(MessageChannel channel) {
        assertThat(MessageChannel.fromWire(channel.wireValue())).isEqualTo(channel);
    }

    @Test
    @DisplayName("TC-COM-047 — a channel is read case- and whitespace-insensitively from the wire")
    void aChannelIsReadCaseInsensitively() {
        assertThat(MessageChannel.fromWire(" SMS ")).isEqualTo(MessageChannel.SMS);
        assertThat(MessageChannel.fromWire("Email")).isEqualTo(MessageChannel.EMAIL);
    }

    @Test
    @DisplayName("TC-COM-048 — an unknown channel is refused rather than guessed at")
    void anUnknownChannelIsRefused() {
        assertThatThrownBy(() -> MessageChannel.fromWire("push"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown channel");
        assertThatThrownBy(() -> MessageChannel.fromWire(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageChannel.fromWire(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-COM-049 — the three channels are exactly the ones the design defines, and push is not one")
    void theThreeChannelsAreTheOnesDefined() {
        // There is no mobile application, so there is no push surface. Modelling one would be
        // modelling a channel nothing can send on.
        assertThat(MessageChannel.values()).containsExactly(
                MessageChannel.SMS, MessageChannel.EMAIL, MessageChannel.WHATSAPP);
    }

    // ------------------------------------------------------------------------ MessageSpec (R4/R5)

    @Test
    @DisplayName("TC-COM-050 — a message spec without a template key is refused")
    void aSpecWithoutATemplateKeyIsRefused() {
        assertThatThrownBy(() -> new MessageSpec("", MessageChannel.SMS, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> new MessageSpec("   ", MessageChannel.SMS, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageSpec(null, MessageChannel.SMS, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-COM-051 — a spec's parameters cannot be mutated after it is built")
    void aSpecsParametersCannotBeMutated() {
        // The Communication Service answers parameter_contract on any mismatch against the
        // template's declared set, so a parameter map that can change after the spec was built is
        // a submission that fails for a reason nothing recorded.
        Map<String, String> mutable = new HashMap<>(Map.of("amount", "38400.00"));
        MessageSpec spec = new MessageSpec("THINQ_MARGIN_SHORTFALL", MessageChannel.SMS, mutable);

        mutable.put("balance", "12345.00");

        assertThat(spec.parameters()).containsOnlyKeys("amount");
        assertThatThrownBy(() -> spec.parameters().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("TC-COM-052 — a spec requires a channel, because one submission carries exactly one")
    void aSpecRequiresAChannel() {
        assertThatThrownBy(() -> new MessageSpec("K", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MessageSpec("K", MessageChannel.SMS, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------- FundsSettings (Rule G1)

    @Test
    @DisplayName("TC-CFG-021 — a minimum add of zero is refused, because it disables the floor silently")
    void aZeroMinimumAddIsRefused() {
        assertThatThrownBy(() -> new FundsSettings(
                Money.ZERO, LocalTime.of(15, 0), DebitInterestRate.unavailable()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("silently");
        assertThatThrownBy(() -> new FundsSettings(
                Money.ofPaise(-1L), LocalTime.of(15, 0), DebitInterestRate.unavailable()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-CFG-022 — every configured value is required rather than defaulted in place")
    void everyConfiguredValueIsRequired() {
        assertThatThrownBy(() -> new FundsSettings(null, LocalTime.NOON, DebitInterestRate.unavailable()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FundsSettings(Money.ofPaise(1L), null, DebitInterestRate.unavailable()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FundsSettings(Money.ofPaise(1L), LocalTime.NOON, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TC-CFG-023 — the shipped defaults are ₹100, a 3:00 PM cut-off, and a provisional rate")
    void theShippedDefaultsAreTheConfiguredValues() {
        FundsSettings defaults = FundsSettings.defaults();

        assertThat(defaults.minimumAdd()).isEqualTo(Money.ofPaise(10_000L));
        assertThat(defaults.payoutCutoff()).isEqualTo(LocalTime.of(15, 0));
        assertThat(defaults.debitInterest().provisional()).isTrue();
        assertThat(defaults.debitInterest().annualPercent()).contains(new BigDecimal("18.00"));
    }

    @Test
    @DisplayName("TC-CFG-024 — the shipped rate is provisional, so no production message may quote it")
    void theShippedRateMayNotBeQuoted() {
        // EB-8: the real rate lives in TechExcel and is not set. The obligation to disclose the
        // debt is not conditional; quoting a stand-in rate is.
        assertThat(FundsSettings.defaults().debitInterest().quotableInMessages()).isFalse();
    }

    // ------------------------------------------------------------- TechExcelErrorCode (OA-7)

    @ParameterizedTest
    @EnumSource(value = TechExcelErrorCode.class, names = {"UNRECOGNISED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("TC-INT-026 — every documented error code maps from its wire value")
    void everyDocumentedCodeMaps(TechExcelErrorCode code) {
        assertThat(TechExcelErrorCode.fromWire(code.wireValue())).isEqualTo(code);
    }

    @Test
    @DisplayName("TC-INT-027 — an unrecognised code stays unrecognised rather than being guessed at")
    void anUnrecognisedCodeStaysUnrecognised() {
        // Guessing what an unfamiliar response meant is how a rejection gets read as a success.
        assertThat(TechExcelErrorCode.fromWire("Some_New_Validation"))
                .isEqualTo(TechExcelErrorCode.UNRECOGNISED);
        assertThat(TechExcelErrorCode.fromWire(null)).isEqualTo(TechExcelErrorCode.UNRECOGNISED);
        assertThat(TechExcelErrorCode.fromWire("   ")).isEqualTo(TechExcelErrorCode.UNRECOGNISED);
    }

    @Test
    @DisplayName("TC-INT-028 — both spellings of each token error are accepted")
    void bothTokenSpellingsAreAccepted() {
        // The contract uses different spellings on different endpoints, so neither is assumed
        // canonical.
        assertThat(TechExcelErrorCode.fromWire("Token Missing"))
                .isEqualTo(TechExcelErrorCode.TOKEN_MISSING);
        assertThat(TechExcelErrorCode.fromWire("Token Validation Missing"))
                .isEqualTo(TechExcelErrorCode.TOKEN_MISSING);
        assertThat(TechExcelErrorCode.fromWire("Token Expired"))
                .isEqualTo(TechExcelErrorCode.TOKEN_EXPIRED);
        assertThat(TechExcelErrorCode.fromWire("Token Validation Expired"))
                .isEqualTo(TechExcelErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("TC-INT-029 — a code is matched without regard to case or surrounding whitespace")
    void aCodeIsMatchedCaseInsensitively() {
        assertThat(TechExcelErrorCode.fromWire("  input_validation  "))
                .isEqualTo(TechExcelErrorCode.INPUT_VALIDATION);
    }

    @Test
    @DisplayName("TC-INT-030 — only the token errors are session problems worth retrying")
    void onlyTokenErrorsAreSessionProblems() {
        assertThat(TechExcelErrorCode.TOKEN_MISSING.isSessionProblem()).isTrue();
        assertThat(TechExcelErrorCode.TOKEN_EXPIRED.isSessionProblem()).isTrue();
        assertThat(TechExcelErrorCode.INPUT_VALUE_VALIDATION.isSessionProblem()).isFalse();
        assertThat(TechExcelErrorCode.DATABASE_EXCEPTION.isSessionProblem()).isFalse();
        assertThat(TechExcelErrorCode.UNRECOGNISED.isSessionProblem()).isFalse();
    }

    @Test
    @DisplayName("TC-INT-031 — the vocabulary carries no code meaning 'already paid'")
    void thereIsNoAlreadyPaidCode() {
        // The one that matters is the one that is missing. TechExcel returns Input_Value_Validation
        // for both an input-value rejection and a duplicate, so no code here may be read as
        // duplication — which is why the run reads status before it reissues.
        assertThat(TechExcelErrorCode.values())
                .extracting(Enum::name)
                .doesNotContain("DUPLICATE", "ALREADY_PAID", "DUPLICATION_VALIDATION");
    }
}
