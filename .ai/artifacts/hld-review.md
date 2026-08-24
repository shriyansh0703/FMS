# HLD Review Report: Fund Management System — Unified System HLD (iteration 3)

## 1. Verdict

* **Status:** Ready with Conditions — **Score: 8.7/10** (6.1 → 8.3 → 8.7)
* **Reviewed Stage:** Growth — a regulated money surface inside a running estate, with five live integrations, a stated availability target and compliance obligations.
* **System Shape:** Full-stack — React client, Spring Boot modular monolith, single PostgreSQL primary, five external integrations (Kambala Noren OMS/RMS, TechExcel back office, Juspay gateway, Profile, Communication Service).

**Verdict:** APPROVED_WITH_CONDITIONS

Iteration 3 was not a defect-fixing pass. It was the design being rewritten against the vendor and platform contracts it had previously assumed, and it changed more than the two review cycles before it did. Five integration contracts were read and folded in; **every field the document now cites was verified against its source workbook**, and all fourteen checked claims match.

The most consequential change resolves a question no reviewer had asked, because nobody knew to ask it. Kambala Noren is not a margin feed — it is the OMS and RMS, and `GetWithdrawalAmt` is its own answer to what may leave an account. Left undiscovered, Stage 5a would have designed FMS to compute a figure that a system upstream of it already computes authoritatively, and the two would have disagreed in production over exactly the number this product exists to explain. §8.0 settles it correctly: RMS's figure is the authority, Rule B4 is the explanation, and a disagreement makes the figure unavailable rather than making FMS choose.

Three of the previous iteration's open conditions closed on evidence rather than on judgement, and one closed by being disproved. What replaces them are four questions that are genuinely external — a channel grant, a payout path, a calendar source, and a free-text field's vocabulary — none of which the design can answer by thinking harder.

### Executive Scorecard & Confidence

Confidence is **high across all nine dimensions**, and materially higher than iteration 2 on the integration-facing ones, because those sections now rest on contracts rather than on assumptions labelled as such.

The standing caveat: I wrote the document under review. This pass was verified by extracting the three vendor workbooks and checking each cited field against them programmatically, and by re-running the traceability citation test. A second reviewer should form their own view on the RMS-versus-Rule-B4 relationship in §8.0, which is the single most consequential judgement in the document.

| Dimension | Score | Change | Notes |
|---|---|---|---|
| 1. Requirements & Scope Fidelity | 9/10 | — | 65/66 cells cite a section naming the requirement; one honest gap |
| 2. Architecture & Trade-off Rigor | 9/10 | +1 | The RMS/Rule B4 resolution is the best-reasoned decision in three iterations |
| 3. System Design & Module Boundaries | 8/10 | — | Ownership map still absent |
| 4. Data & State Architecture | 9/10 | — | Paise-integer rule added; segment obligation closed by contract |
| 5. API Design & Contracts | 9/10 | — | Now describes five real contracts rather than four assumed ones |
| 6. Security, Compliance & Data Privacy | 9/10 | +1 | Taxonomy R4/R5 and Profile PR-31/PR-32 give the non-disclosure design external authority |
| 7. Reliability, Failure Handling & DR | 8/10 | −1 | The no-retry messaging exposure is real, correctly surfaced, and only partly mitigable |
| 8. Performance & Scalability | 8/10 | — | Unchanged |
| 9. Observability, Deployment & Operability | 9/10 | +1 | Instrumentation now bound by four named taxonomy rules rather than a general intention |

---

## 2. Requirement Traceability (Three Doors)

* **Door 1 — Coverage:** **Pass**
* **Door 2 — Fidelity:** **Pass**
* **Door 3 — Readiness:** Pass with concerns

Re-tested after roughly 200 lines were inserted: all 17 distinct anchors still resolve, 65 of 66 cells cite a section that names their requirement, and none cites a summary section. REQ-503 remains deliberately empty with its reason recorded.

Door 2 strengthened rather than merely holding. Three requirements moved from *designed against an assumption* to *designed against a contract*:

- **REQ-108** — the segment obligation in §9.1a was written as a data-model instruction to be honoured. TechExcel's `COCD` already carries `BSE_CASH / NSE_CASH / NSE_FNO / CD_NSE / CD_B` on every entry, so the obligation is satisfied at source and the work is not discarding it on ingest. A requirement the previous review treated as future-proofing is now a contract fact.
- **REQ-404** — §9.1b decided TechExcel supplies the running balance. `CLOSING_AMT` confirms it.
- **REQ-308** — the naming obligation was assumption A5. `Amount`, `AUTH_DUE_AMT` and `RMSData` are real fields, and `RMSData` is the blocked risk amount numerically, which is the deduction in the margin case.

---

## 3. Verification of the Iteration-2 Conditions

