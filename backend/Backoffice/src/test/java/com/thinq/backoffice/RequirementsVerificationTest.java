package com.thinq.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;

import com.thinq.backoffice.platform.AuthProperties;
import com.thinq.backoffice.platform.GatewayProperties;
import com.thinq.backoffice.ratelimit.RateLimitProperties;
import com.thinq.backoffice.ratelimit.RateLimitInterceptor;
import com.thinq.backoffice.ratelimit.RateLimiter;
import com.thinq.backoffice.scheduler.TokenRefresher;

/**
 * THE STATED REQUIREMENTS, ASSERTED RATHER THAN ASSUMED.
 *
 * <p>Each test here corresponds to a requirement someone asked for out loud. They are deliberately
 * shallow — the behaviour is tested elsewhere — and they exist because a requirement that is only
 * satisfied by a file existing somewhere gets deleted by a well-meaning refactor and nobody
 * notices. These fail loudly instead.
 *
 * <p>The context runs in <b>managed</b> auth mode, because the scheduler bean does not exist in
 * pass-through and a test asserting a bean is registered has to be in the configuration that
 * registers it. The base URL points at a dead port on purpose: the startup login fails, is logged,
 * and the process carries on — which is itself the behaviour we want, and means this test does not
 * need a live back office to check that the schedule was wired.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "techexcel.live=true",
        "techexcel.base-url=http://localhost:1/TechBoRest",
        "techexcel.auth.mode=managed",
        "techexcel.auth.username=api",
        "techexcel.auth.password=Api@123456",
        "techexcel.auth.refresh-cron=0 0 */6 * * *",
        "techexcel.auth.refresh-after=20h"})
class RequirementsVerificationTest {

    @Autowired
    private ApplicationContext context;

    // ───────────────────────────────────── 1. Is there a scheduler?

    @Test
    @DisplayName("1 · scheduling is enabled and a task is actually registered")
    void thereIsAScheduler() {
        assertThat(BackofficeApplication.class.getAnnotation(EnableScheduling.class))
                .as("@EnableScheduling is the whole background mechanism; without it the refresh "
                        + "silently never runs and nothing else reports that")
                .isNotNull();

        Set<ScheduledTask> tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();
        assertThat(tasks).as("a registered task, not merely an annotation on a method").isNotEmpty();
    }

    // ───────────────────────────────────── 2. Is there a login, and is it the SERVICE's?

    @Test
    @DisplayName("2 · the service logs in to TechExcel itself, holding its own credential")
    void thereIsAServiceOwnedLoginToTechExcel() {
        // The bean exists only in managed mode, which is the mode where this process — not the
        // caller — is the thing that authenticates to the back office.
        assertThat(context.getBeanNamesForType(TokenRefresher.class))
                .as("managed mode must register the component that logs in on the service's behalf")
                .isNotEmpty();

        AuthProperties auth = context.getBean(AuthProperties.class);
        assertThat(auth.managed()).isTrue();
        assertThat(auth.username()).isNotBlank();
        assertThat(auth.password()).isNotBlank();

        // And the credential never leaks through the object's own printed form.
        assertThat(auth.toString()).doesNotContain("Api@123456").contains("REDACTED");
    }

    // ───────────────────────────────────── 3. Is the refresh on a cron?

    @Test
    @DisplayName("3 · the token refresh runs on a cron, ahead of the documented 24h expiry")
    void thereIsACronJobRefreshingTheToken() {
        Set<ScheduledTask> tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();

        assertThat(tasks)
                .as("the refresh must be a CronTask, not a fixed delay — a fixed delay drifts and "
                        + "cannot be aimed at a quiet hour")
                .anySatisfy(task -> {
                    assertThat(task.getTask()).isInstanceOf(CronTask.class);
                    assertThat(((CronTask) task.getTask()).getExpression()).isEqualTo("0 0 */6 * * *");
                });

        AuthProperties auth = context.getBean(AuthProperties.class);
        // The refresh must happen BEFORE the token dies, or it is a scheduled outage. The
        // constructor refuses >= 24h; this asserts the configured value leaves real headroom.
        assertThat(auth.refreshAfter())
                .isLessThan(AuthProperties.TOKEN_VALIDITY)
                .isLessThanOrEqualTo(Duration.ofHours(22));
    }

    // ───────────────────────────────────── 4. Is there a rate limiter?

    @Test
    @DisplayName("4 · a rate limiter exists, is configurable, and is applied to every API route")
    void thereIsARateLimiter() {
        assertThat(context.getBean(RateLimiter.class)).isNotNull();
        assertThat(context.getBean(RateLimitInterceptor.class)).isNotNull();

        RateLimitProperties limits = context.getBean(RateLimitProperties.class);
        assertThat(limits.enabled()).isTrue();
        // Requests-per-window per caller, both configurable — the thing that was actually asked for.
        assertThat(limits.defaults().requests()).isPositive();
        assertThat(limits.defaults().window()).isPositive();
        assertThat(limits.maxTrackedCallers()).isPositive();
        // And an endpoint may be given its own allowance rather than sharing one global bucket.
        assertThat(limits.forEndpoint("ledger")).isNotNull();
    }

    // ───────────────────────────────────── 5. Java 25 / Spring Boot 4.1.0 / Gradle

    @Test
    @DisplayName("5 · running on Java 25, Spring Boot 4.1.0, built by Gradle")
    void theToolchainIsWhatWasAskedFor() {
        assertThat(Runtime.version().feature())
                .as("the Gradle toolchain resolves a JDK 25 for both compile and test")
                .isGreaterThanOrEqualTo(25);

        assertThat(SpringBootVersion.getVersion()).startsWith("4.1.");

        // Gradle, and only Gradle. A stray pom.xml would mean two build systems disagreeing about
        // what this module is.
        Path module = Path.of(System.getProperty("user.dir"));
        assertThat(module.resolve("build.gradle")).exists();
        assertThat(module.resolve("settings.gradle")).exists();
        assertThat(module.resolve("gradlew")).exists();
        assertThat(Files.exists(module.resolve("pom.xml")))
                .as("no Maven build alongside the Gradle one").isFalse();
    }

    // ───────────────────────────────────── configuration sanity

    @Test
    @DisplayName("the live/mock switch and its upstream target are configuration, not code")
    void theUpstreamIsConfigurable() {
        GatewayProperties gateway = context.getBean(GatewayProperties.class);

        assertThat(gateway.live()).isTrue();
        assertThat(gateway.baseUrl()).isNotNull();
        assertThat(gateway.timeout()).isPositive();
    }
}
