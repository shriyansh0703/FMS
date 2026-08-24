# LLD Review Report — Fund Management System (iteration 2)

| | |
|---|---|
| Stage | 6 — LLD Review. Scope is `fullstack`, so both reviewers ran |
| Backend half | `lld-backend.md` (1,143 lines, was 972), reviewed by `lld-reviewer` |
| Frontend half | `lld-frontend.md` (874 lines, was 809), reviewed by `frontend-lld-review` |
| Consistency pass | `lld.md` v3 — re-run after these fixes, all four checks pass |
| Supersedes | Iteration 1: backend Not Ready (1 P0, 5 P1, 2 P2), frontend Not Ready (1 Critical, 6 Should, 2 Polish) |
| Date | 2026-08-21 |

---

## 1. Verdict

**Verdict:** APPROVED

Backend: **Ready for Implementation** — 0 P0, 0 P1, 1 P2.
Frontend: **Ready for Implementation** — 0 Critical, 0 Should-address, 1 Polish.

All fifteen findings from iteration 1 are closed, and each was verified against the amended documents by
parsing for the specific mechanism rather than by reading the change note. The `lld-reviewer` threshold —
zero P0, zero P1, at most two P2 — is met.

**The P0 was closed correctly rather than conveniently.** Iteration 1 found that the no-double-payout
guarantee rested on TechExcel refusing a repeated instruction key, when TechExcel's duplication
validation returns the same error code as an input-value rejection. The obvious fix would have been to
add an assumption and move on. Instead §6.3 now **reads before it reissues**: for a request stranded in
`INSTRUCTED` — the only state a crash can produce — the run queries `Payment Request Status View Update`
for the same key, applies the prior outcome if one exists, reissues only if none does, and **treats an
unreadable status as a reason to stop rather than to proceed**. The key is retained for what it is
actually good for: making the status query answerable. That is a mechanism, not a caveat.

The one Critical on the frontend was a document defect rather than a design one — a component spec that
described the same handler as both "a no-op" and "opens the derivation panel" — and it is now written as
a two-branch decision with the failure mode named.

---

## 2. Verification of Iteration 1

Each finding checked against the amended document for its specific mechanism.

| # | Finding | Status | Evidence found |
|---|---|---|---|
| BE-P0-1 | Guarantee rests on indistinguishable vendor response | **Closed** | §6.3 queries prior status before reissuing; `PAYOUT_STATUS_UNREADABLE` alert on a failed query; OA-7 records that the design no longer depends on the duplication check |
| BE-P1-2 | Instruction key has no encoding for `UserRefNo` Integer(20) | **Closed** | §6.3a: `(instruction_seq * 100000) + run_date_ordinal`, digit budget shown, disjoint positions, separate sequence for sweeps with no request behind them, `FmsInvariantException` on overflow |
| BE-P1-3 | §7.4's lock absent from §6.3's pseudocode | **Closed** | `requests.lockForUpdate(r.id())` in `applyOutcome`, with the version column explicitly demoted to a consistency check rather than the concurrency mechanism |
| BE-P1-4 | Scheduled dispatch has no storage | **Closed** | V25a `fms_message_intent` with `scheduled_for`, `asserted_state`, a partial index on what is due, and a unique index preventing a ladder step being written twice for one occurrence |
| BE-P1-5 | `EntryDescriptionMapper` named, never defined | **Closed** | §6.3b: full interface, unmapped-type contract returning `ENTRY_DESCRIPTION_UNAVAILABLE` with the reference as secondary detail, purity and configuration-reload stated, unmapped combinations counted and alerted |
| BE-P1-6 | OA-6 fails upward | **Closed** | Rewritten as a verification task that must be confirmed before Stage 7, with the explicit statement that its failure returns the question to the HLD rather than being absorbed |
| BE-P2-7 | Manifest marked depth, not inclusion | **Closed** | Manifest column added; §1.3 retitled as the Excluded half |
| BE-P2-8 | Paise conversion direction unstated | **Closed** | §3.1's Template Method row now says both directions |
| FE-C-1 | §7.4 self-contradiction | **Closed** | Rewritten as a two-branch decision with "there is no branch in which activating the control does nothing", and the competitor failure it would have reproduced quoted |
| FE-S-2 | Live region covering the figures | **Closed** | A dedicated visually-hidden status element carries only the change sentence; the figures sit outside any live region, with the reason given |
| FE-S-3 | Assertive on a possibly-updating deadline | **Closed** | Deadline is a static timestamp announced once; the countdown alternative and its polite-with-thresholds requirement are stated |
| FE-S-4 | `PeriodContext` unjustified | **Closed** | Removed entirely — from §4, §6's tree, §9, §7.6, §10 and the checklist. Both values lifted to `TransactionListPage` |
| FE-S-5 | No loading state on the balance surface | **Closed** | Three-figure skeleton, no figures, actions unavailable with **no reason text** because the reason is not yet known — and the skeleton-over-spinner reasoning given |
| FE-S-6 | Three undecided inputs in the refusal table | **Closed** | `0`, `.5` and `007` each specified, with a paragraph on where the line falls: meaning-preserving normalisation on blur is permitted, meaning-changing rewrite is not, and paste is refused because the rule names it |
| FE-P-8 | Overstated WCAG justification | **Closed** | Restated as the APG recommendation and explicitly "a usability decision rather than a conformance requirement" |
| FE-P-9 | No `prefers-reduced-motion` | **Closed** | The one animation honours it |

**20 of 20 mechanisms verified present.**

---

## 3. Gate Results — Backend

