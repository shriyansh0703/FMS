# PRD Review: Fund Management System — Iteration 3

**Reviewer:** Stage 2 — `prd-reviewing`
**Review date:** 2026-08-21
**PRD version:** 1.0 (status: draft), after the condition-closure pass
**Scope reviewed:** `fullstack`
**Documents:** `docs/specs/001-fund-management-system/product-requirements.md` (index) plus all seven files in its frontmatter `parts:`. 3,024 lines.
**Supersedes:** iteration 2 (APPROVED_WITH_CONDITIONS, 0 Blocker / 3 Critical / 7 Major / 5 Minor) and iteration 1 (CHANGES_REQUESTED, 4 Blocker / 6 Critical / 10 Major / 4 Minor).

---

## Verdict

**Verdict:** APPROVED

The three conditions iteration 2 attached to its approval are closed, along with the two Major items named in the same recommendation. Each was verified against the files and against everything else that cites the rule it changed — which is what iteration 2 failed to do and how it introduced those three defects in the first place.

The PRD has no known internal contradiction. Its register reconciles exactly with its files: 70 defined requirement headings, 66 labelled Must Have, 2 Should Have, 2 excluded with stated reasons, and no requirement ID named anywhere in the index that fails to resolve.

Seven consistency and navigation items remain open and are recorded below. None concerns a requirement, a rule or a number. They are the difference between a PRD that can be designed from and one that can be handed to a team nobody in this session has met, and they should be swept before that handover — but holding the pipeline for them would cost more than it saves.

---

## Scope of This Review

This is a focused re-review, not a fourth full read. Iteration 2 verified all ten of iteration 1's findings against the files and re-ran the complete Split-File Consistency checklist; nothing in this pass invalidates that work. What changed since is exactly five edits, all named by iteration 2's own recommendation.

The verification below therefore does two things: confirms each of the five edits landed, and — because iteration 2's three Critical findings were all caused by an edit whose downstream citations went unchecked — re-examines everything that references the rules those five edits touched.

---

## Verification of the Iteration-2 Conditions

| # | Condition | Status | Evidence |
|---|---|---|---|
| N1 | Rule B9 forbids the flooring Rule B4 now performs | **Closed** | Rule B9 retitled *"…are never clamped, with one exception"* and carries a second paragraph naming the withdrawable figure as that exception. The reasoning given is the right one: the figure answers "what can reach my bank today", and there is no negative answer to that question. It also states what does **not** get hidden — the debt is still shown as a debt under Rule H1, and the shortfall is still a visible term of the derivation — which is the distinction Rule B9 exists to protect |
| N2 | REQ-707 cited for the payin arrival date | **Closed** | Removed from both payin citations. REQ-702's criterion now cites Rule A3 and REQ-202, and carries an explicit note that REQ-707 governs a *withdrawal's* arrival date and does not apply. Add Funds Flow 1 step 3 and REQ-202's own criterion both now derive the date from the route. The two remaining REQ-707 mentions in Configuration are its own definition and its own EB-9 dependency note |
| N3 | Rule B1's excludes cell did not know about the sixth term | **Closed** | The withdrawable row now excludes *"…committed margin, any outstanding margin shortfall"*, matching Rule B4's terms |
| N4 | Rule C14 cited for event-queueing it does not describe | **Closed** | REQ-609 now cites REQ-622 alone, with the mechanism stated inline. No occurrence of the mis-citation remains |
| N5 | REQ-108's label not harmonised with REQ-403's | **Closed** | Relabelled `(Must Have, segment split deferred)`. Must-labelled headings now count 66, matching the register exactly. Its dual appearance — in Must-Have Core and in Won't Have for the segment split specifically — is now the identical treatment REQ-403 receives, which is a legitimate pattern rather than the both-buckets violation it resembled |

### Downstream re-check of the changed rules

The failure mode this pass exists to catch is an edit that satisfies its own finding and breaks a neighbour. Every reference to the five touched rules was re-read:

- **Rule B9** is cited in two other places: REQ-103's fifth criterion (a negative *component* is presented as negative) and a Balances edge case (a component negative because a position was closed). Both concern margin components rather than the withdrawable figure, so the new exception does not reach them and they remain correct as written.
- **Rule B1** is the anchor table for Rule B3's prohibition on defining any of the three figures from another. Adding the shortfall to its excludes cell keeps B1 and B4 describing the same quantity, which is what B3 depends on.
- **Rule A3** now carries the payin arrival obligation alone. It already required a route's arrival time to be known before the route is used, so it absorbs the obligation without amendment.
- **REQ-622** was already cited correctly alongside the removed C14 reference, so REQ-609 lost a wrong citation without losing a right one.
- **REQ-108's** relabelling touches nothing computational. Its appearances across the index — the digest row, the flow diagram subgraph, Core, Won't Have, MVP Scope, Future Scope, Out of Scope, EB-7 and the Phase 1 and Phase 5 rows — were checked and remain mutually consistent: the requirement ships, its segment split does not, and Phase 1 correctly omits it from the delivered set.

