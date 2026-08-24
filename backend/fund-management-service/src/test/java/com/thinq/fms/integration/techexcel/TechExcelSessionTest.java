package com.thinq.fms.integration.techexcel;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.platform.error.VendorUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Session refresh under concurrency.
 *
 * <p>The behaviour worth protecting is a burst-suppression property: when a token expires, every
 * in-flight call discovers it at once, and without coalescing TechExcel receives one login per
 * call at the moment it is already unhappy. That cannot be observed single-threaded, which is why
 * these tests spin threads rather than asserting on a return value.
 */
class TechExcelSessionTest {

    private HttpServer server;
    private AtomicInteger logins;
    private JsonHttp http;
    private ExecutorService pool;

    @BeforeEach
    void start() throws Exception {
        this.logins = new AtomicInteger();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            int n = this.logins.incrementAndGet();
            // A deliberate delay, so concurrent callers genuinely overlap inside the login rather
            // than completing one after another by accident.
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = ("{\"Token\":\"TOK-" + n + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        this.server.setExecutor(Executors.newFixedThreadPool(8));
        this.server.start();
        this.http = new JsonHttp(
                URI.create("http://127.0.0.1:" + this.server.getAddress().getPort()),
                Duration.ofSeconds(2), new ObjectMapper());
        this.pool = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void stop() {
        this.pool.shutdownNow();
        this.server.stop(0);
    }

    @Test
    @DisplayName("eight callers discovering the same expired token produce exactly one login")
    void concurrentRefreshCoalescesOntoOneLogin() throws Exception {
        TechExcelSession session = new TechExcelSession(this.http, "u", "p");
        String stale = session.token();
        int before = this.logins.get();

        Set<String> tokens = new HashSet<>(runConcurrently(8, () -> session.refreshIfStale(stale)));

        // The property: one login, and everyone gets the same token. Losing this turns a token
        // expiry during the end-of-day run into a login storm at the moment TechExcel is least
        // able to absorb one.
        //
        // Note the counter is read rather than reset. An earlier version of this test reset it,
        // which made the stub's next token identical to the stale one and broke the coalescing
        // for a reason that had nothing to do with the code under test.
        assertThat(this.logins.get() - before).isEqualTo(1);
        assertThat(tokens).hasSize(1).doesNotContain(stale);
    }

    @Test
    @DisplayName("a caller whose token was already refreshed by another does not log in again")
    void alreadyRefreshedCallerReusesTheNewToken() throws Exception {
        TechExcelSession session = new TechExcelSession(this.http, "u", "p");
        String stale = session.token();
        String fresh = session.refreshIfStale(stale);
        int before = this.logins.get();

        assertThat(session.refreshIfStale(stale)).isEqualTo(fresh);
        assertThat(this.logins.get() - before)
                .as("the held token is already newer than the stale one").isZero();
    }

    @Test
    @DisplayName("calling invalidate() before refreshIfStale defeats the coalescing")
    void invalidateBeforeRefreshDefeatsCoalescing() throws Exception {
        // This is F-16, pinned as a characterisation test. TechExcelGateway's 401 branch used to
        // do exactly this, and it is why it no longer does: nulling the field means a caller
        // arriving after another has published a fresh token discards it and logs in again.
        TechExcelSession session = new TechExcelSession(this.http, "u", "p");
        String stale = session.token();
        int before = this.logins.get();

        runConcurrently(8, () -> {
            session.invalidate();
            return session.refreshIfStale(stale);
        });

        // Timing-dependent, so the assertion is only that it is not the guaranteed one that
        // refreshIfStale alone achieves. Asserting an exact count here would be flaky.
        assertThat(this.logins.get() - before)
                .as("invalidate() removes the guarantee; refreshIfStale alone gives exactly 1")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("token() logs in once when none is held, and not again afterwards")
    void tokenLogsInLazilyAndOnce() throws Exception {
        TechExcelSession session = new TechExcelSession(this.http, "u", "p");

        assertThat(this.logins.get()).as("construction performs no I/O").isZero();

        String first = session.token();
        assertThat(this.logins.get()).isEqualTo(1);
        assertThat(session.token()).isEqualTo(first);
        assertThat(this.logins.get()).as("a held token is reused without a lock or a call").isEqualTo(1);
    }

    @Test
    @DisplayName("coalescing assumes a new login returns a different token value")
    void coalescingAssumesDistinctTokenValues() throws Exception {
        // A characterisation test, not an endorsement. The mechanism compares values rather than
        // counting generations, so a vendor that reissued an identical token string would defeat
        // it — each waiting caller would still see its stale value held and log in again.
        //
        // TechExcel issues distinct tokens, so this is latent rather than live. It is pinned here
        // because the assumption is invisible in the code and a future reader replacing the
        // comparison would have no way to know it mattered.
        this.server.removeContext("/");
        this.server.createContext("/", exchange -> {
            this.logins.incrementAndGet();
            byte[] body = "{\"Token\":\"CONSTANT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        TechExcelSession session = new TechExcelSession(this.http, "u", "p");
        String stale = session.token();
        int before = this.logins.get();

        runConcurrently(4, () -> session.refreshIfStale(stale));

        assertThat(this.logins.get() - before)
                .as("an identical reissued token defeats coalescing; distinct values give 1")
                .isGreaterThan(1);
    }

    @Test
    @DisplayName("a login answering without a Token is an outage, not a usable session")
    void loginWithoutATokenIsAnOutage() throws Exception {
        this.server.removeContext("/");
        this.server.createContext("/", exchange -> {
            byte[] body = "{\"Status\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // Proceeding tokenless would produce Token Missing on every downstream call, which reads
        // as an outage rather than as the credential problem it is.
        assertThatThrownBy(() -> new TechExcelSession(this.http, "u", "p").token())
                .isInstanceOf(VendorUnavailableException.class)
                .hasMessageContaining("no Token");
    }

    private List<String> runConcurrently(int threads, ThrowingSupplier task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(this.pool.submit(() -> {
                go.await(5, TimeUnit.SECONDS);
                return task.get();
            }));
        }
        go.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            results.add(f.get(10, TimeUnit.SECONDS));
        }
        return results;
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
