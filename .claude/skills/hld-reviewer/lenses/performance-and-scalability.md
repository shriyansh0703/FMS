# Lens 8 — Performance & Scalability

**Core question:** does the end user get a fast, usable experience and does the system stay fast and
correct as load grows — with concrete mechanisms behind every claim, on the client and in the backend
— or are these sections aspirational labels?

## Performance targets

- [ ] Targets are numeric and tied to a measurement — Core Web Vitals (LCP, INP, CLS) at a stated
      percentile for a UI layer; p50/p95/p99 latency for backend endpoints. Adjectives with no numbers
      fail this check outright.
- [ ] A concrete mechanism backs every claim (route-level code splitting, image sizing, font
      preloading, DB query optimization, caching layer — not "the app will be fast" / "the API will be
      fast").
- [ ] High-frequency/real-time updates have a rendering *and* transport strategy — batching, an
      external store, virtualization for large lists, backpressure/fan-out handling on the server —
      not just "we use WebSockets."

## Scalability

- [ ] **Expected load is quantified** — peak RPS/TPS, concurrent users, data volume — and the document
      says what happens at the stated growth horizon (cross-reference the "10x behavior" row in
      Lens 2's trade-off ledger).
- [ ] **Horizontal scaling is addressed** for any component expected to grow — is the component
      stateless and horizontally scalable, or does it have hidden state (in-memory session, local
      cache, sticky-session dependency) that caps how far it can scale out?
- [ ] **Caching strategy is addressed at every layer where it plausibly helps** — CDN/edge, API
      response cache, application-level cache, database query cache — with an invalidation rule for
      each, not just "we'll add caching."
- [ ] **Database bottlenecks are anticipated** — read-heavy paths served by replicas/caches rather than
      the primary, connection-pool limits considered, N+1 query patterns called out if a diagram/flow
      implies them.
- [ ] **Batch processing is addressed** where a workload is naturally bulk (bulk import/export,
      reporting, ML/AI batch jobs) — is it decoupled from the interactive/synchronous path so it can't
      starve it?

## Bundle & loading strategy (client layer)

- [ ] Bundle/loading strategy addressed for anything beyond trivial: code splitting, lazy loading of
      non-critical surfaces, a stated budget for the critical path.

## Modern rendering & hydration failure modes (SSR / streaming SSR / React Server Components)

If the system uses server-side rendering in any form, read `knowledge/rendering-failure-modes.md` and
apply it here — these are commonly missed in reviews that only think about performance in terms of
Core Web Vitals:

- [ ] Hydration mismatch risk is addressed for any content that could legitimately differ between
      server and client render (locale/timezone-dependent formatting, viewport-dependent layout,
      `Date.now()`/random values used during render).
- [ ] Browser-only APIs (`window`, `document`, `localStorage`) are guarded against SSR execution.
- [ ] Server-side singleton/shared-instance leakage across requests is addressed — anything
      initialized once at module scope on the server that could leak one user's data into another
      user's response.

## Cross-reference checks

- [ ] Caching strategy vs. rendering/serving strategy — a statically/edge-rendered route, or a
      read-replica-served endpoint, shouldn't have caching rules written as if it were
      personalized/strongly-consistent, and vice versa (see Lens 4).
- [ ] Scalability claims vs. the architecture's own stated data model — a horizontally-scaled stateless
      service backed by a single-writer, unsharded database is not actually horizontally scalable at
      the data layer even if the service tier is.

## Severity guidance

- No numeric performance target anywhere, for a system where performance is plausibly a real concern
  → **Major**.
- Generic "it will scale" claims at the document's own identified scale target, with no mechanism →
  **Major**.
- A component with hidden local state blocking horizontal scaling, on a path the document itself says
  needs to scale → **Major**.
- No caching invalidation rule stated for a cache that's introduced → **Minor** to **Major** depending
  on how stale data at that cache would matter.
- Hydration mismatch risk left unaddressed for a component that clearly renders differently on server
  vs. client in an SSR system → **Major**.
- A scaled-out service tier sitting in front of an unaddressed single-writer database bottleneck, for
  a system with a stated high-growth trajectory → **Major**.
