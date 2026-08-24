-- V23 — the cap ledger (REQ-701).
--
-- This table exists because no external system can provide it. REQ-701 requires caps
-- enforced per day per route, measured against everything that customer has already sent on
-- that route today. Juspay's Get Balance is the gateway's own balance, not a per-customer
-- remaining cap — verified against the vendor reference on 21 Aug 2026. Only this system
-- knows what this account has sent, so this system owns the ledger of it.
--
-- Enforcing per transaction while telling the trader the limit is daily would let them pass
-- the same amount twice and be refused by their bank instead of by us.

CREATE TABLE fms_route_cap_usage (
    account_id    VARCHAR(20) NOT NULL,
    route         VARCHAR(24) NOT NULL,
    usage_date    DATE        NOT NULL,
    sent_paise    BIGINT      NOT NULL DEFAULT 0 CHECK (sent_paise >= 0),

    PRIMARY KEY (account_id, route, usage_date)
);
