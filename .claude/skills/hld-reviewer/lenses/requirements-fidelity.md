# Lens 1 — Requirements & Scope Fidelity

**Core question:** does the architecture actually solve the problem that was stated, with real
numbers, on every layer it touches — and does every finding you raise trace back to a real
requirement rather than a generic best-practices checklist?

## What to check

- [ ] **Functional scope is concrete.** The document names actual user journeys/features and, where
      relevant, actual system-to-system flows — not just a tech stack or a service list.
- [ ] **Non-functional requirements have numbers**, for whichever layers are in scope: latency,
      availability, concurrent-user/RPS/TPS targets, bundle-size budgets, data-volume/growth
      projections. "Fast" and "scalable" alone are not requirements — flag every unquantified
      performance/scale claim used as if it were one.
- [ ] **Assumptions are stated, not smuggled.** Anything inferred rather than stated should appear
      explicitly, not show up pre-baked into a decision three sections later.
- [ ] **Constraints are named if they plausibly exist** — regulatory, organizational, technical,
      contractual (SLAs owed to customers), or infra (must run in an existing cluster/cloud account).
- [ ] **Scope matches stakes, in both directions.** Flag over-engineering (microservice-grade
      complexity, or a heavy client-state architecture, for a low-traffic internal tool) as readily as
      under-engineering.
- [ ] **Team size, timeline, and stage are accounted for**, and actually constrain later
      recommendations rather than being mentioned once and ignored (cross-check against the maturity
      stage established in Step 1 of `SKILL.md`).
- [ ] **Cross-layer requirements are reconciled.** If the document states a client-facing requirement
      (e.g., "live updates within 1s") and a backend requirement (e.g., "eventual consistency,
      propagation within 5s"), those two numbers must actually be compatible — flag the mismatch if
      they aren't, rather than letting each layer's section pass independently.

## Requirement → Risk → Finding → Fix: the traceability chain

Every finding in the final report should be constructible as this chain, and the report format
requires you to show it explicitly, not just assert a conclusion:

1. **Requirement** — what does the system actually need to satisfy (stated, or a reasonable inference
   you flag as such)?
2. **Risk** — what goes wrong if the document's current approach doesn't satisfy it?
3. **Finding** — the specific gap in the document, with a section reference.
4. **Recommended fix** — the concrete change that would close the gap.

A finding that can't be traced back to a real requirement is either a nitpick (label it as such,
severity Nit) or a sign you're applying a generic checklist item that doesn't actually apply to this
system — reconsider whether it belongs in the report at all. This chain is what separates a review
from a list of things a reviewer personally would have done differently.

## What a strong answer looks like vs. a weak one

- Strong: "Public marketing pages need SEO and sub-2.5s LCP on 4G; the authenticated dashboard has no
  SEO requirement and is used by ~500 internal staff hitting a service with a stated 200 RPS peak, so
  the backend can prioritize simplicity over horizontal auto-scaling at this stage." — traceable,
  numeric, spans layers, and visibly drives later decisions on both sides.
- Weak: "The system should be fast, secure, and scalable." — unfalsifiable, nothing downstream can be
  checked against it, and no finding can be traced back to it.

## Severity guidance

- No non-functional requirements anywhere → **Major** (everything downstream, on every layer, becomes
  unfalsifiable).
- One missing number in an otherwise well-reasoned section → **Minor**.
- Scope dramatically mismatched to stated stakes or stage → **Major**.
- A client-facing and backend-facing requirement that are numerically incompatible (e.g., a "real-time"
  UI promise with no matching backend propagation target) → **Major**.
