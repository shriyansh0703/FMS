# Lens 9 — Observability, Deployment & Operability

**Core question:** can a real team actually test, run, debug, deploy, and afford this in production —
and survive its third-party and infrastructure dependencies failing or changing terms? This is the
lens most often shortchanged in a purely technical review, and it's checked deliberately here rather
than left as an afterthought.

## Testing Strategy & Quality Assurance

- [ ] Coverage goes beyond unit tests where complexity warrants it — component/integration for
      critical UI flows, E2E for the core user journey, contract tests across every service boundary
      and BFF/backend boundary.
- [ ] The highest-risk logic (state-machine/financial/authorization logic, on any layer) has
      explicitly named test coverage.
- [ ] Safe fixtures/test environments are used, not production data or live external systems, for any
      high-stakes domain.
- [ ] **Testability is a design property, not an afterthought** — can dependencies actually be mocked/
      stubbed at the service boundary (named interfaces, dependency injection, sandboxed vendor APIs),
      or does the design implicitly require hitting real third-party services/production infra to test
      anything meaningfully?
- [ ] **Load/performance testing is addressed** for any system with a stated scale or latency target —
      is there a plan to actually validate the design against that target before launch (load-test
      environment, target RPS/concurrency, pass/fail criteria), or is the scale target asserted with no
      way to verify it (cross-reference Lens 8's scalability checks)?
- [ ] **Chaos/fault-injection testing is at least considered** for Growth/Enterprise-stage systems with
      real availability stakes — deliberately killing a dependency, a pod, or a zone to verify the
      failure-handling story in `lenses/reliability-and-failure-handling.md` actually holds, rather than
      assuming it does because it's documented.
- [ ] **Data migrations and schema changes have a test story** — is there a plan to validate a migration
      against production-shaped data (volume, edge cases) before it runs for real, and is a rollback
      path validated too?

## Observability

- [ ] Logging/monitoring covers what would actually matter in an incident (route/render failures, API
      latency/error rates, real-time/connection health, database query performance, queue depth/
      consumer lag).
- [ ] Distributed tracing is addressed for any system with more than one hop between client and data
      (client → BFF → service → service → datastore) — without it, "which layer is actually slow"
      becomes a guessing game in production.
- [ ] Alerting is tied to something concrete (an error-rate threshold, a latency percentile, a queue
      backlog size), not just "we have monitoring."
- [ ] Sensitive data is explicitly excluded from logs/analytics if the domain has any
      (cross-reference Lens 6).
- [ ] **SLOs, not just raw metrics.** Collecting latency/error data is not the same as having a
      Service Level Objective — does the document state a target (e.g., "p95 API latency < 500ms,
      measured over a rolling 28 days," "99.9% of sessions error-free") that alerting and incident
      response are actually anchored to? Rich telemetry with no SLO has visibility with no defined bar
      for "is this actually healthy."
- [ ] **Session replay / user-session debugging**, if used or plausibly needed on the client side. If
      used, it must be held to the same sensitive-data-scrubbing bar as logs/analytics — session
      replay is a common, easy-to-miss channel for PII/financial data leaking into a third-party tool,
      since it can capture full DOM/input state unless explicitly masked.

## Deployment & release

- [ ] A real pipeline is described (build/test/promote) for every independently-deployed piece —
      frontend, each service, and database migrations — with secrets handling addressed.
- [ ] Rollback is addressed for every layer — can a bad frontend release, a bad service deploy, or a
      bad schema migration actually be reverted quickly? Canary/gradual rollout and feature flags need
      a stated kill-switch story.
- [ ] Feature flags gating UI visibility are fine; flags substituting for real server-side
      authorization of sensitive functionality is a cross-cutting **Blocker** (cross-reference Lens 6).
- [ ] **Infrastructure/deployment target is named** (Kubernetes, serverless, VMs, managed PaaS) with
      autoscaling behavior addressed for any component expected to scale.
- [ ] **Disaster recovery is addressed** for the system's data — backup frequency, restore process, and
      a stated (even rough) RTO/RPO for anything where data loss would be consequential.
- [ ] **Schema/data migrations are treated as a deployment concern**, not an implementation detail —
      is there a stated approach for running them without downtime and for rolling them back if a
      migration fails partway?

## Vendor & dependency risk assessment

Systems increasingly depend on third-party services (and managed infrastructure) that aren't
"the architecture" in the traditional sense but are just as capable of causing an outage or a costly
migration. For every consequential third-party dependency named (auth providers, analytics vendors,
CMS platforms, feature-flag systems, payment processors, AI/model providers, monitoring vendors,
managed cloud services):

- [ ] **Outage handling** — what does the system do if this vendor is down? Fallback, degraded mode, or
      total failure?
- [ ] **Vendor lock-in** — is the integration behind an internal abstraction that would make switching
      feasible, or is the vendor's SDK/API called directly throughout the codebase/services?
- [ ] **SLA assumptions** — does the document assume an uptime/latency guarantee from the vendor
      without stating it, or without a plan for when the vendor doesn't meet it?
- [ ] **Migration difficulty** — if this vendor needs to be replaced, how costly would that be, and
      does the document acknowledge it?

Silence on all of the above for a system that clearly depends on 2+ consequential third-party services
is itself a finding, not just an absence of nice-to-have detail. See `knowledge/vendor-and-cost-risk.md`
for deeper guidance, including how to weigh dependency tiers.

## Cost & economic analysis

A technically correct architecture can still be economically unviable — this is frequently invisible
in a purely technical review and should not be. Check whether the document addresses cost implications
of its major choices, proportional to the stated stage (weight this more heavily for Startup/MVP-stage
systems where cost can be existential; note if a well-funded Enterprise system reasonably deprioritizes
it):

- [ ] **Rendering/compute cost** — SSR/edge-rendering compute at stated traffic, vs. a cheaper
      static/CSR alternative, if relevant.
- [ ] **Real-time/streaming infrastructure cost** — WebSocket/streaming connection scaling cost at
      stated concurrency.
- [ ] **Data/infrastructure cost** — database instance sizing, storage growth, cross-region transfer,
      if plausibly significant at stated scale.
- [ ] **Search/AI infrastructure cost** — if the system uses hosted search or AI/model inference, is
      cost-per-request or cost-at-scale addressed at all?
- [ ] **Operational complexity cost** — does an architecturally "more correct" choice (micro-frontends,
      a dedicated BFF layer, a service split) get evaluated against the operational/engineering-time
      cost of running it, or is complexity cost treated as free?

This doesn't require precise dollar figures — a document that at least reasons about *relative* cost
passes; a document that never considers cost as a factor in any decision, for a cost-sensitive stage,
is a finding.

## Severity guidance

- No testing strategy beyond a one-liner, on a system with real complexity on any layer → **Major**.
- No load/performance-testing plan for a system that states an explicit scale, throughput, or latency
  target → **Major** — a scale target with no way to verify it before launch is an unverified claim.
- Design makes dependencies genuinely un-mockable (no named interface/seam at the service boundary),
  on a system with real complexity → **Minor** to **Major** depending on how central the untestable
  path is to the core user journey.
- No chaos/fault-injection consideration at all for an Enterprise-stage system with a stated high-
  availability target → **Minor**, since this is a maturity-appropriate gap rather than a baseline
  requirement — do not penalize Prototype/MVP-stage systems for its absence.
- A schema/data migration with no validation-against-production-shaped-data or rollback-test story,
  for a system where the migration touches consequential data → **Major**.
- No rollback/kill-switch story for continuous deployment, on any independently-deployed layer →
  **Major**.
- No disaster-recovery story (backup/restore, RTO/RPO) for a system holding consequential data →
  **Major**, escalate to **Blocker** if the data is financial, health, or otherwise irreplaceable and
  the document is silent.
- Two or more consequential vendor dependencies with zero outage/lock-in discussion → **Major**.
- No cost consideration anywhere for a stated Startup/MVP-stage, cost-sensitive team → **Major**; the
  same gap for a well-resourced Enterprise system → **Minor**.
- Rich telemetry described with no SLO anchoring it → **Minor** to **Major** depending on how
  consequential the system's availability/latency is.
- No distributed tracing on a system with 3+ hops between client and data, and a stated debuggability/
  incident-response concern → **Minor** to **Major** depending on system complexity.
- Session replay used with no mention of input/PII masking on a system handling sensitive data →
  **Major**, escalate to **Blocker** if the document implies unmasked capture of payment or health
  fields specifically.
