# Archived reference run

These are **real artifacts from a completed partial pipeline run**, kept as a
worked example of the depth the workflow expects. They are not active state and
nothing reads them.

## `backend-hld-run/`

A `scope: backend` run of a Document Management System, taken through Stage 4:

| File | Stage | Lines | Notes |
|---|---|---|---|
| `requirement/requirements.md` | 1 | 686 | Full PRD with 10 REQ IDs, personas, flows, edge cases |
| `review/prd-review.md` | 2 | 66 | Verdict: APPROVED |
| `architecture/hld-backend.md` | 3a | 380 | 7 sections, sequence diagrams, schema design |
| `architecture/tech-stack.md` | 3a | 52 | Stack + infrastructure topology |
| `review/hld-review.md` | 4 | 91 | Verdict was "Ready with Conditions" |
| `artifacts/traceability.md` | multi | 62 | REQ-001…REQ-010, HLD column filled |

## Why keep them

**Calibration.** The depth floors in `hooks/utils/artifact-schema.js` were set
against these files — `requirements.md` came in at 686 lines against a floor of
120, `hld-backend.md` at 380 against a floor of 150. The floors catch lazy output;
they are nowhere near what a serious artifact actually looks like. Read these to
see the target.

## Two things this run would fail today

Useful to know, because both are now enforced:

1. **`hld-review.md` has a non-canonical verdict** — `Ready with Conditions`
   instead of `APPROVED_WITH_CONDITIONS`. `pre-tool.js` now rejects non-canonical
   verdicts so the hard-blocking gate in Rule #6 is actually machine-checkable.
2. **`traceability.md` has empty LLD/Code/Test columns** — correct at Stage 4, but
   the Stage 9 Stop-hook gate would block the handoff to QA until they are filled.
