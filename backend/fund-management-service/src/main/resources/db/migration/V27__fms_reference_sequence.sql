-- V27 — the sequence behind this module's own withdrawal reference (Rule C8).
--
-- A sequence rather than a counter in the application, because two replicas with their own counters
-- issue the same reference to different traders, and a reference that identifies two movements is
-- exactly what Rule C8 exists to prevent. nextval is atomic and never reuses a value, including
-- across a rollback: a gap in the series costs nothing, a repeat costs a support investigation.
--
-- No CYCLE. Wrapping would reissue references that are already printed on statements and quoted in
-- support tickets; at bigint, exhausting it is not a scenario that arises.
CREATE SEQUENCE fms_reference_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;
