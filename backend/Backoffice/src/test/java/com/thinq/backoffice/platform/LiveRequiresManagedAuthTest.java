package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import com.thinq.backoffice.BackofficeApplication;

/**
 * A LIVE GATEWAY WITH NO CREDENTIAL MUST NOT START.
 *
 * <p>This service is the only thing that logs in to TechExcel. Callers present nothing — there is
 * no login route for them to get a token from — so a process configured {@code live=true} while
 * holding no managed credential would send every upstream call unauthenticated and get
 * {@code Token Missing} back from all of them.
 *
 * <p>That failure is <b>indistinguishable from a back-office problem</b> at the point where
 * someone is reading logs at 3am. Refusing to boot moves the discovery to deploy time, where the
 * message can name the property that is wrong.
 *
 * <p>The context is started by hand rather than with {@code @SpringBootTest}, because the thing
 * under test is the startup failing — an annotation that fails to build a context fails the test
 * before the assertion runs.
 */
class LiveRequiresManagedAuthTest {

    private static SpringApplication application() {
        SpringApplication app = new SpringApplication(BackofficeApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        return app;
    }

    @Test
    void liveWithPassthroughAuthRefusesToStartAndSaysWhy() {
        assertThatThrownBy(() -> application().run(
                "--techexcel.live=true",
                "--techexcel.base-url=http://localhost:1/TechBoRest",
                "--techexcel.auth.mode=passthrough"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("techexcel.auth.mode=managed")
                // The message must name the cause, not just the symptom: somebody reading it
                // should not have to work out why a token is missing.
                .hasMessageContaining("callers have no way to present a token");
    }

    @Test
    void mockModeNeedsNoCredentialAtAll() {
        // The default configuration must start with no TechExcel account in existence — that is
        // what makes the whole service usable before access to the back office arrives.
        try (var context = application().run("--techexcel.live=false")) {
            assertThat(context.isRunning()).isTrue();
            assertThat(context.getBeanNamesForType(
                    com.thinq.backoffice.scheduler.TokenRefresher.class)).isEmpty();
        }
    }
}
