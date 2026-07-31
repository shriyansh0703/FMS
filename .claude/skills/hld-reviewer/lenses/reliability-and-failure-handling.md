# Lens 7 — Reliability, Failure Handling & Disaster Recovery

**Core question:** when something downstream fails — a service, a database, a third-party vendor, a
network link — does the system fail in a controlled, understood way, or does the document simply not
discuss failure at all?

## Failure handling on synchronous calls

- [ ] **Retries are addressed**, including whether they're safe (cross-reference idempotency in
      Lens 5) and whether they use backoff/jitter — an unbounded or non-backing-off retry loop is a
      common cause of cascading overload.
- [ ] **Circuit breakers / bulkheads are addressed** for calls to dependencies that could plausibly
      degrade or fail under load, so one slow dependency can't exhaust the caller's own resources.
- [ ] **Timeouts are set and match the caller's own latency budget** (cross-reference Lens 5) — a
      timeout longer than the caller can actually tolerate is effectively no timeout.
- [ ] **Fallback/degraded-mode behavior is stated** — what does the system actually do when a
      dependency is unavailable: serve stale/cached data, degrade a feature, or fail the whole request?
      Silence here for a consequential dependency is itself a finding.

## Asynchronous / event-driven reliability

- [ ] **Delivery guarantees are stated** (at-most-once, at-least-once, exactly-once-in-effect via
      idempotency) for any queue/event system, and the consumer's handling matches what's promised.
- [ ] **Dead-letter handling is addressed** — what happens to a message that repeatedly fails
      processing? Is it parked somewhere inspectable, or silently dropped/retried forever?
- [ ] **Message ordering is addressed if it matters** — does the domain require ordering (e.g.,
      state-transition events for one entity), and if so, is there a mechanism (partition key, single
      consumer per entity) that actually guarantees it, or does the design implicitly assume ordering
      it doesn't provide?
- [ ] **Duplicate delivery is handled**, not just delivery loss — cross-reference the idempotency
      checks in Lens 5.

## Client-facing resilience (when a UI layer exists)

- [ ] **Mutation under network failure has a defined outcome space** beyond success/failure — "request
      timed out, outcome unknown" needs its own UI state (don't silently assume success or failure;
      reconcile against an idempotency key or an authoritative status check) for anything with real
      consequences. (Cross-reference Lens 4's mutation-outcome checks — this is the UX-facing half of
      the same gap.)
- [ ] **Offline/reconnection has an explicit story** if any persistent connection or offline scenario
      exists — detect loss, reconnect, re-authenticate if needed, fetch an authoritative snapshot
      before trusting a stream again rather than silently resuming from stale local state.
- [ ] **Modern rendering/hydration failure modes are addressed** if the system uses server-side
      rendering in any form — read `knowledge/rendering-failure-modes.md` and apply it here (hydration
      mismatches, browser-only APIs executed during SSR, server-side singleton leakage across
      requests).

## Data-layer resilience

- [ ] **Replica/failover behavior is stated** for the primary datastore — what happens on a primary
      failure, and what staleness/consistency window does failover introduce?
- [ ] **Partial-failure handling across a multi-step write** is addressed — if a logical operation
      spans more than one service or datastore, is there a stated pattern (saga, outbox, distributed
      transaction, compensating action) for what happens if step 2 of 3 fails, or does the document
      implicitly assume all-or-nothing behavior it doesn't actually provide?

## Disaster Recovery & Business Continuity

Treat this as its own explicit lens for any system holding consequential data or claiming an
availability target beyond a single-machine prototype — "we have backups" is not a DR story on its own.

- [ ] **RTO (Recovery Time Objective) and RPO (Recovery Point Objective) are named**, even roughly
      (e.g., "RTO 4h, RPO 15min") — a stated availability/durability target with no corresponding
      RTO/RPO is an unquantified promise.
- [ ] **Backup strategy is concrete** — frequency, retention window, and whether backups are tested by
      actually restoring them (an untested backup is an unverified assumption, not a recovery plan).
- [ ] **Regional/zone failover is addressed** if the system claims high availability — is there a
      secondary region/zone, what triggers failover (automatic vs. manual), and what data-consistency
      window does a regional failover introduce (cross-reference the replica/failover check above)?
- [ ] **A named DR tier matches the system's own stated stakes** — active-active, active-passive/warm
      standby, or backup-and-restore-only — rather than the document asserting "high availability"
      while describing only single-region infrastructure with no failover mechanism.
- [ ] **Business continuity beyond the data layer is addressed** for Growth/Enterprise-stage systems —
      dependency on a single region for compute/queues/third-party services the business can't
      function without, not just the datastore.
- [ ] **A DR drill/game-day practice is at least mentioned** for Enterprise-stage or safety/financially
      consequential systems — a plan that has never been exercised is a plan of unknown reliability.

## Severity guidance

- No retry/timeout/circuit-breaker story at all for calls to a dependency the system's own stated
  availability target depends on → **Major**, escalate to **Blocker** if a single dependency outage
  would take down the whole system with no documented fallback and the document claims a high
  availability target.
- No dead-letter or failure-handling story for an async pipeline processing consequential events
  (orders, payments, state transitions) → **Major**.
- A multi-step write spanning services/datastores with no partial-failure story → **Major**, escalate
  to **Blocker** if it can leave financial or safety-relevant data in an inconsistent state with no
  reconciliation path.
- "Timed out, outcome unknown" collapsed into either "assume success" or "assume failure" on a
  consequential mutation → **Major**, escalate to **Blocker** for payment/financial actions.
- Message ordering implicitly assumed but not actually guaranteed by the chosen mechanism, for a
  domain where order matters → **Major**.
- No RTO/RPO stated at all for a system holding consequential (financial, health, or otherwise
  irreplaceable) data → **Major**, escalate to **Blocker** if the document claims a high-availability
  or high-durability target with no matching RTO/RPO to back it up.
- Backups exist but are never described as tested/restored → **Minor** to **Major** depending on how
  irreplaceable the data is.
- "High availability" or a multi-nines uptime target claimed with no regional/zone failover mechanism
  described → **Major**.

(Cross-reference `lenses/observability-deployment-operability.md`'s Deployment & Release checks,
which cover backup/restore as an operational concern — this section is the availability-and-continuity
half of the same gap.)
