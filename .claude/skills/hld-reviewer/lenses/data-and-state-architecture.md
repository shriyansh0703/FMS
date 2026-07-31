# Lens 4 — Data & State Architecture

**Core question:** is there exactly one clear source of truth for every meaningful piece of data —
from a client-side UI value all the way down to the system of record — or does the document quietly
allow two mechanisms to both claim ownership of the same data? This is one of the single most common
causes of complexity and bugs at scale, on the client and in the backend alike.

## Client / application state (when a UI layer exists)

Before checking anything else, build a mental (or scratch) table: for every category of state the
system has, which single mechanism owns it?

| State category | Examples | Should typically be owned by |
|---|---|---|
| Server/remote state | API-fetched records, account data | A data-fetching/cache library (React Query, SWR, RTK Query, Apollo) — not duplicated into Redux/Context "just in case" |
| Real-time/streamed state | Live prices, live feed updates | A dedicated store/external-store pattern, not merged blindly into the same cache as polled data |
| Global client-only state | Theme, layout, feature-flag overrides | A lightweight global store (Context, Zustand, Redux) — scoped to what's genuinely cross-cutting |
| URL/route state | Filters, pagination, selected tab | The URL itself — shareable, back-button-safe |
| Local/ephemeral state | Form drafts, open/closed UI, hover state | Local component state — should not leak into global stores |
| Persisted client state | Remembered preferences, offline drafts | An explicit persistence layer (localStorage/IndexedDB) with a stated invalidation/versioning plan |

If the document doesn't make ownership assignments like this at least implicitly clear, that itself is
a finding — "we use Redux, React Query, and Context" with no stated division of responsibility is a
common, serious gap.

- [ ] **No state category is claimed by two mechanisms at once.** The classic failure: the same
      server-fetched record lives in both a query-cache and a manually-synced Redux/Context slice.
- [ ] **Real-time updates and cache-fetched data reconcile with a stated rule** — if a WebSocket event
      and an HTTP refetch can both write to the same piece of state, which one wins, and how (a
      sequence number, a "socket is always newer" rule)? Silence is a common, high-severity gap.
- [ ] **Persistence and synchronization risk is addressed** — what happens if two tabs both write to
      localStorage/IndexedDB, or a persisted shape needs to change (a version/migration story)?
- [ ] **Cache invalidation has a real rule** tied to specific mutations, not just a default library
      setting mentioned once.

## Backend data / system of record

- [ ] **Datastore choice is justified for the access pattern**, not just named — why this datastore
      over an existing one already in use elsewhere in the system? What are the actual read/write
      patterns (read-heavy vs. write-heavy, point lookups vs. range scans vs. full-text/analytics)?
- [ ] **Indexing strategy matches the stated query patterns** — are the queries the document describes
      actually servable efficiently by the indexes (or lack thereof) it describes?
- [ ] **Sharding/partitioning is addressed if scale plausibly requires it** — what's the shard key, and
      does it match the actual access pattern (avoiding hot shards)?
- [ ] **Replication and consistency model is stated** — synchronous vs. asynchronous replication,
      read-replica staleness, and what consistency guarantee the application actually needs vs. what
      the datastore provides.
- [ ] **Retention and lifecycle policy is addressed** — how long is data kept, is there an archival/
      deletion path, and does it account for regulatory retention/deletion requirements if applicable
      (cross-reference Lens 6).
- [ ] **Schema evolution / migration story exists** — how are backward-incompatible schema changes
      rolled out without downtime or data loss?

## One system of truth, end to end

- [ ] **Exactly one service (or datastore) is the authoritative write path for each entity.** If two
      services can both write the same record — or a client can write directly to a datastore a
      service also owns — flag it as the distributed-systems version of the "two sources of truth"
      client-state anti-pattern above; the risk and severity reasoning are the same.
- [ ] **Derived/cached copies are labeled as such.** A read-replica, a cache, a search index, or a
      client-side cache of backend data should never be treated as authoritative for a write decision.

## Cross-reference checks

- [ ] State-management/data-layer choice vs. diagrams — does the stated approach match what any
      data-flow/sequence diagrams actually depict? (This is the specific case of the general
      diagram-text consistency check in `SKILL.md` — apply that check's full scope here.)
- [ ] Caching strategy vs. rendering/serving strategy — a statically/edge-rendered route, or a
      read-replica-served endpoint, shouldn't have caching rules written as if it were
      personalized/strongly-consistent, and vice versa.
- [ ] Mutation flow vs. error-handling section (cross-reference Lens 7) — does the description of how
      a write happens (optimistic? server-confirmed? synchronous? event-driven?) match how
      errors/timeouts/retries for that same action are handled elsewhere?
- [ ] Auth model vs. security section's token/session-storage claims — must never disagree
      (cross-reference Lens 6).

## Mutations and outcome handling

- [ ] Mutations have a defined outcome space beyond success/failure — "timed out, outcome unknown"
      needs its own path (query by idempotency key, don't blindly retry or assume failure) for
      anything with real consequences.
- [ ] Reconnection/recovery has an explicit story if any persistent connection or offline scenario
      exists — detect loss, reconnect, re-authenticate if needed, fetch an authoritative snapshot
      before trusting the stream again.

## Severity guidance

- Two mechanisms (client-side, or two services) both claiming ownership of the same data with no
  precedence rule → **Major**, escalate to **Blocker** if the conflicting data is financial or
  safety-relevant.
- No stated interaction model between more than two active state/data mechanisms → **Major**.
- Missing freshness/reconciliation story for real-time + polled/cached data where stale data has real
  consequences → **Major**, escalate to **Blocker** if a user could act on stale data believing it's
  live.
- Datastore chosen with no reasoning tied to actual access pattern → **Major**.
- No sharding/scaling story for a datastore at a stated scale that will clearly outgrow a single
  node → **Major**.
- No persistence/schema versioning story where state is persisted across releases (client or server)
  → **Minor** to **Major** depending on what's persisted (form drafts vs. financial records).
