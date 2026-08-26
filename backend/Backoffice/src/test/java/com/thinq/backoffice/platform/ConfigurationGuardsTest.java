package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * THE STARTUP GUARDS, FIRED.
 *
 * <p>Each assertion here corresponds to a way this service can be misconfigured into being
 * dangerous rather than broken — the failure mode where the process boots, reports healthy, and
 * does the wrong thing quietly. A guard that has never been fired is a guard nobody knows works.
 *
 * <p>No Spring context: these are records with validating constructors, so the test is a
 * constructor call.
 */
class ConfigurationGuardsTest {

    private static final URI SOMEWHERE = URI.create("http://backoffice.example:8555/TechBoRest");

    // ------------------------------------------------------------------ GatewayProperties

    @Test
    void liveWithNowhereToGoRefusesToStart() {
        // The alternative is a process that boots, answers every call with nothing, and looks like
        // a back-office outage rather than the typo it is.
        assertThatThrownBy(() -> new GatewayProperties(true, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start");

        assertThatThrownBy(() -> new GatewayProperties(true, URI.create(""), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mockModeNeedsNoBaseUrl() {
        // The default configuration must boot with the properties file absent entirely.
        assertThatCode(() -> new GatewayProperties(false, null, null)).doesNotThrowAnyException();
    }

    @Test
    void aTrailingSlashOnTheBaseUrlIsTrimmed() {
        // Paths are appended with a leading slash, so an untrimmed base URL produces "//api/entry"
        // — which some servers route and some reject, making the bug intermittent.
        GatewayProperties props = new GatewayProperties(true,
                URI.create("http://backoffice.example:8555/TechBoRest/"), null);

        assertThat(props.baseUrl()).hasToString("http://backoffice.example:8555/TechBoRest");
    }

    @Test
    void anAbsentTimeoutFallsBackToThirtySeconds() {
        // The vendor's own ledger capture took 3.1s, so an unset timeout must not default to
        // something that would cut it off.
        assertThat(new GatewayProperties(true, SOMEWHERE, null).timeout())
                .isEqualTo(Duration.ofSeconds(30));
    }

    // --------------------------------------------------------------------- AuthProperties

    @Test
    void anUnknownAuthModeIsRefused() {
        assertThatThrownBy(() -> new AuthProperties("sortOfManaged", null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passthrough");
    }

    @Test
    void theDefaultIsPassthroughAndHoldsNoCredential() {
        AuthProperties auth = new AuthProperties(null, null, null, null, null);

        assertThat(auth.managed()).isFalse();
        assertThat(auth.mode()).isEqualTo(AuthProperties.PASSTHROUGH);
        assertThat(auth.refreshCron()).isEqualTo("0 0 * * * *");
        assertThat(auth.refreshAfter()).isEqualTo(Duration.ofHours(20));
    }

    @Test
    void modeIsReadCaseInsensitively() {
        assertThat(new AuthProperties("  MANAGED ", "api", "pw", null, null).managed()).isTrue();
    }

    @Test
    void managedModeWithoutACredentialRefusesToStart() {
        // Booting anyway means attaching nothing and every call returning Token Missing, which
        // reads as TechExcel's fault rather than ours.
        assertThatThrownBy(() -> new AuthProperties("managed", null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TECHEXCEL_AUTH_USERNAME");

        assertThatThrownBy(() -> new AuthProperties("managed", "api", "  ", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRefreshThatWouldAlwaysRunTooLateRefusesToStart() {
        // The token dies at 24h. Replacing it only once it is already 24h old is not a refresh
        // policy, it is a scheduled outage.
        assertThatThrownBy(() -> new AuthProperties("managed", "api", "pw", null, Duration.ofHours(24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("under the 24h token validity");
    }

    @Test
    void theCredentialNeverReachesAToString() {
        // A record's generated toString would print the password in full the first time anything
        // logged this object — a context dump, a bean-creation failure, a stack trace.
        String printed = new AuthProperties("managed", "api", "Api@123456", null, null).toString();

        assertThat(printed).doesNotContain("Api@123456").contains("REDACTED");
    }
}
