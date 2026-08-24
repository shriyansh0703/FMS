package com.thinq.fms.persistence;

import com.thinq.fms.movement.payout.SequentialFmsReferenceGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule C8's reference, against the real sequence. */
class SequentialFmsReferenceGeneratorTest extends PostgresTestSupport {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-22T09:00:00Z"), ZoneOffset.UTC);

    private SequentialFmsReferenceGenerator generator;

    @BeforeEach
    void setUp() {
        this.generator = new SequentialFmsReferenceGenerator(db, CLOCK, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("the reference is short enough for the column and for a person to read out")
    void theReferenceFitsAndReads() {
        String reference = this.generator.next();

        assertThat(reference).startsWith("FMS-W-20260822-");
        assertThat(reference.length())
                .as("fms_reference is VARCHAR(32); a reference that does not fit fails at insert")
                .isLessThanOrEqualTo(32);
    }

    @Test
    @DisplayName("every reference is distinct")
    void everyReferenceIsDistinct() {
        Set<String> issued = IntStream.range(0, 200)
                .mapToObj(i -> this.generator.next())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(issued).hasSize(200);
    }

    @Test
    @DisplayName("concurrent callers never receive the same reference")
    void concurrentCallersNeverCollide() throws Exception {
        // The reason this comes from a sequence rather than an in-memory counter. Two replicas with
        // their own counters issue the same reference to different traders, and a reference that
        // identifies two movements is what Rule C8 exists to prevent.
        int threads = 16, each = 25;
        var pool = Executors.newFixedThreadPool(threads);
        var startLine = new CountDownLatch(1);

        try {
            List<Future<List<String>>> running = IntStream.range(0, threads)
                    .mapToObj(t -> pool.submit(() -> {
                        startLine.await();
                        return IntStream.range(0, each).mapToObj(i -> this.generator.next()).toList();
                    }))
                    .toList();
            startLine.countDown();

            Set<String> all = new java.util.HashSet<>();
            for (Future<List<String>> f : running) {
                all.addAll(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(all)
                    .as("every one of %d concurrent references must be unique", threads * each)
                    .hasSize(threads * each);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the reference is never equal to a bank reference shape")
    void theReferenceIsDistinguishableFromABankReference() {
        // Rule C8: ours and the bank's are different values and must be tellable apart on sight,
        // because a trader given the wrong one goes to their bank with a value it has never seen.
        assertThat(this.generator.next()).startsWith("FMS-W-").doesNotStartWith("UTR");
    }
}
