-- V26 — message delivery log (REQ-623, REQ-625).
--
-- One row per submission per channel. Rule C1's "SMS and email at minimum" is two
-- submissions with two request_ids that fail independently, because the Communication
-- Service sends exactly one channel per call — so two rows, not one row with two statuses.

CREATE TABLE fms_message_delivery (
    id                BIGSERIAL,
    account_id        VARCHAR(20)  NOT NULL,
    outbox_id         BIGINT       NOT NULL,   -- the intent id, and the service's request_id
    template_key      VARCHAR(64)  NOT NULL,

    -- The exact version the service resolved. This is how REQ-625 — a delivered message
    -- must always be reconstructable — is satisfied WITHOUT this system versioning
    -- templates itself.
    template_id       VARCHAR(64),

    channel           VARCHAR(16)  NOT NULL,
    notification_id   VARCHAR(64),

    -- The service's ten-value vocabulary. Note that on SMS, 'delivered' means the vendor
    -- accepted the message, not that a handset received it — the aggregator publishes no
    -- delivery reports at all. No decision may rest on it, including whether a regulatory
    -- intimation obligation was met.
    status            VARCHAR(24)  NOT NULL,

    -- REQ-623 requires the suppressed messages logged too, not only the sent ones.
    suppression_code  VARCHAR(48),

    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,

    -- The Communication Service's ten-value status vocabulary, verbatim and lowercase as
    -- the service reports it (caller-integration.md §8). Closed here so a vocabulary drift
    -- on their side surfaces as a failed write naming the value, rather than as a status
    -- nothing downstream knows how to render.
    CONSTRAINT fms_msg_status_vocabulary
        CHECK (status IN ('accepted', 'claimed', 'dispatched', 'failed', 'sent',
                          'delivered', 'bounced', 'rejected', 'dropped', 'expired')),

    -- 'whatsapp' is absent deliberately. The grant is unconfirmed (TASK-02) and admitting a
    -- channel this system may not be permitted to send on would let a row exist for a
    -- message that can never be submitted. Add it when the grant is confirmed.
    CONSTRAINT fms_msg_channel_vocabulary
        CHECK (channel IN ('sms', 'email')),

    PRIMARY KEY (id, submitted_at)
) PARTITION BY RANGE (submitted_at);

CREATE TABLE fms_message_delivery_2026_08 PARTITION OF fms_message_delivery
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE fms_message_delivery_2026_09 PARTITION OF fms_message_delivery
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE fms_message_delivery_2026_10 PARTITION OF fms_message_delivery
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
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
CREATE TABLE fms_message_delivery_overflow PARTITION OF fms_message_delivery DEFAULT;

-- Lookup, NOT uniqueness — and the distinction is why this is not a UNIQUE index.
--
-- The intent was one row per (outbox_id, channel): §7.9 resubmits a terminally failed message
-- under a NEW request_id, which is a new intent id, so a second row under the same outbox_id and
-- channel would be a bug. PostgreSQL requires the partition key in any unique index on a
-- partitioned table, so submitted_at has to be in the key — and including it means two rows for
-- the same intent and channel at different timestamps both pass. A UNIQUE index there prevents
-- only an exact-timestamp collision, which is close to nothing, while reading as a guarantee
-- that something downstream will eventually rely on.
--
-- So it is declared for what it does: serve the delivery reconciler's lookup by intent. The
-- one-row-per-(intent, channel) rule is the dispatcher's to hold, and it holds it by construction
-- — an intent is submitted once, and a resubmission gets a new intent.
--
-- Making it genuinely enforceable would mean partitioning by something derived from outbox_id, or
-- a non-partitioned companion table. Neither is worth it for a rule the write path already
-- satisfies structurally; both are worth revisiting if the dispatcher ever gains a retry.
CREATE INDEX fms_msg_outbox_channel
    ON fms_message_delivery (outbox_id, channel);
