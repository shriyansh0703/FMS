# Lens 2 — Architecture & Trade-off Rigor

**Core question:** is the structural shape of this system right — on the client, at the service
boundary, and in the data layer — and was every major choice actually reasoned through against a
real, costed alternative, or just asserted?

## Mandatory trade-off ledger

For **every** major decision — framework/meta-framework, rendering strategy, state-management
library, monolith-vs-microservices, monolith-vs-micro-frontend, API style (REST/GraphQL/gRPC/tRPC),
messaging model (sync request/response vs. async/event-driven), datastore choice, routing approach —
you must be able to fill in this exact ledger from what's written. A decision missing even one row is
not fully reasoned, regardless of whether the choice itself is reasonable:

| Row | Question |
|---|---|
| Choice | What did they choose, unambiguously? |
| Rejected alternative | What specific alternative was considered and rejected — not "X is popular" |
| Reason specific to this system | Why did the alternative lose *here* (team familiarity, SEO need, real-time need, data sensitivity, consistency requirements, scale, cost)? |
| Cost of the chosen approach | What does this choice give up? A choice presented as risk-free is a smell. |
| Migration implication | If this needs to be reversed later, what does that cost, and is there a stated trigger for reconsidering it? |
| 10x behavior | What happens at 10x scale (traffic, data volume) or 10x team size — does the document say what would need to change? |

If a decision is missing 2+ rows, that is a **Major** finding on its own — this is what separates a
staff-level review from a pass/fail read of whether the tech choices are reasonable in isolation.

## Structural checks

- [ ] **Service/module separation is justified**, not just diagrammed. Why does this need a separate
      service (or a separate micro-frontend, or a separate package)? Could it live inside an existing
      one? Are all upstream/downstream dependencies identified?
- [ ] Rendering/delivery strategy is explicit per route-type (if a UI layer exists), not one global
      answer — unless the system is genuinely simple enough that one answer is honestly correct.
- [ ] Layering and dependency direction is stated for any system with real complexity — client → BFF
      → service → data store, and which direction calls are allowed to flow.
- [ ] Sync vs. async is a deliberate choice per interaction, not a default — is it clear which calls
      are synchronous request/response and which are event/queue-driven, and why?
- [ ] False certainty on genuine industry toss-ups (REST vs. GraphQL, monolith vs. microservices,
      monolith vs. micro-frontends, SQL vs. NoSQL, WebSocket vs. SSE, cookie-session vs. bearer-JWT) —
      presenting one as obviously correct with no counter-case is thin reasoning even if the choice is
      defensible.
- [ ] Reversibility is proportionally treated — hard-to-reverse choices (datastore, auth model, service
      decomposition) get more scrutiny than easy-to-reverse ones (see the ledger's migration-implication
      row).
- [ ] Stage/scale context (from the SKILL.md maturity-stage step) actually changes at least one real
      recommendation — if the document would read identically at Prototype and Enterprise stage, the
      "tailored to context" framing is decorative.
- [ ] Complexity is proportional to the problem — as readily flag an over-engineered
      microservice-per-feature decomposition for a low-traffic system as an under-engineered monolith
      for a system with genuinely independent scaling/ownership needs.

## Legacy Replacement & Migration Strategy (when this HLD replaces or supersedes an existing system)

If the document describes replacing, rewriting, or superseding an existing production system, treat the
migration path itself as a first-class design decision — not an implementation detail to sort out later.

- [ ] **A named migration pattern is stated** — big-bang cutover, Strangler Fig (incremental
      route-by-route or feature-by-feature replacement behind a facade), parallel-run/shadow traffic,
      or an explicit justification for why big-bang is acceptable here (e.g., low-stakes internal tool,
      small data volume). Silence on *how* the cutover happens, with only the end-state architecture
      described, is itself a finding.
- [ ] **Data migration/backfill is addressed** — one-time backfill vs. continuous dual-write during a
      transition period, how consistency between old and new stores is verified during the transition,
      and what happens to data written to the legacy system after migration notionally "completes."
- [ ] **Phase-out criteria are concrete** — what specific, measurable condition marks each legacy
      component/route as safe to decommission (traffic fully cut over and verified, not just "once the
      new system is live"), rather than the legacy system lingering indefinitely by default.
- [ ] **Rollback during migration is addressed** — if the new path misbehaves partway through a phased
      migration, can traffic/data ownership be reverted to the legacy system for the affected slice, or
      is the migration a one-way door once started?
- [ ] **Dual-running cost is acknowledged** — operating both systems simultaneously during the
      transition has a real infrastructure and cognitive-load cost; a migration plan that ignores this
      (or has no stated end date for dual-running) is thin.

## What a strong answer looks like vs. a weak one

- Strong: "Chose a BFF-mediated cookie session over bearer JWTs in browser memory: the BFF holds
  tokens server-side, reducing XSS token-exfiltration exposure at the cost of an added CSRF surface
  (addressed in Security) and one more service to operate. Migrating to bearer tokens later would
  require reissuing all sessions — acceptable churn, so defer that decision until this becomes a
  multi-team, independently-deployed API surface."
- Strong (backend): "Chose an async event (order.placed) over a synchronous call from checkout to
  fulfillment: fulfillment's own p99 is 2s and checkout can't afford to wait on it. Cost: eventual
  consistency means the UI must show 'processing' rather than an immediate confirmation (see API/UI
  contract in Lens 5). Revisit if fulfillment ever needs to reject an order synchronously."
- Weak: "We use JWT-based auth for stateless, scalable auth." / "We use microservices for
  scalability." — fills in only the Choice row.

## Severity guidance

- A foundational decision (framework/rendering/auth/real-time transport/service decomposition/
  datastore) missing 2+ ledger rows → **Major**.
- A peripheral decision (a specific chart library, a specific queue vendor for a low-volume topic)
  missing the same rows → **Minor**.
- No layering/dependency-direction statement at all on a system with real complexity → **Major**,
  escalate to **Blocker** if it directly threatens correctness or security elsewhere.
- A synchronous call placed where the document's own stated latency/availability numbers make it
  untenable (e.g., a checkout path synchronously blocking on a non-critical, slow downstream) →
  **Major**.
- A system explicitly replacing an existing one with no stated migration pattern at all (big-bang,
  Strangler Fig, or parallel-run) → **Major**, escalate to **Blocker** if the legacy system handles
  financial/safety-relevant data and the document gives no cutover or rollback story whatsoever.
- No data-migration/backfill story for a replacement system with a stateful legacy predecessor →
  **Major**.
- No decommissioning/phase-out criteria for the legacy system (it's simply left running indefinitely
  with no stated end condition) → **Minor** to **Major** depending on the cost/risk of prolonged
  dual-running.
