package com.thinq.fms.movement.payout;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * This module's own reference for a withdrawal, distinct from the bank's (Rule C8).
 *
 * <p><b>Why it comes from a database sequence rather than a counter or a UUID.</b> Support quotes
 * this value and traders read it aloud, so it has to be short and unambiguous — which rules out a
 * UUID. It also has to be unique across every instance, which rules out anything held in memory:
 * two replicas with their own counters issue the same reference to different traders, and Rule C8's
 * whole point is that a reference identifies one movement.
 *
 * <p>The shape is {@code FMS-W-<yyyymmdd>-<n>}, inside {@code fms_reference}'s 32 characters. The
 * date is for the human reading it; the sequence value is what makes it unique, and the date is not
 * relied on for that.
 */
public class SequentialFmsReferenceGenerator implements PayoutOrchestrator.FmsReferenceGenerator {

    private final JdbcClient db;
    private final Clock clock;
    private final ZoneId zone;

    public SequentialFmsReferenceGenerator(JdbcClient db, Clock clock, ZoneId zone) {
        this.db = Objects.requireNonNull(db, "db");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public String next() {
        // nextval is atomic and never reuses a value, including across a rollback. A reference
        // consumed by a transaction that then failed is simply never used, which is the correct
        // trade: a gap in the series costs nothing, a repeat costs a support investigation.
        long sequence = this.db.sql("SELECT nextval('fms_reference_seq')")
                .query(Long.class)
                .single();

        LocalDate today = LocalDate.now(this.clock.withZone(this.zone));
        return "FMS-W-%04d%02d%02d-%d".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(), sequence);
    }
}
