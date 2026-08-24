# HLD Review Report: Settlement And Funding Experience (Run 004) — Amendment Re-approval

## 1. Verdict

* **Status:** Ready for Implementation — **Score: 9.1/10**
* **Reviewed Stage:** Enterprise — a running system moving real money for 500,000 traders under a regulatory obligation.
* **System Shape:** Full-stack brownfield delta. Unchanged from the previous pass.
* **Revision reviewed:** `hld.md` v5. The previous pass approved v3 at 9.1/10; v4 added the Stage 6 amendment and v5 corrected it.
* **Scope of this pass:** the amendment and its consequences, plus a check that it did not break anything the earlier passes established. The architecture itself was reviewed in full twice and is unchanged — no decision in §§1–7, 9–16 or 18–22 has moved.

The amendment is sound and now internally consistent. It records two endpoints the Stage 6 LLD review found in `lld-backend.md` and not here, and — correctly — treats that as an HLD-layer decision rather than something the LLD could settle on its own. The mechanism it adopts is better than the one v3 specified, and §8.5 says so with the reasoning attached rather than quietly replacing the text.

**This pass found two real defects in v4 and both are closed in v5.** The amendment superseded §13.2 without editing it, so v4 contained a section instructing an implementer to carry the screen-open moment on the payment-memory read and another section explaining why that cannot be built. The §20 decision table carried the same stale mechanism in its "chosen" column. An amendment that supersedes but leaves the superseded text in place is worse than no amendment, because a reader arriving at §13.2 has no signal to look elsewhere.

Score is unchanged at 9.1. The amendment neither improves nor degrades the architecture; it documents a mechanism already reviewed at LLD depth.

### Executive Scorecard & Confidence

Confidence is **high**. This is the third pass over this document and the amendment surface is small and well bounded.

| Dimension | Score | Change | Notes |
|---|---|---|---|
| 1. Requirements & Scope Fidelity | 9/10 | — | Unchanged |
| 2. Architecture & Trade-off Rigor | 9/10 | — | §20 gains a corrected row; the reasoning quality is consistent with the rest |
| 3. System Design & Team/Module Boundaries | 9/10 | — | Unchanged |
| 4. Data & State Architecture | 9/10 | — | Unchanged. The amendment adds no state |
| 5. API Design & Contracts | 9/10 | — | Two endpoints and three optional fields, all additive, all matching the LLDs exactly |
| 6. Security, Compliance & Data Privacy | 9/10 | — | The new endpoints carry no trader-supplied content and no new disclosure |
| 7. Reliability & Failure Handling | 8/10 | — | Unchanged |
| 8. Performance & Scalability | 9/10 | — | The new endpoints add one write-shaped call and one cacheable read per screen open, immaterial against §5.1's modelled load |
| 9. Observability & Operability | 9/10 | — | Four metrics added; all eleven PRD tracking events now map |

---

## 2. Requirement Traceability

* **Door 1 — Coverage:** Pass. All seven requirements remain covered; the amendment adds instrumentation for four PRD events that had none.
* **Door 2 — Fidelity:** Pass. The amendment changes a carrier, not an intent — §8.5 states that the design intent of §13.2, reporting at open rather than at submission, is preserved exactly.
* **Door 3 — Readiness:** Pass, and improved. v3 named a mechanism that could not be built; v5 names one that can.

### Requirement Coverage Table

| Requirement | Status | Change from the previous pass |
|---|---|---|
| REQ-SF-01 | Covered | None |
| REQ-SF-02 | Covered | None |
| REQ-SF-03 | Covered | None |
| REQ-SF-04 | Covered | K4's event now exists, so the requirement's effect is measurable rather than only implementable |
| REQ-SF-05 | Covered as deferred | None |
| REQ-SF-06 | Covered | K6's tag now has a stated client-side source |
| REQ-SF-07 | Covered | As above for the withdrawal half |
| Guardrails G1–G4 | Covered | G1 and G2 gain a denominator; G4's carrier is now buildable |
| PRD tracking table | **Covered — all eleven events** | Four events had no metric in v3. Verified row by row rather than asserted |

---

## 3. Findings

### Closed in v5