| Gate | Result |
|---|---|
| Gate 0 — Baseline & Manifest | **Pass.** Baseline fully cited. Manifest now marks six modules `Included` with depth, six `Excluded` with reasons. No new service, interface or boundary beyond the HLD |
| Gate 1 — Consistency & Drift | **Pass.** The one Deformation is resolved: §6.3's pseudocode acquires the lock §7.4 specifies. No Omission, Bloat or Degradation found on re-read |
| Gate 2 — Module Completeness | **Pass.** The two modules previously scored "Still HLD-level" both gained the missing piece: message dispatch has its scheduling substrate, ledger view has its mapper contract |
| Gate 3 — Implementability | **Pass.** Every critical flow has pseudocode covering the exception branches, including the ones added this pass |

### Implementability Score

| Module | Iteration 1 | Now |
|---|---|---|
| Balance derivation | Fully specified | **Fully specified** |
| Withdrawal and the EOD run | Specified with gaps | **Fully specified** — the status-query branch and the key encoding were the gaps |
| Payin | Specified with gaps | **Fully specified** |
| Ledger view and export | **Still HLD-level** | **Fully specified** — §6.3b |
| Account health | Fully specified | **Fully specified** |
| Message dispatch | **Still HLD-level** | **Fully specified** — V25a and §7.9 |

---

## 4. Remaining Findings

### Backend

> **BE-P2-9 — The status query's poll window is unstated**
> **Severity:** P2 · **Gate:** Module Completeness
> **Observation:** §7.9 says a notification "still non-terminal past the poll window" is alerted, and does not say how long that window is or where it is configured.
> **Impact:** None on correctness — the branch exists and its action is right. But `tech-stack.md` states that every tunable is configuration rather than a constant, and this one is currently neither. An implementer will pick a number.
> **Recommendation:** Name it as a configured value alongside `payoutCutoff`. One line.

### Frontend

> **FE-P-10 — `useAmountField`'s blur-time normalisation is specified in prose but not in the hook's return shape**
> **Severity:** 🟢 Polish
> **Observation:** §7.3 now specifies that `.5` becomes `0.50` and `007` becomes `7` on blur. §10's hook signature returns `{ value, onBeforeInput, onChange, error }` with no `onBlur`.
> **Impact:** Trivial and self-evident at implementation time, but the signature and the behaviour should agree in a document whose whole argument is that this field's specification is its behaviour.
> **Recommendation:** Add `onBlur` to the returned shape in §10.

Neither blocks implementation. Two P2/Polish findings is within the threshold.

---

## 5. Questions for the Author

The design no longer depends on any of these, but all three remain worth answering before code is
written, because each would simplify something or confirm a fallback is unnecessary.

1. **Does TechExcel's duplication validation key on `UserRefNo`, and is a duplicate distinguishable from a validation failure?** If yes, it becomes a redundant second line behind the status query rather than a change. If no, the design is already correct.
2. **Does the `Ledger` API page?** BE-P1-6 is now a verification task gating Stage 7 rather than a carried assumption. It is a lookup.
3. **Is FMS granted the `whatsapp` channel, and what address format does it use?** Unchanged from Stage 4. It decides whether four requirements keep the channel they were written for.

---

## 6. Suggested Improvements

1. Name the poll window as configuration — BE-P2-9.
2. Add `onBlur` to `useAmountField`'s return shape — FE-P-10.
3. Answer question 2 before Stage 7 opens; it is the only one whose answer could still change a design decision.

---

## 7. What Is Genuinely Strong

Recorded with the same specificity as the criticism.

- **BE-P0-1's fix inverted the mechanism rather than documenting around it.** The easy close was an assumption. Instead the run reads before it reissues, and — the part that matters — **an unreadable status aborts the account rather than being treated as an absent payment.** That is the correct default for money that may already have moved, and it is the opposite of what a retry-oriented instinct produces.
- **§6.3a shows its digit budget.** Fifteen digits for the instruction, five for the run-date ordinal, disjoint positions, an overflow that throws rather than truncates, and a separate sequence for a mandated return that has no request behind it. The last of those is the detail a design usually misses: "use the request id" has no answer for a sweep nobody requested.
- **V25a's unique index prevents a ladder step being written twice for one occurrence**, which is a different guarantee from the submission idempotency the `request_id` provides. The document distinguishes them explicitly rather than assuming one covers the other.
- **§6.3b gives the unmapped case a designed behaviour**, matching the PRD's edge case that a raw reference must be shown *with* an explicit statement that a plain description is unavailable — never presented as though it were the description. It also counts and alerts on unmapped combinations, treating an unknown entry type as a requirement gap a user is currently looking at.
- **§7.9 refuses to treat SMS `delivered` as proof of receipt**, and escalates to a human when both channels of a regulatory intimation fail terminally while the shortfall still stands. Naming alerting as "an admission that delivery failed while it still mattered" rather than as a fallback is the right framing.
- **FE-S-6's answer draws the line rather than just filling the rows.** Meaning-preserving normalisation on blur is permitted; meaning-changing rewrite is not; paste is refused because the rule names it and a money field is not the place to reinterpret a rule. That reasoning is reusable for the next input question.
- **FE-S-5 chose a skeleton over a spinner for a stated reason** — a spinner over a money surface reads as *a figure is being calculated*, and it is being fetched, not calculated.
- **Both documents record what an earlier draft said and why it was wrong** — §7.8's REQ-201 row, §7.4's no-op wording, §4's context justification. That is what stops the same mistake returning at Stage 8.
