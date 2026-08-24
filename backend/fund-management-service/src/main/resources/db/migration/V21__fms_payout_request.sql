-- V21 — withdrawal requests. One row per request, whole lifecycle.
--
-- This table carries three constraints that are business rules rather than data hygiene.
-- Each is enforced here, and not in a service method, because a service check has a window
-- and a constraint does not.

CREATE TABLE fms_payout_request (
    id                              BIGSERIAL PRIMARY KEY,

    -- VARCHAR(20) to match the back office's own Client_code width, which is what AccountRef
    -- validates against. Two widths for one identifier is two contracts, and the wider one
    -- silently accepts a value the narrower one would have refused at the boundary.
    account_id                      VARCHAR(20)  NOT NULL,
    amount_paise                    BIGINT       NOT NULL CHECK (amount_paise > 0),
    state                           VARCHAR(24)  NOT NULL,

    -- Rule W12: the destination is fixed at request time. A later change to the trader's
    -- accounts never redirects a request already in flight.
    -- Only the masked form is stored: Profile masks server-side (PR-31) and this system
    -- never receives the unmasked value, so there is nothing here to leak.
    destination_ref                 VARCHAR(64)  NOT NULL,
    destination_masked              VARCHAR(24)  NOT NULL,

    -- Rule W11: what was true at each decision, so "why did I receive less than I asked
    -- for?" has an answer months later.
    withdrawable_at_request_paise   BIGINT       NOT NULL,
    withdrawable_at_settle_paise    BIGINT,

    -- REQ-303: the quoted date and the achieved date. This pairing is the entire mitigation
    -- for the PRD's rated risk that operations cannot meet the times the product quotes —
    -- without both stored there is nothing to compare.
    arrival_date_quoted             DATE         NOT NULL,
    credited_on                     DATE,

    -- Rule C8: the bank's own transfer reference and ours are different fields.
    bank_reference                  VARCHAR(64),
    fms_reference                   VARCHAR(32)  NOT NULL,

    settlement_reason_code          VARCHAR(48),
    settlement_reason_text          TEXT,
    amount_sent_paise               BIGINT,

    requested_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at                       TIMESTAMPTZ,

    -- Stale-in-memory-copy detection. NOT the concurrency mechanism: the settlement path
    -- takes a row lock (SELECT ... FOR UPDATE). The two are easy to confuse and they fail
    -- differently, so this comment is load-bearing.
    version                         INTEGER      NOT NULL DEFAULT 0,

    -- A trader chasing a payment needs the identifier their bank can trace. Populating ours
    -- into the bank's field would send them somewhere the reference means nothing, so the
    -- database refuses it rather than trusting convention.
    CONSTRAINT fms_payout_refs_differ
        CHECK (bank_reference IS NULL OR bank_reference <> fms_reference),

    -- A settlement may send less than was requested. It may never send more, and it
    -- never sends a negative amount.
    CONSTRAINT fms_payout_sent_within_request
        CHECK (amount_sent_paise IS NULL
               OR (amount_sent_paise >= 0 AND amount_sent_paise <= amount_paise)),

    -- Rule B9 floors the withdrawable figure at zero, so a stamped one is never negative.
    CONSTRAINT fms_payout_withdrawable_non_negative
        CHECK (withdrawable_at_request_paise >= 0
               AND (withdrawable_at_settle_paise IS NULL OR withdrawable_at_settle_paise >= 0)),

    -- THE STATE VOCABULARY IS CLOSED, AND THAT IS WHAT MAKES THE INDEX BELOW A RULE.
    --
    -- fms_payout_one_open_per_account enforces Rule W4 by naming the open states in a
    -- partial predicate. That is only equivalent to "one open request per account" while
    -- every open state appears in the predicate. Without this constraint the column is free
    -- text: a lowercase 'accepted', a trailing space, or a state added later for a genuinely
    -- open condition all slip past the index in silence, and the symptom is a trader with two
    -- live withdrawals rather than an error anyone sees.
    --
    -- The list is §7.5's state machine in full. Adding a state now fails here, at migration
    -- time, where the author is looking — which is the point. A new OPEN state must also be
    -- added to the index predicate below; a new TERMINAL state must not.
    CONSTRAINT fms_payout_state_vocabulary
        CHECK (state IN ('ACCEPTED', 'QUEUED_FOR_RUN', 'INSTRUCTED',
                         'PAID', 'PARTLY_PAID', 'NOTHING_SENT', 'RETURNED', 'CANCELLED'))
);

-- Rule W4 — ONE OPEN REQUEST PER ACCOUNT.
--
-- Rule W3 removed reservation from the withdrawal path, which left this index as the only
-- thing preventing a trader committing the same money twice. It is enforced here, where a
-- race cannot get past it, and the service deliberately does NOT pre-check by reading first
-- — a read-then-write is a race dressed as validation.
--
-- The index is only correct BECAUSE it is partial: a full unique index on account_id would
-- permit one withdrawal per account ever. The WHERE clause is half the rule; the other half
-- is fms_payout_state_vocabulary above, which is what stops an unlisted state bypassing this
-- silently. Neither is sufficient alone.
CREATE UNIQUE INDEX fms_payout_one_open_per_account
    ON fms_payout_request (account_id)
    WHERE state IN ('ACCEPTED', 'QUEUED_FOR_RUN', 'INSTRUCTED');

-- The end-of-day run's only scan.
CREATE INDEX fms_payout_run_scan
    ON fms_payout_request (state, requested_at)
    WHERE state IN ('ACCEPTED', 'QUEUED_FOR_RUN');

-- Our reference is unique, and support searches by it.
CREATE UNIQUE INDEX fms_payout_fms_reference ON fms_payout_request (fms_reference);
