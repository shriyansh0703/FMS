# Distributed Backend Architecture (Microservices & Distributed Transactions)

Consulted from Lens 3 (System Design & Team/Module Boundaries) whenever the system under review uses,
or is explicitly considering, a microservices or otherwise distributed service architecture.
Monolith-vs-microservices as a single high-level decision is covered in Lens 2's trade-off ledger;
this file covers the failure modes specific to *actually running* a distributed backend, which are
commonly missing from HLDs that treat "we'll use microservices" as a single line item rather than an
architecture with its own deep set of concerns.

## Distributed transactions & data consistency

If a logical business operation spans more than one service's data, the document needs a real
mechanism, not an assumption of atomicity:

- **Saga pattern** — is a sequence of local transactions with compensating actions described for
  multi-step operations, with the compensation logic actually named for each step (not just "we'll
  roll back")?
- **Outbox pattern** — for services that need to update their own database and reliably publish an
  event as a single logical unit, is there a mechanism to avoid the classic dual-write problem (DB
  write succeeds, event publish fails, or vice versa)?
- **Eventual consistency window** — if consistency is eventual rather than strong, is the window
  bounded and stated, and does the UI/consumer-facing behavior account for it (cross-reference Lens 1's
  cross-layer requirement-reconciliation check)?

Silence on all of the above for a system that clearly has multi-service writes for a single logical
operation is itself a finding — a common, high-severity gap.

## Service-to-service call chain risk

- **Cascading failure** — does a single slow/failing service in a call chain risk taking down every
  caller above it? Is there timeout/circuit-breaker/bulkhead isolation at each hop (cross-reference
  Lens 7), or does the document only address this at the edge?
- **Fan-out amplification** — does one incoming request trigger calls to many downstream services
  (N+1-style fan-out at the service level)? If so, is there a stated limit, batching, or aggregation
  strategy (cross-reference the aggregation-ownership check in Lens 3)?
- **Chain-depth latency budget** — for a call chain more than 2-3 hops deep, does the document's
  latency target for the outermost call actually add up given the stated latency of each hop, or is
  the top-level number asserted independently of what the chain would actually produce?

## Service contracts & versioning in practice

- **Contract testing** — is there a stated mechanism (consumer-driven contract tests, a shared schema
  registry) that would actually catch a breaking change before it reaches production, or is
  compatibility purely a documentation convention?
- **Independent deployability, verified** — can one service actually be deployed without coordinating
  with others, or does the document describe microservices while the actual deploy process requires a
  synchronized multi-service release? If the latter, the architecture isn't delivering its primary
  claimed benefit — call this out directly, the same way a micro-frontend architecture with a
  coordinated release pipeline would be called out.

## Data ownership across services

- **No shared database.** Does more than one service read/write the same underlying database/tables
  directly? This is one of the most common ways a "microservices" architecture is actually a
  distributed monolith with all the deployment complexity of microservices and none of the isolation
  benefits — flag it explicitly if found.
- **Cross-service queries** — how does a service get data it needs but doesn't own — a direct
  synchronous call, a local read-replica/materialized view kept in sync via events, or something else?
  Each has different consistency and coupling trade-offs; the document should state which it chose and
  why (cross-reference the trade-off ledger in Lens 2).

## Cross-team ownership boundaries

Cross-reference `lenses/system-design-and-boundaries.md` — does each service map to a clear team
owner? An API gateway, service mesh, or shared platform layer that every service depends on needs an
explicit owner (typically a platform team) rather than being ownerless — the backend analogue of an
ownerless frontend shell.

## Severity guidance

- Multi-service write for a single logical operation with no saga/outbox/compensating-action pattern →
  **Major**, escalate to **Blocker** if it can leave financial or safety-relevant data inconsistent
  with no reconciliation path.
- More than one service reading/writing the same database directly → **Major** (distributed-monolith
  risk), escalate toward **Blocker** if it's on a path with real consistency/ownership stakes.
- No circuit-breaker/timeout isolation at any hop in a call chain of 3+ services → **Major**.
- Independent deployability claimed but the actual pipeline requires coordinated multi-service
  releases → **Major** (the stated architectural benefit isn't real).
- No contract-testing or schema-compatibility mechanism for a system with 3+ services owned by
  different teams → **Minor** to **Major** depending on team count and release cadence.
