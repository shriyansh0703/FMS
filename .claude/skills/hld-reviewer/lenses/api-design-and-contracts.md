# Lens 5 — API Design & Contracts

**Core question:** is every contract between two independently-deployable pieces of this system —
client-to-BFF, BFF-to-service, service-to-service, system-to-third-party — actually specified well
enough that both sides could be built independently and still work together on day one?

## Style & shape

- [ ] **Protocol/style is stated and justified** (REST, GraphQL, gRPC, tRPC, event/message-based) —
      cross-reference the trade-off ledger in Lens 2 for *why*; this lens checks that the chosen style
      is then actually specified in enough detail to build from.
- [ ] **Resource/operation shape is concrete** — for REST, are resources and verbs named; for GraphQL,
      is the schema's shape at least sketched; for gRPC/RPC, are service/method signatures named? A
      contract described only as "the frontend calls the backend API" is not a contract.
- [ ] **Pagination is addressed** for any list-returning endpoint that could plausibly grow —
      cursor-based vs. offset, and page-size limits.

## Versioning & compatibility

- [ ] **A versioning strategy exists** for any API with more than one consumer, or any consumer that
      deploys independently of the API owner (a separately-deployed frontend counts) — how is a
      breaking change rolled out without breaking existing clients?
- [ ] **Backward compatibility expectations are explicit** — does the document say how long an old
      client/consumer is expected to keep working against a new API version?

## Reliability properties of the contract

- [ ] **Timeouts are specified** for every synchronous call in the document, not left to defaults —
      cross-reference Lens 7 for what happens when a timeout fires.
- [ ] **Idempotency is addressed for every mutating operation that could plausibly be retried** (client
      retry on a flaky connection, an at-least-once message delivery, a load-balancer retry) — is there
      an idempotency key, a natural idempotent operation, or an explicit "this is not safe to retry"
      warning with a reason?
- [ ] **Error responses are structured, not just status codes** — does the contract distinguish
      retryable errors from terminal ones, and carry enough information for the caller to act
      correctly (not just "something went wrong")?
- [ ] **Rate limiting / quota behavior is addressed** for any externally-facing or high-fanout internal
      API, including what the caller should do when throttled.

## Contract fidelity across the boundary

- [ ] **The client's (or downstream service's) actual usage matches the contract described** — does a
      sequence diagram or a described client flow call an endpoint/method in a way consistent with the
      pagination, versioning, and error-handling story stated for it? (Specific instance of the
      diagram-text/cross-layer consistency check in `SKILL.md`.)
- [ ] **Authentication/authorization on the contract is stated here and matches the Security lens** —
      every endpoint/method should have an explicit statement of who's allowed to call it, not just a
      global "the API is authenticated."
- [ ] **Payload size / streaming needs are addressed** if the domain plausibly involves large payloads
      (file upload/download, bulk export, long-running results) — a single unstreamed synchronous
      response for something that could be large is a common, easy-to-miss gap.

## Severity guidance

- No timeout specified for a synchronous call on a path with a stated latency/availability
  requirement → **Major**.
- A mutating operation with no idempotency story on a path that's plausibly retried (client retry,
  at-least-once delivery, load-balancer retry) and has real consequences (payment, order, irreversible
  state change) → **Major**, escalate to **Blocker** if a double-submit would cause financial or
  safety harm.
- No versioning strategy for an API with independently-deployed consumers → **Major**.
- Authorization stated only globally, with no per-endpoint/method statement, for a system with mixed
  permission levels → **Major** (cross-reference Lens 6; escalate there if it implies client-only
  enforcement).
- Missing pagination on a list endpoint with unbounded growth potential → **Minor** to **Major**
  depending on stated/likely data volume.
