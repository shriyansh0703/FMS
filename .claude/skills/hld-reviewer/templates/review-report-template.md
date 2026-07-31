# HLD Review Report: [Design Name]

## 1. Verdict
* **Status:** [Ready for Implementation / Ready with Conditions / Not Ready] — **Score: [X.X/10]**
* **Reviewed Stage:** [Prototype / Startup-MVP / Growth / Enterprise] — [brief rationale for stage calibration]
* **System Shape:** [e.g., "Full-stack: React client + BFF + 3 backend services + Postgres" / "Backend-only data platform" / "Frontend-only, existing API" — states which layers this review actually covers]

[One-paragraph summary explaining the overall assessment, core architectural readiness across every
layer the document covers, and the primary rationale for the verdict.]

### Executive Scorecard & Confidence
[A short, honest note on how much to trust this review: is the document detailed enough to review with
high confidence, or are sections too sparse? Name any dimension scored as "entirely absent," and any
dimension marked not applicable to this system's shape.]

| Dimension | Score | Notes |
|---|---|---|
| 1. Requirements & Scope Fidelity | X/10 | |
| 2. Architecture & Trade-off Rigor | X/10 | |
| 3. System Design & Team/Module Boundaries | X/10 | |
| 4. Data & State Architecture | X/10 | |
| 5. API Design & Contracts | X/10 | |
| 6. Security, Compliance & Data Privacy | X/10 | |
| 7. Reliability, Failure Handling & DR | X/10 | |
| 8. Performance & Scalability | X/10 | |
| 9. Observability, Deployment & Operability | X/10 | |

---

## 2. Requirement Traceability (Three Doors)

* **Door 1 - Coverage:** [Pass / Pass with concerns / Fail]
* **Door 2 - Fidelity:** [Pass / Pass with concerns / Fail]
* **Door 3 - Readiness:** [Pass / Pass with concerns / Fail]

### Requirement Coverage Table
*(Populated if a PRD or explicit requirements list was supplied or inferred)*

| Requirement ID / Name | Category | Status | Notes / Traceability |
|---|---|---|---|
| [REQ-01: Name] | [Functional / NFR] | [Covered / Partial / Missing] | [Traceability note or reference to finding] |

---

## 3. Findings by Category

*(Group ALL findings — frontend, backend, data, and infra alike — under the nine dimensions below.
Never split into a separate "frontend findings" section and "backend findings" section; a
cross-layer finding is filed once, under its root-cause dimension, with cross-references noted. Omit
a dimension header if it has no findings. Include 🔴 Blockers, 🟠 Majors, 🟡 Minors, and ⚪ Nits.)*

### [Requirements & Scope Fidelity / Architecture & Trade-off Rigor / System Design & Team-Module Boundaries / Data & State Architecture / API Design & Contracts / Security, Compliance & Data Privacy / Reliability, Failure Handling & DR / Performance & Scalability / Observability, Deployment & Operability]

> **[FIND-ID] [Finding Title]**
> * **Severity:** [🔴 Blocker / 🟠 Major / 🟡 Minor / ⚪ Nit]
> * **Dimension:** [Match header above]
> * **Layer(s):** [Client / BFF / Service / Data / Infra — one or more]
> * **Observation (Evidence):** *Section X, [Section Name]: [specific description of what the HLD says or fails to say]*
> * **Impact (Risk):** [Explain the specific, practical engineering impact. Do not use generic summaries — teach exactly HOW data leaks across SSR requests or across services with no data-ownership rule, HOW specific browser APIs cause hydration errors, HOW a missing idempotency key causes a double charge, or WHY team/service scaling breaks down without ownership.]
> * **Recommendation (Fix):** [Concrete, actionable change that closes the gap]

---

## 4. Missing Information

*(Bullet list of critical or important architectural details the HLD should have specified but
omitted, across any layer)*
* **[Missing Item 1]**: [e.g., Unspecified server-side request isolation in the SSR entitlement path]
* **[Missing Item 2]**: [e.g., No idempotency key on the `POST /orders` mutation despite a stated client-retry policy]
* **[Missing Item 3]**: [e.g., Absence of cost analysis explicitly covering SSR compute, WebSocket fan-out, and database scaling]

---

## 5. Risks Identified

*(Bullet list summarizing the highest-stakes risks, cross-referencing severities and findings above)*
* **[🔴 Blocker Risk]**: [Summary of risk, cross-referencing FIND-01]
* **[🟠 Major Risk]**: [Summary of risk, cross-referencing FIND-02]

---

## 6. Questions for the Author

*(Direct, technical questions the author needs to answer before final approval or implementation —
span every layer the document covers)*
1. [Question 1: e.g., How does the order-placed event's consumer handle a duplicate delivery if the message broker only guarantees at-least-once?]
2. [Question 2: e.g., How does the WebSocket stream handle client sequence-key reconciliation when an HTTP polling response arrives concurrently?]

---

## 7. Suggested Improvements

*(Concrete, actionable improvements — not a repeat of every finding, but the highest-leverage fixes,
ordered by impact, across the whole system)*

1. **[Top Fix 1]**: Addresses [FIND-XX] → Resolves [Requirement/Risk].
2. **[Top Fix 2]**: Addresses [FIND-XX] → Resolves [Requirement/Risk].

### Stress-Test Matrix Results
*(For each of the seven scenarios: state what the document's own design would actually do, and
whether it held up. Mark any scenario not applicable to this system's shape as N/A with a one-line
reason.)*
* **1. Traffic spike:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning, including calling out if the document relies on undocumented assumptions to pass]
* **2. Dependency outage:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]
* **3. Data inconsistency:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]
* **4. Mutation under network failure:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]
* **5. Deployment failure:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]
* **6. Organizational scaling:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]
* **7. [Domain-Specific Scenario Name]:** [Held up / Failed / Implicit but undocumented / N/A] — [Reasoning]

### What's Genuinely Strong
*(A short, honest list, spanning whichever layers are strong. A review that never says anything
positive is uncalibrated and makes negative findings harder to trust.)*
* [Strength 1]
* [Strength 2]
