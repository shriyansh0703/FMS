# LLD Review Report — Settlement And Funding Experience (Run 004) — Re-review

| | |
|---|---|
| Stage | 6 — LLD Review, second pass. Scope fullstack, both reviewers ran |
| Backend | `lld-reviewer` against `lld-backend.md` v3 |
| Frontend | `frontend-lld-review` against `lld-frontend.md` v3 |
| Baseline | `hld.md` v4 (amended, see §2), PRD v1.1 + 3 parts, `lld.md` v2 |
| Previous pass | 2 P0, 3 P1 backend; 2 🔴, 3 🟡 frontend; verdict CHANGES_REQUESTED |

---

## 1. Verdict

**Ready for Implementation.**

All ten findings are closed and the HLD amendment that P0-2 required has been made. The metric blind spot that produced four of the ten was closed as one change rather than four patches, which is what the previous review asked for and is the reason it can be called closed rather than reduced.

The check that matters was run mechanically rather than asserted: **all eleven events in the PRD's tracking table now map to a declared metric.** Three were added — `fms.funding.amount.adjusted` (K4), `fms.deposit.repeat_within_window` (K5), `fms.settlement.explanation.requested` (K7) — and K6's `preselection_kept` tag now has a client-side source it previously lacked. This is the fourth pass at this defect class across three stages, and the first one to close it, because it was the first to walk the PRD's own tracking table row by row rather than reading the designs and asking what looked missing.

Backend: 0 P0, 0 P1, 1 P2. Frontend: 0 🔴, 0 🟡, 2 🟢. Under `lld-reviewer`'s fixed threshold — no P0, no P1, P2 count ≤ 2 — this passes.

One item is not a finding against either LLD but must be visible at the gate: **`hld.md` was amended after Stage 4 approved it**, and `hld-review.md` still carries a verdict for the pre-amendment document. §2 states what changed and why re-approval is a judgement call rather than an obligation.

---

## 2. Baseline Confirmation

| Item | Backend | Frontend |
|---|---|---|
| PRD referenced | Yes — v1.1 + 3 parts | Yes |
| HLD referenced | Yes — v4, including the new §8.5 | Yes |
| API contract cross-checked | Yes, by `lld.md` v2 | Yes |
| Guardrails checked | Yes — `ModuleBoundaryTest` binding, no exemption sought | Existing client conventions binding |
| **Manifest present** | **Yes — new §1.1a.** Eleven modules, seven Included, four Excluded, every exclusion reasoned | Not required; §6's folder tree serves the purpose |

### The HLD amendment, stated plainly

`hld.md` gained §8.5 and three endpoint rows, recording `POST /funds/screen-open`, `GET /funds/features`, and the telemetry fields on the movement requests. §8.5 also supersedes §13.2's original claim that the screen-open moment rides the payment-memory read, with the retry reasoning that made that impossible.

Stage 4's `hld-review.md` reviewed the document before this amendment. The amendment is additive, changes no behaviour the Stage 4 review evaluated, and its mechanism was itself reviewed at LLD depth in the previous pass of this document — which is where the defect in §13.2 was found. On that basis this review does not treat the stale HLD verdict as blocking, but it is the user's call whether Stage 4 re-runs before Stage 7. Recorded here rather than left for someone to notice.

---

## 3. Gate Results — Backend

| Gate | Result | Summary |
|---|---|---|
| Gate 0 — Baseline & Manifest | **Pass** | Manifest present and complete; the two previously undefined boundaries are now in the HLD |
| Gate 1 — Consistency & Drift | **Pass** | No Omission, Bloat, Deformation or Degradation. The one prior Deformation is resolved by the HLD carrying the better mechanism |
| Gate 2 — Module Completeness | **Pass** | Configuration wired; the hot-path access pattern is named and indexed |
| Gate 3 — Implementability | **Pass** | Unchanged from the previous pass, which already passed this gate |

