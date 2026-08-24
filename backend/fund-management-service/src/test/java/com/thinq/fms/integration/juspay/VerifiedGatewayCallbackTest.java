package com.thinq.fms.integration.juspay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stage 11, MEDIUM-2 — the obligation made impossible to overlook. */
class VerifiedGatewayCallbackTest {

    @Test
    @DisplayName("a verification must name what it checked")
    void aVerificationMustNameWhatItChecked() {
        // "Verified" with nothing behind it is precisely the state this type exists to prevent, and
        // an unnamed verification cannot be audited afterwards.
        assertThatThrownBy(() -> VerifiedGatewayCallback.signatureVerified("  "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(VerifiedGatewayCallback.signatureVerified("HMAC-SHA256/key-3").scheme())
                .isEqualTo("HMAC-SHA256/key-3");
    }

    @Test
    @DisplayName("the escape hatch is named so it is obvious in a diff")
    void theEscapeHatchIsObvious() {
        // A reviewer seeing notFromAGatewayCallback in the callback endpoint knows at a glance that
        // the signature check is missing. That visibility is the whole design.
        VerifiedGatewayCallback internal =
                VerifiedGatewayCallback.notFromAGatewayCallback("operator replay");

        assertThat(internal.scheme()).startsWith("not-a-callback:");
        assertThat(internal.toString()).contains("not-a-callback");
    }

    @Test
    @DisplayName("the escape hatch still demands a reason")
    void theEscapeHatchStillDemandsAReason() {
        assertThatThrownBy(() -> VerifiedGatewayCallback.notFromAGatewayCallback(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
