package com.thinq.backoffice.platform;

import java.time.Duration;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.net.http.HttpClient;

import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.thinq.backoffice.ratelimit.RateLimitProperties;

/**
 * The gateway's wiring, and the one switch behind all of it.
 *
 * <pre>
 *   techexcel.live=false  ->  serve the built-in mock. Nothing leaves this host.
 *   techexcel.live=true   ->  proxy every call to the real back office, verbatim,
 *                             and return its answer unchanged.
 * </pre>
 *
 * <p>The point of the switch is that CALLERS NEVER CHANGE: same base URL, same paths, same bearer
 * flow, same envelope, same field names. Only the far end moves. If a caller can tell the two apart
 * from a response body, the mock is wrong.
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, AuthProperties.class,
        RateLimitProperties.class})
public class TechExcelConfig {

    /**
     * Used in live mode to relay calls upstream, and by the scheduler to log in.
     *
     * <p>The read timeout is {@code techexcel.timeout} rather than the JDK default of none: the
     * vendor's own Ledger capture took 3.1 seconds, so a call that is merely slow must not be
     * mistaken for one that will never answer, and a call that will never answer must not hold a
     * request thread forever.
     */
    @Bean
    RestClient.Builder techexcelRestClientBuilder(GatewayProperties props) {
        // TWO DEADLINES, because they fail differently and send an operator to different places:
        // connect means the host is unreachable, read means it is reachable and slow.
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.timeout());
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Loud, because "which TechExcel am I hitting" is the one thing an operator must never have to
     * guess.
     */
    @Bean
    ApplicationListener<ApplicationReadyEvent> techexcelBanner(GatewayProperties props,
                                                              AuthProperties auth,
                                                              RateLimitProperties limits,
                                                              Environment env) {
        return event -> {
            String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
            System.out.println(props.live()
                    ? """
                      ==========================================================
                       LIVE  -> proxying to %s
                       Requests leave this host and reach the real back office.
                      =========================================================="""
                            .formatted(props.baseUrl())
                    : """
                      ----------------------------------------------------------
                       MOCK  -> generated data, nothing leaves this host
                       no credential needed, and none is held
                      ----------------------------------------------------------""");
            if (auth.managed()) {
                // Say it out loud: this process is holding a real credential.
                System.out.println(" AUTH  -> managed. Logs in as '" + auth.username()
                        + "' and refreshes its own token (" + auth.refreshCron()
                        + ", replace after " + auth.refreshAfter() + " of the documented 24h).");
            }
            System.out.println(" LIMIT -> " + (limits.enabled()
                    ? limits.defaults().requests() + " requests / " + limits.defaults().window()
                            + " per caller per endpoint"
                    : "DISABLED (backoffice.ratelimit.enabled=false)"));
            System.out.println("listening on http://localhost:" + port);
        };
    }
}