**Implementability score:** `settlement`, `platform`, `fundview`, `notification` — Fully specified. `funding` / `withdrawal` published interfaces — Fully specified (was: Specified with gaps). `integration.orders` — Specified with gaps, deliberately, pending OQ-1.

---

## 4. Verification Of Prior Findings

| Prior finding | Status | Evidence |
|---|---|---|
| **P0-1** Missing Manifest | **Closed.** | New §1.1a lists eleven modules. `notification` — the module whose status was undetermined — is marked Included *narrowly*, with the scope stated as two new event types on an unchanged pipeline, which is the answer the finding asked for. Four exclusions each carry a real reason rather than "N/A": `ledger` adds no movement, `partner` initiates no payment, `reconciliation` has no new position, `integration.bank` is unchanged in how it is called |
| **P0-2** Two endpoints the HLD never defined | **Closed at the right layer.** | `hld.md` §8.5 now carries both, with the retry argument that justifies the deviation from §13.2 and an explicit statement that the design intent — report at open, not at submission — is unchanged. The LLD's mechanism survived the amendment intact, which is the outcome the finding recommended |
| **P1-1** K5 and K7 had no metric | **Closed, and verified mechanically.** | `fms.deposit.repeat_within_window` derives K5 server-side from deposit timestamps, so it needs no client event — the cheapest correct answer. `fms.settlement.explanation.requested` rides the existing explanation read path. All eleven PRD events now map |
| **P1-2** `settlementWindowBusinessDays` unwired | **Closed.** | §6.2 now states that `SettlementRunner` resolves the window when opening a run journal and computes `expected_complete_by` from it — the column run 001 hardcoded at two business days — and that the same resolved value is the source of the `within_window` tag, giving guardrail G3 the input it lacked |
| **P1-3** Month-range activity query unindexed | **Closed with the right diagnosis.** | Two new partial indexes leading on `created_at`, with a comment stating why a trader-leading index cannot serve this question: the memory asks "this trader's latest" and the judgement asks "which traders during month M", and the two have opposite leading columns. §2.3 records that this is what `hld.md` §5.3's chunk budget assumes |
| **🔴 K6 tag had no source** | **Closed.** | `preselectionKept` is now on both movement request bodies, in both documents, with identical naming. **Null when nothing was pre-selected**, so "no memory" stays distinguishable from "memory overridden" — a distinction the finding did not ask for and which is what makes a low K6 diagnosable |
| **🔴 K4 had no event** | **Closed.** | `amountAdjustedAfterProjection` on the deposit request, emitted once per screen open. Both documents state the once-not-per-keystroke rule and the reason — otherwise the metric measures typing speed |
| **🟡 Flag fallback did not survive reload** | **Closed.** | `localStorage`-persisted, seeded into the query on mount. The reasoning is stated: the in-memory cache dies on the reload that a degraded service makes more likely, which would return the client to never-fetched exactly when the fallback was needed |
| **🟡 Instant ceiling against a remembered rail** | **Closed by deciding, not by adding behaviour.** | §23 now covers it and declines to drop the pre-selection, on the grounds that silently changing a selection the trader can see is a worse surprise than an explained refusal, and the existing message already names the alternative. The regression is recorded as accepted with its reason rather than hidden |
| **🟡 Switch off mid-session** | **Closed.** | Takes effect on next mount. One line, and it removes the case of figures vanishing under a trader's hands |

**Regressions introduced by the fixes:** none found. One consequential edit was checked for specifically — the frontend's D8 decision row still described switches as "delivered on existing responses" after the endpoint decision changed; it was corrected in the same pass, and §11, §23 and the implementation checklist agree with it.

---

## 5. Remaining Findings

