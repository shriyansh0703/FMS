package com.thinq.fms.integration.communication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-33: the caller obligations in §6, every one of which fails silently if unmet.
 *
 * <p>None of these produces an error from the platform. Validation there is shallow by design and
 * both providers accept anything address-shaped and answer success, so an unmet obligation shows up
 * as a delivered, billed message that reached the wrong person — and a delivery log that records it
 * as a success. There is no safe placeholder and no way to test this against a live send, which is
 * why the guard has to be here.
 */
class NotificationSubmissionTest {

    private NotificationSubmission submission(MessageChannel channel, String address) {
        return new NotificationSubmission("req-1", "MARGIN_SHORTFALL_STEP_1", channel, address,
                Map.of("amount", "5000.00"));
    }

    @ParameterizedTest(name = "{0} is accepted")
    @ValueSource(strings = {"+919451740121", "+14155552671", "+441632960961"})
    @DisplayName("plain E.164 with a country code is accepted")
    void plainE164IsAccepted(String number) {
        assertThatCode(() -> submission(MessageChannel.SMS, number)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"9451740121", "09451740121", "919451740121"})
    @DisplayName("a number without a leading plus is refused, since nothing downstream would report it")
    void aNumberWithoutAPlusIsRefused(String number) {
        // The platform does not add a country code and owns no numbering plan to guess from. Ten
        // digits is inside its 8–15 bound, so a bare national number is accepted, sent and billed.
        assertThatThrownBy(() -> submission(MessageChannel.SMS, number))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    @DisplayName("parentheses around the country code are refused, not silently stripped")
    void parenthesesAreRefused() {
        // The platform strips punctuation but keeps '+' only at position 0, so this normalises to
        // "919451740121" — a number that passes every check and reaches the provider without its
        // plus. Refusing is the only way to catch it, because nothing later can tell.
        assertThatThrownBy(() -> submission(MessageChannel.SMS, "(+91) 9451 740121"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parentheses");
    }

    @Test
    @DisplayName("an opening parenthesis is caught even when no closing one follows")
    void anOpeningParenthesisAloneIsCaught() {
        // Both parenthesis checks were passing only because the CLOSING one happened to catch the
        // same input: a boundary mutation of the opening check survived. A value with '(' at
        // position 0 and no ')' separates them.
        assertThatThrownBy(() -> submission(MessageChannel.SMS, "(919451740121"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parentheses");

        // And the mirror image, so neither check is load-bearing for the other.
        assertThatThrownBy(() -> submission(MessageChannel.SMS, ")919451740121"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parentheses");
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"+9194517", "+9194517401212345", "+0919451740121", "+91-9451-740121",
            "not-a-number", "+"})
    @DisplayName("a number outside the 8-to-15-digit shape is refused")
    void aMalformedNumberIsRefused(String number) {
        assertThatThrownBy(() -> submission(MessageChannel.SMS, number))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WhatsApp addresses are held to the same E.164 rule as SMS")
    void whatsappUsesTheSameRule() {
        assertThatCode(() -> submission(MessageChannel.WHATSAPP, "+919451740121"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> submission(MessageChannel.WHATSAPP, "9451740121"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- the one that matters most ----

    @Test
    @DisplayName("an email local part is passed through with its case intact")
    void anEmailLocalPartKeepsItsCase() {
        // The contract calls lower-casing the local part the most commonly shipped normalisation
        // bug, and notes it misdelivers rather than mis-sorting: the platform folds the DOMAIN only,
        // and the local part is case-sensitive per RFC 5321. This asserts the absence of a
        // transformation, which is the only way to stop a later "tidy-up" reintroducing it.
        NotificationSubmission s = submission(MessageChannel.EMAIL, "Nikhil.Sharma@Example.COM");

        assertThat(s.address())
                .as("the local part must survive exactly as the user gave it")
                .isEqualTo("Nikhil.Sharma@Example.COM");
    }

    @Test
    @DisplayName("no address of any channel is rewritten on the way through")
    void noAddressIsRewritten() {
        // Validation refuses; it never normalises. A validator that helpfully tidies is how the
        // local-part bug gets shipped.
        assertThat(submission(MessageChannel.SMS, "+919451740121").address())
                .isEqualTo("+919451740121");
        assertThat(submission(MessageChannel.EMAIL, "N.Sharma+Funds@Example.co.IN").address())
                .isEqualTo("N.Sharma+Funds@Example.co.IN");
    }

    @ParameterizedTest(name = "{0} is accepted")
    @ValueSource(strings = {"a@b.co", "nikhil.sharma@example.com", "n+tag@sub.example.co.in"})
    @DisplayName("an ordinary email address is accepted")
    void anOrdinaryEmailIsAccepted(String address) {
        assertThatCode(() -> submission(MessageChannel.EMAIL, address)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"nikhil", "nikhil@", "@example.com", "nikhil@example", "a b@example.com",
            "two@@example.com"})
    @DisplayName("a value that was never an address is refused")
    void aNonAddressIsRefused(String address) {
        assertThatThrownBy(() -> submission(MessageChannel.EMAIL, address))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a blank address is refused, because there is no directory to fall back on")
    void aBlankAddressIsRefused() {
        assertThatThrownBy(() -> submission(MessageChannel.SMS, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