No new contradiction was introduced by the closure pass.

---

## Findings by Severity

### Blockers

**None.**

### Critical

**None.** All three from iteration 2 are closed and verified.

### Major — carried, none blocking design

- [ ] **M2 — Rules are numbered out of sequence.** Add Funds runs A9, A9a–A9d, **A12, A13**, A10, A11. Transactions & Statements runs L5, L5a–L5d, **L8a**, L6, L7, L8, L9. A reader searching for A10 passes two later rules to reach it. → Reorder, or renumber the late additions into place.

- [ ] **M6 — The instrumentation PRD is authoritative and undeclared.** `03-instrumentation/product-requirements-events-and-funnels.md` is 1,359 lines, matches the split-part naming convention, is named by the index as the file to read before writing a single event, and is absent from the frontmatter `parts:` array. A parts-aware reader will not open it; a reader following the index will. Its requirement IDs are consequently in no traceability matrix and nothing downstream is held to them. → Add it to `parts:` and bring its IDs into the matrix, or state in the index that it is a separate specification with its own lifecycle.

- [ ] **M7 — Implementation references remain, against a ticked gate.** `tradeNowHref`/`#/orders` was correctly replaced with `postFundingDestination` and a described destination. Still present: `web/app.js` and `./web/gen-comms.sh` in the communications generated-content fence, and `derive()` in the funds-screen note and REQ-621's surrounding text. The critical gate claiming no implementation detail anywhere remains ticked. → Remove them, or untick the gate and record the exception the way the document already does honestly for two other checklist items.

- [ ] **M8 — The message catalogue's source of truth is a prototype.** Communications §4 is fenced as generated from `web/app.js`, and the readable catalogue is an external URL a clone cannot resolve. Every user-facing message string is therefore outside this document's change control, and can change without the PRD changing. → Bring the catalogue in as authored content, or state in the index that message copy is owned and versioned elsewhere.

- [ ] **M9 — Five of seven part files still lack the breadcrumb.** Communications and Configuration now open with `[← Back to PRD index](product-requirements.md)`. Balances & Margin, Add Funds, Withdraw Funds, Transactions & Statements and Account Health link back only through their "Part N of…" line beneath the first heading. → Add the breadcrumb line to the five, keeping the existing orientation line beneath it.

### Minor

- [ ] **m1 — Parked REQ-304 content still reads as live inside Flow 1.** Branches A and B remain numbered branches carrying a parked marker, and one still reads "User chooses the faster route".
- [ ] **m2 — `minAdd` carries two confidence levels.** ₹100, settled with a named owner in Configuration; the same ₹100 marked `[PROPOSED: pending commercial confirmation]` in the index Hard Numbers table.
- [ ] **m3 — Support number and mailbox remain placeholders** under C-Q7, and email footers depend on them.
- [ ] **m4 — The Configuration row in Detailed Feature Specifications links to the file root** while every other row deep-links to a rules anchor.
- [ ] **m5 — The digest compresses four separate requirements into one cell.** REQ-601–604 share a digest row, which made sense when the row covered a range and makes the digest less scannable now that each exists separately.

---

## Section Ratings

| Section | Rating | Note |
|---|---|---|
| Problem Statement | **Strong** | Four structured teardowns of live products, four named failure modes, each with a specific observed instance. The Reality-Check Gate is answered honestly, including the two questions it fails |
| User Personas | **Strong** | Three personas with demographics, goals, pain points and four formal user stories each; every one has at least one flow |
| User Flows | **Strong** | Eleven flows with trigger, preconditions, numbered happy path, branches, error paths and postconditions. Transactions Flow 2 is now honest about the FMS/Ledger split |
| Acceptance Criteria | **Strong** | All 70 requirements carry EARS criteria. The reconstructed ones are testable against named amounts, channels, day counts and states |
| Business Rules | **Strong** | Rules carry their reasoning and, often, the observed failure that produced them. Rule B4 remains the standout: one definition, one home, referenced everywhere, and now with an exception clause that says what it does not hide |
| Requirements Register | **Strong** | 70 defined against 66 + 2 + 2, exact. Reversed from the weakest section in iteration 1 |
| Scope | **Strong** | Every requirement sits in exactly one bucket, with the two split requirements — REQ-108 and REQ-403 — handled identically and explicitly |
| Edge Cases | **Strong** | Sixteen cross-cutting cases plus feature-specific sets. Several are genuinely hard: the simultaneous trade and withdrawal, a reversal arriving before its original, two entries at identical timestamps |
| Non-Functional Requirements | **Strong** | Every number carries a stated basis or a `[PROPOSED]` marker with reasoning |
| Success Metrics | **Adequate** | Measurable and mapped, correctness invariants correctly separated as target-zero. Still depends on the undeclared instrumentation file — M6 |
| Risks & Constraints | **Strong** | Thirteen risks with impact, likelihood and mitigations that admit what they do not fix; seven assumptions each labelled as assumed |
| Cross-file consistency | **Strong** | No known contradiction remains |