> **[AMD-01] The amendment superseded §13.2 without editing it**
> * **Severity:** 🟠 Major — in v4. **Closed in v5.**
> * **Dimension:** API Design & Contracts
> * **Observation:** *v4's §8.5 stated that `POST /funds/screen-open` supersedes the earlier claim that the screen-open moment rides the payment-memory read. §13.2 still contained that claim, in full, with no pointer to §8.5.*
> * **Impact:** An implementer reading §13.2 — which is the client section, and therefore the section a frontend engineer reads first — would have built the mechanism §8.5 explains cannot work: a recording side effect inside a GET the client retries twice, counting one screen open up to three times. The two sections would each have looked authoritative in isolation. This is the failure mode an amendment is supposed to prevent, arriving through the amendment itself.
> * **Resolution:** §13.2 now names the endpoint, points at §8.5 for the reasoning, and states that both money-movement screens report — which also carries G1 and G2's denominator, a fact §13.2 had never mentioned.

> **[AMD-02] The decision table's chosen column carried the stale mechanism**
> * **Severity:** 🟡 Minor — in v4. **Closed in v5.**
> * **Dimension:** Architecture & Trade-off Rigor
> * **Observation:** *§20's G4 row read "Reported at screen open, on the memory read" as the chosen option.*
> * **Impact:** §20 is the table a reviewer or a new engineer scans to learn what was decided and why. A stale chosen column there is more misleading than stale prose, because the table's whole purpose is to be the summary you trust without reading the sections.
> * **Resolution:** The row now names the dedicated endpoint as chosen and lists both rejected alternatives — submission-only reporting and the memory-read carrier — each with its specific failure.

### Open

None. No finding at any severity survives in v5.

---

## 4. Missing Information

None.

---

## 5. Risks Identified

The register in §21 is unchanged and remains accurate. The amendment introduces no new risk: both endpoints are additive, neither sits on a payment path, and `GET /funds/features` failing is handled on the client side by the persisted-fallback rule in `lld-frontend.md` §11.

One observation rather than a risk: the deposit request now carries three optional telemetry fields. `lld-review.md` flags at 🟢 that a fourth would make the case for a separate telemetry call stronger than the case for riding an existing one. That threshold is not reached.

---

## 6. Questions for the Author

None.

---

## 7. Suggested Improvements

None blocking. The amendment is complete and the document is internally consistent.

### Stress-Test Matrix Results

Re-run only where the amendment could have changed the outcome. The other four hold as assessed in the previous pass.

* **2. Dependency outage:** **Held up.** `GET /funds/features` is a new dependency for the client, and its failure mode is designed rather than assumed — the last known value persists, and only a never-fetched client defaults to all-on. A feature switch whose availability gates a shipped feature is exactly the kind of addition that usually creates a new outage path; this one does not.
* **5. Deployment failure:** **Held up.** Both endpoints are additive and neither is required for any existing flow to work. A client deployed before the server sees two 404s, degrades to no telemetry and default-on switches, and funds normally.
* **7. Domain-specific — the trader who funds twice in a minute:** **Held up.** Unaffected by the amendment; §11.1's invalidation rule is untouched.

### What Is Genuinely Strong

* **The amendment exists at all.** The LLD had a better mechanism than the approved HLD and documented the deviation rather than hiding it; the resolution was to move the better mechanism up a layer rather than let the two documents disagree. That is the correct handling and it is rarer than it should be.
* **§8.5 states what it supersedes.** Not "the design is X" but "the design is X, this document previously said Y, and here is why Y cannot be built". A reader who remembers v3 is told directly that their memory is out of date.
* **Carried forward and unchanged:** §7.2.1's four-fact transaction, §11.1's narrowing of the caching argument rather than overriding it, the structural exclusions pinned by assertions, and the refusal to invent an RTO in §15.4.

---

## Verdict

Two defects found in the v4 amendment, both closed in v5. No finding survives. The architecture is unchanged from the pass that scored it 9.1/10, and the amendment documents a mechanism that the Stage 6 LLD review had already examined in more detail than this stage normally reaches.

All three doors pass. `hld.md` v5, `lld-backend.md` v3 and `lld-frontend.md` v3 now describe the same system.

**Verdict:** APPROVED
