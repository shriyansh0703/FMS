-- Constraint verification for V21–V26, against a real PostgreSQL.
--
-- WHY THIS EXISTS AND WHY IT IS NOT A JUnit TEST
--
-- Every constraint below fails SILENTLY when it is wrong. A partial unique index with a predicate
-- that misses a state, a unique index over a nullable column, a CHECK that was never created —
-- each looks correct in review and each permits exactly the row it was written to refuse. Three
-- separate code reviews found defects of this kind by reading, and one of them (fms_intent_once
-- over a nullable asserted_ref) shipped through two reviews before being caught.
--
-- Reading is evidently not sufficient, so this executes them.
--
-- It is a SQL script rather than a Testcontainers test on purpose: adding a container framework to
-- a service that does not otherwise need one is a dependency, a startup cost on every build, and a
-- second thing to patch. This runs on demand, in about ten seconds, and needs nothing but Docker.
--
--   docker run -d --name fms-pg -e POSTGRES_PASSWORD=fms -e POSTGRES_DB=fms \
--     -p 55432:5432 postgres:16-alpine
--   for f in src/main/resources/db/migration/V2*.sql; do
--     docker exec -i fms-pg psql -U postgres -d fms -v ON_ERROR_STOP=1 < "$f"; done
--   docker exec -i fms-pg psql -U postgres -d fms < src/test/resources/db/constraint-verification.sql
--
-- Every block below prints EXPECTED and then either an ERROR (a constraint doing its job) or a
-- successful row count. A block that prints neither is the one to investigate.

\set ON_ERROR_STOP 0
\pset pager off

\echo '### Rule W4 — one open withdrawal request per account'
INSERT INTO fms_payout_request (account_id, amount_paise, state, destination_ref, destination_masked,
    withdrawable_at_request_paise, arrival_date_quoted, fms_reference)
VALUES ('VERIFY01', 500000, 'ACCEPTED', 'acc-1', 'x4471', 1000000, DATE '2026-08-22', 'VER-1');
\echo 'EXPECTED: the second insert below is REJECTED'
INSERT INTO fms_payout_request (account_id, amount_paise, state, destination_ref, destination_masked,
    withdrawable_at_request_paise, arrival_date_quoted, fms_reference)
VALUES ('VERIFY01', 100000, 'ACCEPTED', 'acc-1', 'x4471', 1000000, DATE '2026-08-22', 'VER-2');

\echo '### Rule W4 — a terminal request must NOT block a new one (the predicate is the rule)'
UPDATE fms_payout_request SET state='PAID' WHERE fms_reference='VER-1';
\echo 'EXPECTED: succeeds'
INSERT INTO fms_payout_request (account_id, amount_paise, state, destination_ref, destination_masked,
    withdrawable_at_request_paise, arrival_date_quoted, fms_reference)
VALUES ('VERIFY01', 100000, 'ACCEPTED', 'acc-1', 'x4471', 1000000, DATE '2026-08-22', 'VER-3');

\echo '### the state vocabulary is closed — without this the W4 index is bypassable'
\echo 'EXPECTED: REJECTED (lowercase state)'
INSERT INTO fms_payout_request (account_id, amount_paise, state, destination_ref, destination_masked,
    withdrawable_at_request_paise, arrival_date_quoted, fms_reference)
VALUES ('VERIFY02', 100, 'accepted', 'a', 'x', 100, DATE '2026-08-22', 'VER-4');

\echo '### Rule C8 — the bank reference and ours never share a value'
\echo 'EXPECTED: REJECTED'
UPDATE fms_payout_request SET bank_reference='VER-3' WHERE fms_reference='VER-3';

\echo '### a settlement sends less or equal, never more, and never negative'
\echo 'EXPECTED: both REJECTED'
UPDATE fms_payout_request SET amount_sent_paise=999999 WHERE fms_reference='VER-3';
UPDATE fms_payout_request SET amount_sent_paise=-1 WHERE fms_reference='VER-3';

\echo '### Rule A6 — one credit per payment, however many confirmations arrive'
INSERT INTO fms_payin_attempt (account_id, amount_paise, route, state, gateway_payment_ref)
VALUES ('VERIFY01', 250000, 'UPI', 'CONFIRMED', 'verify-order-1');
\echo 'EXPECTED: REJECTED (repeat confirmation of the same payment)'
INSERT INTO fms_payin_attempt (account_id, amount_paise, route, state, gateway_payment_ref)
VALUES ('VERIFY01', 250000, 'UPI', 'CONFIRMED', 'verify-order-1');

\echo '### Rule A6 must NOT block attempts that have not reached the gateway'
\echo 'EXPECTED: both succeed (NULL gateway refs are distinct, and that is correct here)'
INSERT INTO fms_payin_attempt (account_id, amount_paise, route, state) VALUES ('VERIFY01', 100, 'UPI', 'STARTED');
INSERT INTO fms_payin_attempt (account_id, amount_paise, route, state) VALUES ('VERIFY01', 200, 'UPI', 'STARTED');

