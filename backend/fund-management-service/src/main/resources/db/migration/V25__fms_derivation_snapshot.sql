-- V25 — derivation snapshots (Rule W11).
--
-- The inputs and output of derive() at every decision point: a payout request, a settlement,
-- a message render. NOT for a page view — a view is not disputed months later, a payout is.

CREATE TABLE fms_derivation_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          VARCHAR(20)  NOT NULL,
    computed_at         TIMESTAMPTZ  NOT NULL,

    -- Which system answered. REQ-107 renders this alongside the time, so a figure stepping
    -- at the market-open boundary reads as a scheduled handover rather than a data error.
    source              VARCHAR(16)  NOT NULL,   -- FRONT_OFFICE | BACK_OFFICE

    -- JSONB deliberately: the input set is a whole captured at an instant, it is never
    -- queried by individual field, and its shape will change as vendors change. Normalising
    -- it would buy query patterns nobody needs and impose a migration every time a source
    -- adds a field.
    inputs              JSONB        NOT NULL,

    withdrawable_paise  BIGINT,
    rms_figure_paise    BIGINT,

    -- RECONCILED | DIVERGENT | UNAVAILABLE. Recorded so that a later question about why a
    -- figure was unavailable has an answer rather than an absence.
    reconciliation      VARCHAR(16)  NOT NULL,

    context             VARCHAR(32)  NOT NULL,   -- PAYOUT_REQUEST | SETTLEMENT | MESSAGE | VIEW

    -- Each of these three vocabularies is fixed by the design, so each is closed here.
    -- REQ-107 renders `source`, and a value outside this pair would reach a trader as a
    -- blank provenance line rather than as an error.
    CONSTRAINT fms_snapshot_source_vocabulary
        CHECK (source IN ('FRONT_OFFICE', 'BACK_OFFICE')),
    CONSTRAINT fms_snapshot_reconciliation_vocabulary
        CHECK (reconciliation IN ('RECONCILED', 'DIVERGENT', 'UNAVAILABLE')),
    CONSTRAINT fms_snapshot_context_vocabulary
        CHECK (context IN ('PAYOUT_REQUEST', 'SETTLEMENT', 'MESSAGE', 'VIEW'))
);

-- "Why did I receive less than I asked for?", months later.
CREATE INDEX fms_snapshot_account
    ON fms_derivation_snapshot (account_id, computed_at DESC);