---

## Strengths

- **The register reconciles exactly.** 70 defined requirement headings against 66 Must-Have, 2 Should-Have and 2 excluded, with every ID the index names resolving to a definition. This was the document's worst property in iteration 1 and is now among its best.
- **The withdrawals were the right call.** Four requirements that could not be grounded were removed rather than invented, and the document records where they went. Declining to author a Must-Have from a passing clause is the harder decision.
- **Every reconstruction names its source**, so a reader can check the derivation instead of trusting it.
- **Rule B9's new exception explains its own limits.** It states not only what floors but what does not get hidden as a result — the debt under Rule H1, the shortfall as a visible derivation term. An exception that names what it does not cover is far harder to over-apply.
- **Rule B4 survived three passes without losing its shape.** It gained a term and an exception clause and is still one definition in one place that every other file references.
- **The document is honest about its own gaps.** Two critical gates and one quality check remain ticked-with-explanation rather than silently passed, and the withdrawal security gap is recorded as a Phase 3 gate rather than buried as a risk.

---

## Risks Carried Into Design

1. **The four thinner reconstructions carry intent that was never author-confirmed.** REQ-602, REQ-603, REQ-609 and REQ-615 are grounded in shorter sources than the other six. Each is defensible and each says so at the point of use, but if one misreads the intent it ships as a Must-Have nobody wrote. — *Mitigation:* a targeted author read of those four, cheapest now and most expensive after the LLD.
2. **EB-9 gates Phase 1, not Phase 3.** The settlement calendar feeds Rule B4's unsettled-proceeds deduction, so the withdrawable figure is wrong on every holiday until a source is nominated. The PRD now records this; the schedule has to act on it.
3. **The instrumentation PRD sits outside every gate.** — *Mitigation:* M6.
4. **Message copy can change without the PRD changing.** — *Mitigation:* M8.
5. **REQ-303, REQ-307, REQ-707 and REQ-501 remain blocked on EB-9 and EB-8.** Correctly carried as Must-Have with owners and phases named, but they are commitments against dependencies with no resolution date.

---

## Questions for the Author

1. **Do REQ-602, REQ-603, REQ-609 and REQ-615 say what you intended those numbers to mean?** The four reconstructions with the shortest sources.
2. **Does the instrumentation PRD join `parts:`?** Deferred at the Stage 1 gate, raised again at iterations 1 and 2, still open.
3. **Who owns message copy after handover?** If the answer is the prototype, the PRD should say so rather than appearing to govern it.

---

## Recommendation

**Approved. Proceed to Stage 3 and write the unified HLD.**

The PRD is designable from: the register is trustworthy, every requirement has testable acceptance criteria, no rule contradicts another, and the decisions an architect would otherwise have had to guess — who selects a payment route, what a shortfall does to the withdrawable figure, which team delivers period reconciliation — are all settled in the document.

Two things to carry into Stage 3 rather than leave in this file:

- **EB-9 is a Phase 1 dependency**, not a Phase 3 one, because Rule B4's unsettled-proceeds deduction is measured in settlement days. The HLD's sequencing should reflect that.
- **The four thinner reconstructions** are the parts of this PRD most likely to be wrong. If the HLD touches shortfall messaging or payin confirmation content, those requirements are worth confirming before designing against them.

The five remaining Major items and five Minor ones are consistency and navigation defects. They belong in a housekeeping pass before this document is handed to a team that was not in these conversations, and none of them should hold design.

---

## Review Metadata

- **Files read:** 8 (index + 7 declared parts), 3,024 lines
- **Requirement headings defined:** 70 — 66 Must Have (2 of them with an explicitly deferred second phase), 2 Should Have, 2 excluded (parked, relocated)
- **Register claims:** 68 active, 66 Must-Have, 2 Should-Have — matches the headings exactly
- **Requirement IDs named in the index and undefined:** 0
- **Findings:** 0 Blocker, 0 Critical, 5 Major, 5 Minor
- **Iteration-2 conditions verified closed:** 5 of 5
- **Iteration-1 findings verified resolved:** 10 of 10
- **Split-File Consistency:** 8 of 10 pass; the two failures are M6 and M9
- **Not read as part of this review:** `03-instrumentation/product-requirements-events-and-funnels.md`, still outside `parts:` — finding M6
