# Scoring Rubric

Score each of the nine dimensions 1-10, then synthesize one overall verdict. This is a judgment call
informed by the nine scores, not a mechanical average — a Blocker in any single dimension caps the
overall verdict.

## Dimensions and default weights

| # | Dimension | Weight | Core question |
|---|---|---|---|
| 1 | Requirements & Scope Fidelity | 10% | Does everything trace back to a real, quantified requirement, with a clear chain to findings? |
| 2 | Architecture & Trade-off Rigor | 14% | Is every major choice justified against a named, costed alternative? |
| 3 | System Design & Team/Module Boundaries | 10% | Will this stay maintainable as the codebase, data, and org grow — ownership, boundaries, governance? |
| 4 | Data & State Architecture | 14% | Is there one clear source of truth for every piece of data, client-side and server-side, with no silent conflicts? |
| 5 | API Design & Contracts | 10% | Are the contracts between independently-deployed pieces actually specified well enough to build against? |
| 6 | Security, Compliance & Data Privacy | 14% | Would this survive a real attacker or a real audit, on every layer, incl. GDPR/CCPA/HIPAA where relevant? |
| 7 | Reliability, Failure Handling & DR | 10% | Does the system fail in a controlled way, and recover, with a stated RTO/RPO? |
| 8 | Performance & Scalability | 10% | Fast and correct at the stated scale, with concrete mechanisms, including rendering-specific failure modes? |
| 9 | Observability, Deployment & Operability | 8% | Can a real team run this, afford it, and survive its third-party/infra dependencies? |

Adjust weights explicitly when the domain or maturity stage clearly demands it (e.g., weight Security
and Data & State Architecture higher for financial/health systems; weight System Design and
Operability lower for a genuine single-developer prototype, since team/module boundaries and cost
governance aren't yet real constraints at that stage; weight API Design near-zero if the HLD is a
single self-contained service with no external consumers). If a dimension has no applicable content in
this HLD (e.g., no UI layer at all, so no rendering concerns within Performance & Scalability), say so
and reweight the remaining dimensions rather than scoring it as absent-and-penalized. Always say when
and why you deviated from the defaults.

## Maturity stage — score relative to the stated (or inferred) stage, not an absolute bar

State the stage explicitly in the report: **Prototype**, **Startup/MVP**, **Growth**, or
**Enterprise**. Use it to calibrate expectations, not to excuse real gaps:

- **Prototype** — proving an idea works. Missing formal team-boundary, API-versioning, or cost
  governance is *expected*, not a finding. Security and data-correctness basics (no client-side-only
  or caller-asserted-only auth, no shipped secrets, no unbounded distributed writes on financial data)
  still apply in full — those are never stage-relative.
- **Startup/MVP** — shipping to real users, small team. Lighter system-design and vendor-risk rigor is
  acceptable if the document names a trigger for revisiting it later (e.g., "reassess service
  boundaries once a second team owns part of this"). Absence of that trigger is a legitimate finding.
- **Growth** — real scale, likely more than one team and/or more than one service. System design,
  data-ownership clarity, contract versioning, and vendor/cost risk become first-class concerns; gaps
  here should be scored and weighted at full severity.
- **Enterprise** — full rigor across all nine dimensions expected; gaps that would be tolerable at
  Startup stage are Major or Blocker here.

If the document doesn't state its stage, infer it from team size, timeline, and stated scale, and say
so. Do not silently default to Enterprise-level expectations for a document that clearly reads as an
MVP.

## Scale (per dimension)

- **9-10 — Exemplary.** Specific, alternatives named and correctly rejected with real costs, nothing
  hand-waved, appropriate to the stated stage.
- **7-8 — Solid.** Minor gaps or slightly generic justification; nothing that blocks a build at this
  stage.
- **5-6 — Adequate but risky.** Right shape, at least one important justification missing or thin for
  the stated stage.
- **3-4 — Weak.** Template-shaped rather than reasoned; would be sent back at a real review regardless
  of stage.
- **1-2 — Absent or actively wrong.** Missing, or the guidance given would cause real harm if followed.

## Handling a dimension that is entirely absent from the document

Some HLDs skip a whole area (no Security section at all; no discussion of data ownership beyond a
datastore name; no mention of team/service boundaries). Don't silently skip scoring it or leave it
blank — define the outcome explicitly, and distinguish "absent because genuinely not applicable to
this system's shape" (see the reweighting note above) from "absent because the author skipped it":

- **Score:** a genuinely-applicable-but-entirely-absent dimension scores **2/10** by default — not 0
  (0 implies actively harmful guidance was given, which requires content to exist) and not skipped
  (which would understate the gap). If the dimension is safety- or correctness-critical (Security,
  Data & State Architecture, Reliability for a high-stakes system), the absence is automatically at
  least a **Major** finding; for Security specifically, treat total absence as a **Blocker** if the
  system handles authentication, payments, or sensitive data at all.
- **Confidence adjustment:** note in the Confidence & Completeness section of the report that this
  dimension's score reflects total absence, not a judged weak attempt — this is a different kind of
  finding than "present but poorly reasoned," and the report should not blur the two.
- **Do not infer content that isn't there** to be generous — a missing section is a missing section,
  even if you can guess what the author probably intended.

## Producing the overall verdict

State one of three verdicts in plain language, paired with the numeric score and the stage:

- **Approve** — no Blockers; at most a few Majors with clear fixes; appropriate for its stated stage.
- **Approve with required changes** — one or more Majors (or a dense cluster of Minors pointing at the
  same underlying gap) that must be resolved first, but the core architecture is sound for its stage.
- **Do not build from this yet** — one or more Blockers. Name each explicitly and describe what a
  corrected version needs to demonstrate.

A bare number is not a verdict. "6.5/10 — Approve with required changes — reviewed as a Growth-stage
system" is the minimum useful output; always pair all three.
