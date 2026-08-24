-- V24 — append-only movement history (REQ-405).
--
-- REQ-405 requires a movement's full timeline: every state it passed through, the time of
-- each, and the reason recorded at any refusal, failure or reversal. That cannot be
-- reconstructed from a current status column, so transitions are WRITTEN AS THEY HAPPEN.
-- This is a write-path decision, not a display one: a movement whose intermediate states
-- were never written cannot have its timeline shown afterwards.
--
-- Append-only. No UPDATE, no DELETE. A correction is a new row referencing what it corrects,
-- matching Rule L2's treatment of ledger entries so this system's own records follow the
-- same discipline as the entries it presents.

CREATE TABLE fms_movement_state_event (
    id             BIGSERIAL,
    movement_kind  VARCHAR(8)   NOT NULL,   -- PAYIN | PAYOUT
    movement_id    BIGINT       NOT NULL,
    from_state     VARCHAR(24),
    to_state       VARCHAR(24)  NOT NULL,
    reason_code    VARCHAR(48),
    reason_text    TEXT,
    actor          VARCHAR(32)  NOT NULL,   -- USER | SYSTEM | TECHEXCEL | RMS | GATEWAY
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fms_mse_kind_vocabulary  CHECK (movement_kind IN ('PAYIN', 'PAYOUT')),
    CONSTRAINT fms_mse_actor_vocabulary CHECK (actor IN ('USER', 'SYSTEM', 'TECHEXCEL', 'RMS', 'GATEWAY')),

    -- from_state and to_state are deliberately NOT constrained. They span both movement
    -- kinds, and while §7.5 fixes the payout vocabulary, no artifact yet assigns codes to
    -- the payin states or to Rule A9a's six outcomes. Constraining them here would mean
    -- inventing that vocabulary in a migration, which is the wrong place to decide it.
    -- Close this when the payin lifecycle lands (TASK-29) and the codes exist to name.

    -- Postgres requires the partition key in any unique constraint, which is why the
    -- primary key is composite rather than the bare id.
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions make retention a partition drop rather than a delete sweep over a live
-- table. Deliberately partitioned where the other tables are not: this one is append-only,
-- queried by recent window, and retention-bounded — the three properties that justify it.
CREATE TABLE fms_movement_state_event_2026_08 PARTITION OF fms_movement_state_event
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE fms_movement_state_event_2026_09 PARTITION OF fms_movement_state_event
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE fms_movement_state_event_2026_10 PARTITION OF fms_movement_state_event
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- A row landing outside every declared partition would otherwise fail the insert, which on
-- an audit table means losing the record of a state change that did happen. This catches it
-- instead, and its non-emptiness is an alert: it means partition creation has fallen behind.
-- OPERATIONAL NOTE — the DEFAULT partition is a safety net with a cost.
--
-- Keep it: losing an audit row because partition creation fell behind is worse than the problem
-- below. But know the problem. Once rows land in a DEFAULT partition, ATTACH PARTITION for a
-- range covering those rows FAILS — PostgreSQL scans the default partition and refuses if any row
-- would belong in the incoming one. Recovery means moving those rows out first, on a live table.
--
-- So a non-empty default partition is not merely an alert that creation has fallen behind; it is
-- the point at which catching up stops being free. Monthly partition creation must be automated
-- before first production deploy, not after. The declared partitions below run out after
-- 2026-10-31.
CREATE TABLE fms_movement_state_event_overflow PARTITION OF fms_movement_state_event DEFAULT;

-- One movement's timeline — the only query this table serves.
CREATE INDEX fms_mse_movement
    ON fms_movement_state_event (movement_kind, movement_id, occurred_at);