\echo '### fms_intent_once — one intent per occurrence per channel'
INSERT INTO fms_message_intent (account_id, template_key, channel, asserted_state, asserted_ref, scheduled_for)
VALUES ('VERIFY01','shortfall_sms','sms','SHORTFALL_OPEN','SF-VERIFY', now());
\echo 'EXPECTED: REJECTED (the same ladder step written twice for one shortfall)'
INSERT INTO fms_message_intent (account_id, template_key, channel, asserted_state, asserted_ref, scheduled_for)
VALUES ('VERIFY01','shortfall_sms','sms','SHORTFALL_OPEN','SF-VERIFY', now());

\echo '### asserted_ref NOT NULL — the fix that makes fms_intent_once constrain anything'
\echo 'EXPECTED: both REJECTED. With a nullable column PostgreSQL treats NULLs as distinct, so'
\echo '          unlimited duplicate intents coexisted and the trader received the message twice.'
INSERT INTO fms_message_intent (account_id, template_key, channel, asserted_state, asserted_ref, scheduled_for)
VALUES ('VERIFY01','shortfall_sms','sms','SHORTFALL_OPEN', NULL, now());
INSERT INTO fms_message_intent (account_id, template_key, channel, asserted_state, asserted_ref, scheduled_for)
VALUES ('VERIFY01','shortfall_sms','sms','SHORTFALL_OPEN', '', now());

\echo '### the snapshot vocabularies (REQ-107 renders source; a bad value is a blank provenance line)'
\echo 'EXPECTED: both REJECTED'
INSERT INTO fms_derivation_snapshot (account_id, computed_at, source, inputs, reconciliation, context)
VALUES ('VERIFY01', now(), 'MIDDLE_OFFICE', '{}', 'RECONCILED', 'VIEW');
INSERT INTO fms_derivation_snapshot (account_id, computed_at, source, inputs, reconciliation, context)
VALUES ('VERIFY01', now(), 'FRONT_OFFICE', '{}', 'MAYBE', 'VIEW');

\echo '### the delivery vocabularies — whatsapp stays out while OA-2 is unconfirmed'
\echo 'EXPECTED: both REJECTED'
INSERT INTO fms_message_delivery (account_id, outbox_id, template_key, channel, status)
VALUES ('VERIFY01', 99, 'k', 'whatsapp', 'accepted');
INSERT INTO fms_message_delivery (account_id, outbox_id, template_key, channel, status)
VALUES ('VERIFY01', 99, 'k', 'sms', 'teleported');

\echo '### movement vocabularies'
\echo 'EXPECTED: both REJECTED'
INSERT INTO fms_movement_state_event (movement_kind, movement_id, to_state, actor)
VALUES ('TRANSFER', 90, 'X', 'SYSTEM');
INSERT INTO fms_movement_state_event (movement_kind, movement_id, to_state, actor)
VALUES ('PAYIN', 90, 'X', 'ROBOT');

\echo '### partition routing, and the DEFAULT partition catching an out-of-range audit row'
INSERT INTO fms_movement_state_event (movement_kind, movement_id, to_state, actor, occurred_at)
VALUES ('PAYOUT', 91, 'INSTRUCTED', 'SYSTEM', TIMESTAMPTZ '2026-09-15 10:00:00+00');
INSERT INTO fms_movement_state_event (movement_kind, movement_id, to_state, actor, occurred_at)
VALUES ('PAYOUT', 92, 'PAID', 'TECHEXCEL', TIMESTAMPTZ '2026-12-10 10:00:00+00');
\echo 'EXPECTED: 91 in the September partition, 92 in the overflow — the audit row survives'
SELECT movement_id, tableoid::regclass AS partition
  FROM fms_movement_state_event WHERE movement_id IN (91, 92) ORDER BY movement_id;

\echo '### and the cost of that safety net: ATTACH is refused while the row sits in DEFAULT'
CREATE TABLE IF NOT EXISTS verify_dec_partition
  (LIKE fms_movement_state_event INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
\echo 'EXPECTED: REJECTED. This is why monthly partition creation must be automated BEFORE'
\echo '          the declared partitions run out, not after.'
ALTER TABLE fms_movement_state_event ATTACH PARTITION verify_dec_partition
  FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
DROP TABLE IF EXISTS verify_dec_partition;

\echo '### cleanup'
DELETE FROM fms_payout_request WHERE account_id LIKE 'VERIFY%';
DELETE FROM fms_payin_attempt WHERE account_id LIKE 'VERIFY%';
DELETE FROM fms_message_intent WHERE account_id LIKE 'VERIFY%';
DELETE FROM fms_derivation_snapshot WHERE account_id LIKE 'VERIFY%';
DELETE FROM fms_message_delivery WHERE account_id LIKE 'VERIFY%';
DELETE FROM fms_movement_state_event WHERE movement_id IN (90, 91, 92);
\echo 'verification complete'
