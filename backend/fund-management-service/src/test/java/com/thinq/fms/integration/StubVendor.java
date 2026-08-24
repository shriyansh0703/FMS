package com.thinq.fms.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real HTTP server standing in for a vendor.
 *
 * <p><b>Why a server and not a mock.</b> The defect this harness exists to prevent was a
 * NullPointerException on the first statement after a successful response — the single most common
 * outcome of the most-used method in the integration package, on which 94 tests were silent because
 * none of them ever called it. A mocked {@code JsonHttp} would not have caught it either: the bug
 * was in how the gateway read a response, not in how it made a request.
 *
 * <p>Uses the JDK's own {@code HttpServer}, so it adds no dependency. Binds to port 0 so tests can
 * run in parallel without collisions.
 */
public final class StubVendor implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, List<String>> responsesByPathFragment = new LinkedHashMap<>();
    private final Map<String, List<Integer>> statusesByPathFragment = new LinkedHashMap<>();
    private final Map<String, AtomicInteger> callCounts = new LinkedHashMap<>();
    private final List<String> requestBodies = new ArrayList<>();
    private volatile int statusToReturn = 200;

    public StubVendor() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the stub vendor", e);
        }
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    /**
     * Queue a response for any request whose path contains {@code pathFragment}.
     *
     * <p>Queued rather than fixed, so a test can script a sequence — an expired-token answer
     * followed by a success, for instance. The last queued response repeats once the queue is
     * exhausted, which keeps a test that only cares about the first call from having to enumerate
     * every subsequent one.
     */
    /**
     * Queue a response, taking whatever {@link #withStatus} is set.
     *
     * <p>Deliberately does <b>not</b> queue a status of its own: doing so silently overrode
     * {@code withStatus} for every existing caller, which turned six passing error tests green
     * against the wrong status.
     */
    public StubVendor respond(String pathFragment, String json) {
        this.responsesByPathFragment.computeIfAbsent(pathFragment, k -> new ArrayList<>()).add(json);
        return this;
    }

    /**
     * Queue a response with its own status.
     *
     * <p>Per-call rather than global, because some sequences only exist across statuses — a 500
     * followed by a 200 is the shape of an ambiguous submit being resolved by an idempotent
     * re-send, and a single global status cannot express it. {@link #withStatus} remains for the
     * simple case of every response sharing one.
     */
    public StubVendor respond(String pathFragment, int status, String json) {
        this.responsesByPathFragment.computeIfAbsent(pathFragment, k -> new ArrayList<>()).add(json);
        this.statusesByPathFragment.computeIfAbsent(pathFragment, k -> new ArrayList<>()).add(status);
        return this;
    }

    /** Force a status for every response, for exercising transport-level failures. */
    public StubVendor withStatus(int status) {
        this.statusToReturn = status;
        return this;
    }

    public int callsTo(String pathFragment) {
        AtomicInteger n = this.callCounts.get(pathFragment);
        return n == null ? 0 : n.get();
    }

    public List<String> requestBodies() {
        return List.copyOf(this.requestBodies);
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        this.requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        String body = "{}";
        int status = this.statusToReturn;
        for (Map.Entry<String, List<String>> e : this.responsesByPathFragment.entrySet()) {
            if (!path.contains(e.getKey())) {
                continue;
            }
            this.callCounts.computeIfAbsent(e.getKey(), k -> new AtomicInteger()).incrementAndGet();
            List<String> queued = e.getValue();
            List<Integer> statuses = this.statusesByPathFragment.get(e.getKey());
            body = queued.size() > 1 ? queued.remove(0) : queued.get(0);
            if (statuses != null && !statuses.isEmpty()) {
                status = statuses.size() > 1 ? statuses.remove(0) : statuses.get(0);
            }
            break;
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}
