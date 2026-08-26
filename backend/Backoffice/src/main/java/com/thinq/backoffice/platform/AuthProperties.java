package com.thinq.backoffice.platform;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How this gateway gets a token from TechExcel, bound from {@code techexcel.auth.*}.
 *
 * <p><b>passthrough</b> — the gateway holds no credential at all. MOCK MODE ONLY: this service
 * exposes no login route, so a caller has no way to obtain a TechExcel token and a live gateway in
 * this mode would send every request unauthenticated. {@code VendorGateway} refuses to start on
 * that combination.
 *
 * <p><b>managed</b> — REQUIRED WHEN LIVE. The gateway logs in itself, keeps the token in memory,
 * and refreshes it on a cron before the documented 24 hours are up. Every upstream call carries
 * that token. This is the mode the service runs in against a real back office, and it is the one
 * that costs something: the process holds a real TechExcel credential and applies it to any
 * request that reaches it, so access to this service must be restricted at the network layer.
 *
 * @param mode         passthrough or managed.
 * @param username     managed mode only. From the environment, never a committed file.
 * @param password     managed mode only. From the environment, never a committed file.
 * @param refreshCron  when to CHECK whether the token needs replacing. Six-field Spring cron.
 * @param refreshAfter replace a token once it is older than this. Well under the documented 24-hour
 *                     validity, so a slow or failed refresh still has hours of headroom.
 */
@ConfigurationProperties(prefix = "techexcel.auth")
public record AuthProperties(String mode, String username, String password,
                             String refreshCron, Duration refreshAfter) {

    public static final String MANAGED = "managed";
    public static final String PASSTHROUGH = "passthrough";

    /** The vendor's documented validity, from the Login PDF: "valid 24 hours from time generate". */
    public static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

    public AuthProperties {
        mode = (mode == null || mode.isBlank()) ? PASSTHROUGH : mode.trim().toLowerCase();
        if (!MANAGED.equals(mode) && !PASSTHROUGH.equals(mode)) {
            throw new IllegalStateException(
                    "techexcel.auth.mode must be 'passthrough' or 'managed', not '" + mode + "'.");
        }
        refreshCron = (refreshCron == null || refreshCron.isBlank()) ? "0 0 * * * *" : refreshCron;
        refreshAfter = refreshAfter == null ? Duration.ofHours(20) : refreshAfter;

        if (MANAGED.equals(mode)) {
            // Fail at startup, not on the first call at 3am. A gateway that boots in managed mode
            // with no credential attaches nothing, and every request comes back Token Missing —
            // which reads as TechExcel's fault rather than ours.
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "techexcel.auth.mode=managed needs techexcel.auth.username and .password. "
                                + "Supply them as TECHEXCEL_AUTH_USERNAME and TECHEXCEL_AUTH_PASSWORD "
                                + "in the environment — never in properties/techexcel.properties, "
                                + "which is committed.");
            }
            if (refreshAfter.compareTo(TOKEN_VALIDITY) >= 0) {
                throw new IllegalStateException(
                        "techexcel.auth.refresh-after must be under the 24h token validity, or the "
                                + "refresh always runs too late. Got " + refreshAfter + ".");
            }
        }
    }

    public boolean managed() {
        return MANAGED.equals(mode);
    }

    /**
     * Never let a credential reach a log line, an error chain or a stack trace. A record's
     * generated toString would print the password in full the first time anything logged this.
     */
    @Override
    public String toString() {
        return "AuthProperties[mode=" + mode
                + ", username=" + (username == null ? "unset" : "set")
                + ", password=" + (password == null ? "unset" : "REDACTED")
                + ", refreshCron=" + refreshCron
                + ", refreshAfter=" + refreshAfter + "]";
    }
}
