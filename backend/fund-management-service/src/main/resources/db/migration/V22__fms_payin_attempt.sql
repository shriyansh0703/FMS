-- V22 — payin attempts. Every attempt, including the ones that failed.
--
-- Rule L8: a deposit that failed is part of what happened to the account, and it is the
-- entry a trader most often needs to discuss. Its recorded reason stays with it.

CREATE TABLE fms_payin_attempt (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          VARCHAR(20)  NOT NULL,
    amount_paise        BIGINT       NOT NULL CHECK (amount_paise > 0),
    route               VARCHAR(24)  NOT NULL,
    state               VARCHAR(24)  NOT NULL,

    -- The gateway's identity for the payment. Null until the attempt reaches the gateway,
    -- which is why the uniqueness below is partial.
    gateway_payment_ref VARCHAR(96),

    -- One of Rule A9a's six outcomes. Note that "unknown" is not "failed" — Rule A9b makes
    -- that distinction because the recovery is the opposite: wait, and specifically do not
    -- retry.
    outcome_code        VARCHAR(48),

    -- Last four digits only (REQ-612). The full number is never stored and never rendered.
    source_masked       VARCHAR(24),

    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    version             INTEGER      NOT NULL DEFAULT 0
);

-- Rule A6 — ONE CREDIT PER PAYMENT, however many confirmations arrive.
--
-- Repeat confirmations are an EXPECTED condition, not an exceptional one. The second insert
-- collides here and the handler returns success having recorded nothing further, because
-- the caller is a gateway that will retry on anything else.
CREATE UNIQUE INDEX fms_payin_gateway_ref
    ON fms_payin_attempt (gateway_payment_ref)
    WHERE gateway_payment_ref IS NOT NULL;

-- Serves the money-in-and-out view, and the last-successful-deposit lookup Rule A1 needs to
-- open the amount field on what the trader last added.
CREATE INDEX fms_payin_account_recent
    ON fms_payin_attempt (account_id, started_at DESC);