| # | Condition | Status | Evidence |
|---|---|---|---|
| 1 | Confirm the running-balance question with TechExcel | **Closed** | `CLOSING_AMT` — "Closing Amount of transaction" — verified in the workbook. §9.1b's decision is now contractual |
| 2 | Confirm assumptions A5 and A6 | **Closed, one by disproof** | A5: the fields exist; caveat carried as R7. A6: Juspay's `Get Balance` is the gateway's balance, not a per-customer cap, so **FMS owns the cap ledger** — `/funds/payin/limits` is confirmed necessary |
| 3 | State the integrity check's shape and cost | **Not addressed** | Carried forward as FIND-14. §16.4 states the frequency; neither §16.4 nor §5 states whether it is per-account on access or a sweep |
| 4 | Resolve REQ-503, or descope it | **Open** | Product decision on EB-6, unchanged |
| — | FIND-12, the running-balance fallback | **Closed** | The fallback is moot: TechExcel supplies the figure |
| — | FIND-13, no ownership map | **Not addressed** | Carried forward |

---

## 4. Findings by Category

### Reliability, Failure Handling & DR

> **FIND-15 — The mandatory regulatory intimation rests on a channel that cannot evidence receipt**
> * **Severity:** 🟠 Major
> * **Dimension:** Reliability, Failure Handling & DR
> * **Layer(s):** Service, Infra
> * **Observation (Evidence):** §8.0 records that the Communication Service never retries, that `failed` is terminal, and that nothing calls back on a stuck hand-off. It further records that SMS `delivered` means the vendor accepted rather than that a handset received, marked `SYNTHETIC_ACCEPT_NO_DLR`, because the aggregator publishes no delivery-report mechanism at all. §15 responds by polling and resubmitting with a new `request_id`; §21 R6a states the residual.
> * **Impact (Risk):** The margin shortfall intimation is described by the PRD as mandatory and same-day, and it is the message whose absence costs a user their positions. The design's mitigation — poll, and on a terminal status resubmit under a new key — closes the *unsent* case and cannot close the *unacknowledged* case, because no acknowledgement exists on the channel. So the system can prove it dispatched and can never prove the client was informed. If the obligation is worded as informing the client, no amount of engineering on this channel satisfies it; if it is worded as issuing the intimation, the design already satisfies it. Nobody in this pipeline can tell which, and the difference is a regulatory finding rather than a defect.
> * **Recommendation (Fix):** The document does the right thing by escalating rather than deciding — §23 item 8 puts it to compliance. Add one operational control it does not yet have: alert when a shortfall intimation reaches a terminal non-delivery status for an account still in shortfall, so a human can act inside the same session rather than discovering it in a monthly report.

> **FIND-16 — Two-channel delivery is two independent submissions, and the ladder's guarantee assumes one act**
> * **Severity:** 🟡 Minor
> * **Dimension:** Reliability, Failure Handling & DR
> * **Layer(s):** Service
> * **Observation (Evidence):** §8.0 records that `channels` carries exactly one element per call, so Rule C1's "SMS and email at minimum, regardless of preferences" is two submissions with two `request_id`s that fail independently.
> * **Impact (Risk):** Rule C1 exists because either channel can silently fail. Two submissions is the correct implementation, but it introduces a partial state the PRD does not describe: SMS accepted and email terminally failed, or the reverse. The account has then been told on one channel and not the other, which satisfies Rule C1's *minimum* on a literal reading and defeats its purpose on the intended one. Nothing in the design says which outcome counts as the intimation having been made.
> * **Recommendation (Fix):** State the rule explicitly: the intimation is satisfied when at least one channel reaches a non-terminal-failure status, and a terminal failure on either is recorded against the account and alerted. One sentence in §15.

### Performance & Scalability

> **FIND-14 — The hourly integrity check still has no stated shape or cost** *(carried, unchanged)*
> * **Severity:** 🟡 Minor
> * **Observation (Evidence):** §16.4 raises the check to hourly in market hours on the grounds that it is "a comparison over data already fetched". §5's workload table does not include it.
> * **Impact (Risk):** True for an account being viewed; false for the book as a whole. A sweep across 500,000 accounts twelve times a session is a materially different load against a vendor integration with its own rate limits, and §5 does not account for it.
> * **Recommendation (Fix):** State which, and if a sweep, add it to §5.

### System Design & Module Boundaries

> **FIND-13 — Still no ownership map** *(carried, unchanged)*
> * **Severity:** 🟡 Minor
> * **Recommendation (Fix):** One column on §7's and §13.1's tables.

> **FIND-17 — Three systems can execute a payout, and the guard against two being live is a comment**
> * **Severity:** 🟡 Minor
> * **Dimension:** System Design & Module Boundaries
> * **Layer(s):** Service, Infra
> * **Observation (Evidence):** §21 R8 records that Noren's `WithdrawFunds`, TechExcel's `Payout_Request_Addition` and Juspay's merchant payout can each move money out, that this design routes through the back office, and that "exactly one payout path is enabled, and that is a deployment invariant rather than a code one — it should be asserted at startup rather than assumed."
> * **Impact (Risk):** The risk is stated accurately and the mitigation is stated as an intention. Rule W9's guarantee that the same money is never sent twice rests on FMS combining a request and a mandated return into one instruction; a second live path instructs independently and the combine step protects nothing. The failure is silent, produces a duplicate payout, and is exactly the invariant the PRD sets to zero.
> * **Recommendation (Fix):** Make the assertion real: fail startup if more than one payout integration is configured. It is a few lines and it converts a documented intention into an enforced one, in the same spirit as putting Rule W4 in a unique index.

