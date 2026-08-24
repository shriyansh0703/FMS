package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.platform.error.VendorUnavailableException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TechExcel's session token, obtained from {@code POST /TechBoRest/api/login} and presented on
 * every subsequent call.
 *
 * <p><b>Why this is a class and not a field.</b> The token expires, and TechExcel signals that
 * with {@code Token Validation Expired} on the <i>next</i> call rather than on a schedule. So
 * refresh is demand-driven, and a demand-driven refresh shared by concurrent callers needs one
 * of them to do the work while the others wait — otherwise a token expiring during the
 * end-of-day run triggers one login per in-flight call, and TechExcel sees a burst of logins at
 * the moment the run depends on it most.
 *
 * <p>Reads of a valid token take no lock. The lock is held only across a login.
 */
public final class TechExcelSession {

    private static final String LOGIN_PATH = "/TechBoRest/api/login";

    private final JsonHttp http;
    private final String loginName;
    private final String password;
    private final ReentrantLock loginLock = new ReentrantLock();

    /** Volatile so a token published by the refreshing thread is visible to the waiting ones. */
    private volatile String token;

    public TechExcelSession(JsonHttp http, String loginName, String password) {
        this.http = Objects.requireNonNull(http, "http");
        this.loginName = Objects.requireNonNull(loginName, "loginName");
        this.password = Objects.requireNonNull(password, "password");
    }

    /** The current token, logging in if none is held. */
    public String token() throws Exception {
        String current = this.token;
        return current != null ? current : refreshIfStale(null);
    }

    /**
     * Obtain a token, coalescing concurrent callers onto one login.
     *
     * <p>The caller passes the token it just found expired. If the held token is something else,
     * another thread has already refreshed and this caller takes that result instead of
     * triggering a second login. The check is made twice — once before taking the lock, once
     * after — because the winner publishes its token while the losers are still queued.
     *
     * @param staleToken the token that failed, or null when the caller holds none
     */
    public String refreshIfStale(String staleToken) throws Exception {
        String current = this.token;
        if (isUsable(current, staleToken)) {
            return current;
        }

        this.loginLock.lock();
        try {
            current = this.token;
            if (isUsable(current, staleToken)) {
                return current;
            }
            this.token = login();
            return this.token;
        } finally {
            this.loginLock.unlock();
        }
    }

    /** A held token is usable when it exists and is not the one the caller just saw fail. */
    private static boolean isUsable(String held, String staleToken) {
        return held != null && !held.equals(staleToken);
    }

    private String login() throws Exception {
        JsonNode response = this.http.post(LOGIN_PATH,
                Map.of("name", this.loginName, "password", this.password), Map.of());

        JsonNode tokenNode = response.get("Token");
        if (tokenNode == null || tokenNode.asString().isBlank()) {
            // A login that answers without a token is not a session this system can use.
            // Proceeding tokenless would produce Token Missing on every downstream call, which
            // reads as an outage rather than as the credential problem it is.
            throw new VendorUnavailableException("techexcel", "TechExcel login returned no Token field");
        }
        return tokenNode.asString();
    }

    /** Discard the held token. The next call logs in. */
    public void invalidate() {
        this.token = null;
    }
}
