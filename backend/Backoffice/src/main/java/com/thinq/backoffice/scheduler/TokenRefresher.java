package com.thinq.backoffice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.thinq.backoffice.platform.AuthProperties;
import com.thinq.backoffice.platform.GatewayProperties;

/**
 * Keeps a TechExcel token alive so a headless caller never meets an expired one.
 *
 * <p>The vendor's Login document is explicit: a token is valid <b>24 hours from the time it was
 * generated</b>, and every other document carries a {@code Token Expired} row reading "Token
 * Invalid After 24 Hours". Waiting for that to happen and then reacting means somebody's overnight
 * batch is the thing that discovers it, so this replaces the token well before — by default once
 * it is 20 hours old, leaving four hours of headroom for a refresh that fails and has to be
 * retried.
 *
 * <p><b>WHY A CRON AND AN AGE CHECK, NOT A TIMER ARMED FOR THE EXPIRY.</b> The cron only decides
 * how often to <i>look</i>; the age of the held token decides whether to <i>act</i>. A missed tick,
 * a clock change, a restart, or a process asleep in a closed laptop then costs nothing — the next
 * tick, whenever it lands, sees the age and refreshes. A single timer set to "expiry minus an hour"
 * has none of those recoveries, and fails silently in every one of those cases.
 *
 * <p>BOTH KNOBS ARE CONFIGURATION: {@code techexcel.auth.refresh-cron} (how often to look) and
 * {@code techexcel.auth.refresh-after} (how old is too old). Startup refuses a refresh-after of 24h
 * or more, because such a refresh always runs too late to be worth having.
 *
 * <p>The token lives in memory only. It is never written to disk, never returned by an endpoint,
 * and never logged — not even truncated, since a prefix of a bearer token is still credential
 * material.
 *
 * <p>THIS BEAN ONLY EXISTS IN MANAGED MODE. In the default pass-through the gateway holds no
 * credential at all, and there is nothing to refresh.
 */
@Component
@ConditionalOnProperty(prefix = "techexcel.auth", name = "mode", havingValue = AuthProperties.MANAGED)
public class TokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(TokenRefresher.class);

    private record Held(String token, Instant issuedAt) { }

    private final AtomicReference<Held> held = new AtomicReference<>();
    private final GatewayProperties gateway;
    private final AuthProperties auth;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    TokenRefresher(GatewayProperties gateway, AuthProperties auth,
                   RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.auth = auth;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /** The current token, if we hold one. Empty until the first successful login. */
    public Optional<String> current() {
        Held now = held.get();
        return now == null ? Optional.empty() : Optional.of(now.token());
    }

    /**
     * Log in once at startup so the first real request does not pay for it — and so a wrong
     * credential is discovered now, in the startup log, rather than by whoever calls first.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void primeOnStartup() {
        refreshIfStale();
    }

    /** The scheduled check. Cron decides how often to look; age decides whether to act. */
    @Scheduled(cron = "${techexcel.auth.refresh-cron:0 0 * * * *}")
    public void scheduledCheck() {
        refreshIfStale();
    }

    /**
     * Replace the token if we hold none, or the one we hold is old enough.
     *
     * <p>A failed refresh deliberately KEEPS the existing token. It may still have hours left, and
     * throwing away a working credential because one HTTP call failed would turn a transient
     * upstream blip into an outage.
     */
    public synchronized void refreshIfStale() {
        Held now = held.get();
        if (now != null && age(now).compareTo(auth.refreshAfter()) < 0) {
            return;
        }
        try {
            String token = login();
            held.set(new Held(token, Instant.now()));
            log.info("techexcel: token refreshed, valid for a further {}h",
                    AuthProperties.TOKEN_VALIDITY.toHours());
        } catch (RuntimeException e) {
            // Name the exception class, never the credential and never the token.
            if (now == null) {
                log.error("techexcel: could not obtain a token ({}). Calls without their own "
                        + "Authorization header will be rejected upstream.",
                        e.getClass().getSimpleName());
            } else {
                log.warn("techexcel: token refresh failed ({}); keeping the existing token, {}h "
                        + "old. Will retry on the next tick.",
                        e.getClass().getSimpleName(), age(now).toHours());
            }
        }
    }

    private Duration age(Held h) {
        return Duration.between(h.issuedAt(), Instant.now());
    }

    /**
     * POST /api/login upstream. The response is a bare JSON string, so this unwraps it rather than
     * expecting an object.
     */
    private String login() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", auth.username());
        body.put("password", auth.password());

        String raw = restClient.post()
                .uri(gateway.baseUrl() + "/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .body(String.class);

        JsonNode node = objectMapper.readTree(raw == null ? "" : raw);
        if (node.isString()) {
            return node.stringValue();
        }
        // Some deployments answer with the object the document's response table describes rather
        // than the bare string its sample shows. Accept both; refusing one of the vendor's own two
        // shapes would be an outage caused by reading the PDF too literally.
        JsonNode token = node.get("Token");
        if (token != null && token.isString() && !token.stringValue().isBlank()) {
            return token.stringValue();
        }
        // A rejection arrives as the envelope, with HTTP 200 like everything else here. Surface its
        // Error Code, never the body we sent.
        throw new IllegalStateException(
                "upstream login refused: " + node.path("Error Code").asString("unknown"));
    }
}
