-- V25.1 — scheduled message intents.
--
-- The estate's outbox dispatches promptly after commit. The shortfall ladder has step
-- offsets and the dues sequence runs day 0, 7, 14, 30 then monthly, so an intent must be
-- able to WAIT without being lost, and must NOT be sent once the state it asserts has
-- resolved (REQ-622). Neither property is available from a plain delayed job.
--
-- The intent row is written in the same transaction as the state change that caused it,
-- which is what makes the message and the state atomic.

CREATE TABLE fms_message_intent (
    -- This id IS the Communication Service's request_id. A relay retry after a crash
    -- replays the same key and the service returns the original result rather than sending
    -- again. Note this is SUBMISSION idempotency only: the service itself never retries a
    -- failed send, which the delivery reconciler handles separately.
    id              BIGSERIAL PRIMARY KEY,

    account_id      VARCHAR(20)  NOT NULL,
    template_key    VARCHAR(64)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,

    -- The state this message asserts, re-checked at dispatch. If the shortfall cleared
    -- while step 2 waited, step 2 is marked STATE_RESOLVED and never sent — REQ-622's
    -- drop-rather-than-retract, which is why the schedule cannot be a plain delayed job.
    asserted_state  VARCHAR(32)  NOT NULL,

    -- The occurrence this intent belongs to: the shortfall, the dues cycle, the payin
    -- attempt. NOT NULL is load-bearing rather than tidiness — see fms_intent_once below,
    -- which does not constrain a row whose asserted_ref is NULL.
    --
    -- A caller with no natural occurrence key must synthesise a stable one (a daily digest
    -- uses its date) rather than leaving it empty. Synthesising it is a visible decision at
    -- the call site; leaving it null was an invisible hole in the uniqueness rule.
    asserted_ref    VARCHAR(64)  NOT NULL CHECK (asserted_ref <> ''),

    scheduled_for   TIMESTAMPTZ  NOT NULL,   -- now() for immediate; the offset for a ladder step
    dispatched_at   TIMESTAMPTZ,
    dropped_reason  VARCHAR(48),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The relay's only query: what is due and not yet resolved.
CREATE INDEX fms_intent_due
    ON fms_message_intent (scheduled_for)
    WHERE dispatched_at IS NULL AND dropped_reason IS NULL;

-- One intent per template per occurrence per channel. A ladder step written twice for one
-- shortfall collides here rather than being sent twice. This is a different guarantee from
-- the request_id above: that one prevents a double SUBMISSION of one intent, this one
-- prevents two intents existing for one occurrence.
--
-- This index only works because asserted_ref is NOT NULL. PostgreSQL treats NULLs as
-- distinct from one another in a unique index, so while the column was nullable this
-- constraint silently permitted unlimited duplicates for every intent that carried no
-- occurrence reference — the exact case it was written to prevent, and the trader would
-- have received the message twice.
--
-- PostgreSQL 15 offers NULLS NOT DISTINCT for this. It is deliberately NOT used: no server
-- version is pinned in tech-stack.md, and a constraint that silently stops constraining on
-- an older server is the failure this comment exists to describe.
CREATE UNIQUE INDEX fms_intent_once
    ON fms_message_intent (account_id, template_key, channel, asserted_ref);
