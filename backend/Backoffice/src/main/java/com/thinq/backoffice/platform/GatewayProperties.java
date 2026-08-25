package com.thinq.backoffice.platform;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The switch this whole service exists for, bound from {@code techexcel.*}.
 *
 * <p>Loaded from {@code properties/techexcel.properties} (imported by application.yml, and
 * OUTSIDE the jar so it can be edited per environment without a rebuild), overridable by
 * environment variable and system property in the usual Spring order:
 *
 * <pre>
 *   TECHEXCEL_LIVE=true TECHEXCEL_BASE_URL=http://host:8555/TechBoRest ./gradlew bootRun
 *   java -Dtechexcel.live=true -jar build/libs/fms-backoffice-0.1.0.jar
 * </pre>
 *
 * <p>THE DEFAULT IS MOCK. Defaulting the other way is how a laptop ends up posting into a
 * broker's back office.
 *
 * @param live    false serves the built-in mock; true proxies to the real back office.
 * @param baseUrl the real back office, INCLUDING its {@code /TechBoRest} prefix. Live only.
 * @param timeout upstream read timeout. Live only.
 */
@ConfigurationProperties(prefix = "techexcel")
public record GatewayProperties(boolean live, URI baseUrl, Duration timeout) {

    public GatewayProperties {
        // Live with nowhere to go must fail at startup, not on every call. A gateway that boots
        // and then answers nothing is harder to diagnose than one that refuses to boot and says why.
        if (live && (baseUrl == null || baseUrl.toString().isBlank())) {
            throw new IllegalStateException(
                    "techexcel.live=true but techexcel.base-url is empty. Refusing to start: "
                            + "a live gateway with nowhere to go would silently answer nothing.");
        }
        if (baseUrl != null) {
            String raw = baseUrl.toString();
            if (raw.endsWith("/")) {
                baseUrl = URI.create(raw.substring(0, raw.length() - 1));
            }
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
    }
}