---

## 5. Conditions on This Approval

None blocks Stage 5 from starting. The first three are external and should be answered before Stage 5a designs against them.

1. **Confirm the WhatsApp grant and address format for FMS.** Narrowed by Profile's §7.4b, which proves the channel is live in the estate. What remains is whether FMS is in the grant. Roughly four requirements — REQ-602, REQ-604, REQ-624, REQ-626 — depend on the answer, and if it is no, that is a Stage 1 amendment rather than an LLD improvisation.
2. **Confirm which system executes a payout**, and enforce singularity at startup — FIND-17.
3. **Agree the `Reject_Reason` vocabulary with the back office** — R7. `RMSData` covers the margin case numerically; everything else depends on what is written into a free-text field.
4. **EB-9, the calendar source.** Unchanged, still the highest-severity open item, still gating Phase 1 rather than Phase 3.

Carried, unchanged: FIND-13, FIND-14, REQ-503's product decision, and the two PRD-level items — EB-1 stating the opposite of the ledger decision, and open question 7 on whether the running Fund Management Service is this same product.

---

## 6. Questions for the Author

1. Is the integrity check per-account on access or a sweep? Third time asked; it is the only cost in the document that could be off by orders of magnitude.
2. When Rule C1's two submissions split — one accepted, one terminally failed — has the intimation been made? FIND-16.
3. RMS's `GetWithdrawalAmt` is now the authority on what may leave. Does RMS apply the same deductions Rule B4 names — money added today, unsettled proceeds, unposted charges — or a different set? If different, the reconciliation in §8.0 will fail routinely rather than exceptionally, and "present as unavailable" becomes the normal state rather than the alarm.

---

## 7. Suggested Improvements

1. **Answer question 3 above before Stage 5a.** It is the one place where a correct-sounding design could fail continuously in production. §8.0's reconciliation is right in principle and depends entirely on the two figures being built from compatible inputs.
2. **Enforce the single payout path at startup** — FIND-17.
3. **Alert on a terminally undelivered shortfall intimation while the shortfall stands** — FIND-15.
4. **Size the integrity check; add the ownership column** — FIND-14, FIND-13.

### Stress-Test Matrix Results

* **1. Traffic spike:** **Held up.** Unchanged.
* **2. Dependency outage:** **Held up, and better specified.** A dropped Noren subscription is now handled as staleness rather than silence, which closes a gap the polling model never had to think about: a stream that stops delivering looks exactly like nothing changing.
* **3. Data inconsistency:** **Held up.** The RMS-versus-Rule-B4 reconciliation adds a second consistency check the earlier design had no way to perform, and it fails safe by presenting the figure as unavailable.
* **4. Mutation under network failure:** **Held up.** Instruction-keyed idempotency, unchanged from iteration 2.
* **5. Deployment failure:** **Held up.** Forward-only additive migrations; content-hashed bundles.
* **6. Organizational scaling:** **Implicit but undocumented.** FIND-13.
* **7. Domain-specific — a shortfall intimation is terminally undelivered while positions approach square-off:** **Failed.** This replaces iteration 2's scenario because it is now the sharpest edge in the document. The service will not retry, will not call back, and offers no receipt evidence on SMS. §15 polls and resubmits, which addresses a message that never left — and if the resubmission also fails terminally, nothing in the design escalates to a human while the deadline is still live. FIND-15 recommends the alert that would change this to "held up".

### What's Genuinely Strong

* **§8.0 is the section this document was missing for two iterations.** Five contracts, what each provides, and what each *changed* — four decisions reversed on evidence. Most designs record their integrations as a list of systems; this one records them as a list of things it got wrong and corrected.
* **The RMS-versus-Rule-B4 resolution.** The easy readings were both wrong: treat `GetWithdrawalAmt` as a duplicate and ignore it, or abandon Rule B4 and present RMS's number bare. The document takes neither — RMS holds the authority because it knows what is blocked against positions, Rule B4 becomes the explanation, and a disagreement is surfaced rather than resolved. That keeps Rule B12 intact while acknowledging that the definition does not live in FMS.
* **Rule R5 turned into a data-model decision.** "Money is an integer in paise, never a float" could have been left in the analytics section it came from. §9.1c ties it to Rule B4's exactness requirement and to the boundary conversion, which is where it actually bites.
* **The taxonomy rules are read as architecture, not analytics.** R3 — the server emits outcomes — is justified here by the observation that an end-of-day payout outcome has no client present to emit it. That is the kind of connection that only comes from reading the constraint against the design rather than filing it.
* **Profile's PR-28 was allowed to change a design decision.** Verification resolving after the session ends means the account list cannot be cached for a journey. It would have been easy to read Profile as "FMS reads a list" and move on.
* **The document escalates what it cannot decide.** The SMS receipt question goes to compliance, the WhatsApp grant to whoever holds it, EB-1 to the PRD's author. Three iterations in, the open-questions section is longer and more specific than it started — which is the correct direction for a design that has been reading contracts.