> **Severity:** P2
> **Gate:** Module Completeness — Observability
> **Observation:** `fms.deposit.repeat_within_window` is described in §7.4 as derived by "a scheduled query over `funding_attempt` credited timestamps per trader". No schedule, owner class or package is named, and it is the only metric in the table without a stated emitter.
> **Impact:** Minor and non-blocking. An implementer will place it somewhere reasonable, but "somewhere reasonable" for a scheduled job in this service means the single-runner arrangement §12.2 describes, and nothing says so.
> **Recommendation:** One clause naming the owning class and its cadence. Take it during implementation rather than in another review cycle.

> **Severity:** 🟢
> **Category:** Observability & Telemetry
> **Observation:** Three optional telemetry fields now ride the deposit request. If a future field is added, the request body becomes a mixed payload of instruction and measurement.
> **Impact:** None today. Worth watching rather than acting on — at four or five fields the argument for a separate telemetry call becomes stronger than the argument for riding an existing one.
> **Recommendation:** No action.

> **Severity:** 🟢
> **Category:** Component Architecture
> **Observation:** `useScreenOpenReport` now mounts on two surfaces and holds both the report and the open timestamp.
> **Impact:** None. Noted only because the same hook now serves three metrics (G1/G2 denominator, G4 duration, K4's once-per-open scope), which is a reasonable amount of responsibility for one hook and the point at which to stop adding to it.
> **Recommendation:** No action.

---

## 6. Missing Information

None blocking. The single P2 above is the only item either document should still add, and it is an implementation-time clause rather than a design gap.

---

## 7. Questions For The Author

1. Should Stage 4 re-run against the amended `hld.md`, or is the amendment's additive nature sufficient? This review does not treat it as blocking but will not decide it either.

---

## 8. Suggested Improvements

1. **Name the emitter for the K5 derivation** — one clause, take it during implementation.
2. **Carry the metric-table walk into Stage 10.** The QA stage tests against PRD acceptance criteria; the tracking table is the one part of the PRD that four separate reviews failed to check row by row. Testing that each declared event actually fires is the last place this class can be caught before production.

### What Is Genuinely Strong

- **The Manifest answered the question it was asked.** `notification` was the module whose status was undetermined, and it is marked Included *narrowly* with the boundary drawn precisely — new event types, unchanged pipeline. A Manifest that had listed it as Included without that qualifier would have satisfied the letter of the finding and left the same ambiguity.
- **K5 is derived rather than instrumented.** Adding a client event was the obvious fix; deriving it from timestamps the service already stores is cheaper, cannot be lost to a client failure, and works retroactively — the baseline the PRD asked for can be computed from history rather than waiting a month to accumulate.
- **The two new indexes come with the diagnosis, not just the fix.** The comment stating that the memory and the judgement ask opposite questions with opposite leading columns is what stops someone later consolidating them into one index and reintroducing the probe-per-trader cost.
- **`preselectionKept` being three-valued.** Nothing asked for null-when-nothing-was-preselected; without it, a trader with no history and a trader who overrode the memory would be indistinguishable, and a falling K6 would be undiagnosable — which is the exact failure the earlier `fms.payment.memory.lookup` counter was added to prevent on the other side.
- **The instant-ceiling case was decided rather than smoothed.** Declining to drop the pre-selection, and recording the regression as accepted with its reason, is more useful than silently adding behaviour that would have surprised the trader differently.
- **Carried forward from the previous pass and unchanged:** the module boundary respected rather than exempted, `ActiveTraderSet` carrying its own answered flag, the sealed `DestinationOutcome` with zero evaluated first, and the `ChooseDestination` assertion being on the absence of the request.

---

## 9. Verdict

Ten findings closed. The metric class that took four passes across three stages to exhaust is closed, and closed by a method — walking the PRD's tracking table rather than reading the designs — that is worth carrying into Stage 10, because that stage is the last place it could be caught.

Backend: 0 P0, 0 P1, 1 P2. Frontend: 0 🔴, 0 🟡, 2 🟢. Both documents are internally consistent, consistent with each other per `lld.md` v2, and consistent with `hld.md` v4.

**Verdict:** APPROVED
