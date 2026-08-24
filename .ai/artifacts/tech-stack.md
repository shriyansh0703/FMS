# Technology Stack — Fund Management System

Companion to `hld.md`. This document states what the stack is, what the Fund Management System adds to
it, and — for the additions — what was considered and rejected.

**Its central claim is that FMS adds almost nothing.** The estate already runs a Spring Boot service
that owns a trader's money, on a single PostgreSQL primary, with a client-rendered React web client, a
transactional outbox and dispatcher, Flyway migrations at V20, and Micrometer instrumentation. That is
observed rather than assumed: it is recorded in the approved design set for run 004 of the sibling Fund
Management Service, held for reference at `05-dependencies/fms-run-004-reference/`.

Where this document names a technology, it is because the platform already runs it or because FMS
genuinely needs something the platform lacks. There is exactly one entry in the second category, and it
is an integration rather than a technology.

---

## Architecture Overview

A modular monolith. One Spring Boot service owns the money surface, backed by one PostgreSQL primary,
with a React and TypeScript web client. Module boundaries give isolation between the balance
derivation, the movement engine, the ledger view, account health and the message dispatcher without
putting a network hop on the path a trader hits most.

FMS is not a new process, a new datastore, a new queue or a new client application. It is a set of
modules inside the estate's existing shape, plus four integrations that already exist for other reasons
and one that does not yet.

The design decision that shapes the stack is recorded in `hld.md` §6.3: **TechExcel is the system of
record for ledger entries and balances.** FMS therefore stores no entries. It stores what the back
office has no home for — withdrawal requests, payin attempts, movement state history, derivation
snapshots and the message delivery log — which is why the storage figures below are small even though
the ledger itself is not.

---

## Runtime & Infrastructure Topology

| Layer | Technology | Status for FMS |
|---|---|---|
| Web client | React with TypeScript, client-rendered, React Query for server state | Existing. FMS adds screens, no new rendering model and no new state library |
| Client caching | React Query by query key; the funding position is invalidated after any completed movement by the same trader | Existing rule, retained. `hld.md` §11 explains why the balance itself is never cached |
| Static delivery | CDN, content-hashed bundles | Existing |
| API edge | Platform API gateway, TLS termination, per-account rate limiting | Existing. FMS adds a stricter limit on the two endpoints that originate money movement |
| Service | Java on Spring Boot, modular monolith | Existing. FMS adds modules inside it |
| Service-to-service | mTLS inside the platform | Existing |
| Primary datastore | PostgreSQL, single primary | Existing. FMS adds tables and additive migrations; no table is rewritten |
| Schema migration | Flyway, versioned, forward-only | Existing, at V20. FMS continues the sequence |
| Async | Transactional outbox with a relay dispatcher | Existing, built in run 001. FMS registers event types, not machinery |
| Scheduling | Spring scheduling with a single-runner arrangement | Existing. The settlement run and reconciliation sweep already work this way; the end-of-day payout run joins them |
| Metrics | Micrometer counters and timers | Existing. FMS adds the counters named in `hld.md` §17 |
| Message delivery | Platform Communication Service — `sms` and `email` documented; `whatsapp` is a valid channel and live in the estate (Profile §7.4b sends on it), but the grant to FMS and its address format are unconfirmed | Existing. **Never retries** — `failed` is terminal and nothing calls back, so `hld.md` §15 polls and resubmits with a new key. `request_id` is an idempotency key and maps onto the outbox row |
| Deployment | Containers on the platform's orchestration and pipeline | Existing |

### Partitioning and retention

`movement_state_event` and `message_delivery` are partitioned by month. Both are append-only, both are
read by recent window, and both have a retention boundary — which makes dropping an old partition the
retention mechanism rather than a delete sweep over a live table.

### The constraint that lives in the schema rather than in code

A partial unique index on `payout_request`, over the account and restricted to open states, enforces
Rule W4's one-open-request rule. This is deliberate placement. Rule W3 removed reservation from the
withdrawal path, which left Rule W4 as the sole protection against a trader committing the same money
twice, so it is enforced where a race cannot get past it.

The same table carries a second constraint for the same reason. Rule C8 requires the bank's own transfer
reference and this module's reference to be different fields that never share a value, because a trader
chasing a payment needs the identifier their bank can trace. Both are stored as separate columns with a
check that they are never equal — a convention would be honoured until the day someone populated one
from the other to fill a gap.

`payout_request` also stores the arrival date **quoted** to the trader alongside the date actually
credited. That pairing is the entire mitigation for a rated risk in the PRD, that operations cannot meet
the times the product quotes; without both dates stored there is nothing to compare and the risk has no
control behind it.

---

## What FMS Adds to the Stack

**One integration, and no new technology.**

| Need | Served by | Already present because |
|---|---|---|
| Balances, entries, charges, settlement check, debit rate | TechExcel | Integrated; now also the system of record per the Stage 3 gate decision |
| Intraday margin, positions, shortfall, **and the withdrawable figure** | Kambala Noren — OMS and RMS, the front office | Integrated. `GetRmsLimits`, `GetWithdrawalAmt`, and subscribe streams that push funds, payin and payout changes rather than being polled |
| Payin routes (UPI collect, UPI intent, netbanking), payout rail, refunds, IFSC validation | Juspay | Integrated |
| **Per-user per-route daily headroom** | **FMS itself** | Juspay's `Get Balance` is the gateway's balance, not a customer's remaining cap. No external system knows what this customer has already sent on this route today, so REQ-701's cap ledger is FMS's to own |
| Proven bank accounts, `maxBankAccounts` | Profile | Integrated; FMS reads and never mutates. Verification resolves **after the session ends** (PR-28), so the list is re-read at every decision point rather than cached for a journey |
| Server-side masking of regulated identifiers, including in exports | Profile's disclosure policy | PR-31 and PR-32 make this estate-wide rather than an FMS choice, and they bind REQ-407's CSV export |
| Message dispatch | Platform Communication Service | Integrated. One channel per call, so a two-channel obligation is two submissions |
| **Trading and settlement calendar** | **Nothing yet — no source is nominated** | **This is EB-9, and it is the one genuine gap** |

The calendar is the only entry in this table that has no answer. `hld.md` §21 R1 explains why it gates
Phase 1 rather than Phase 3: Rule B4's unsettled-proceeds deduction is measured in settlement days, so
without a calendar the withdrawable figure is computed against weekdays and is wrong on every trading
holiday. Whether it arrives from TechExcel or from a direct authoritative download is open; that it must
arrive before the three balances ship is not.

---

## Dependencies Deliberately Not Added

| Tempting addition | Why it was not added |
|---|---|
| A cache for the balance figures | The figure a trader checks before committing money must not be briefly wrong. Run 001 made this path read-your-writes and run 004 declined the same cache for the same reason. `hld.md` §11 caches inputs, which carry their age and are rendered with it, and never the answer |
| A read replica | ~21 requests per second sustained against indexed reads on append-only tables does not need one, and a replica puts a lag window on the one surface where staleness reads to a trader as missing money |
| A separate ledger service inside FMS | Decided at the Stage 3 gate: TechExcel is the system of record. A second set of books that must agree with the first is the failure the reconciliation requirements exist to prevent |
| A message broker | The existing outbox and relay already deliver the ordering and atomicity REQ-622 needs. A broker would add an operational dependency to gain nothing at ~5,000 messages a day |
| A workflow engine for the end-of-day payout run | Roughly 500 requests in one nightly batch is a loop with a chunk size and a leader lock, not an orchestration problem |
| A rules engine for message suppression | Suppression is a handful of stated rules — quiet hours, one SMS per event per day, regulatory messages exempt — that read better as code than as configuration a reviewer cannot diff |
| A vector store, a search engine, or any AI component | Nothing in this PRD asks a question that is not answered by an indexed query. The transaction list needs a date range and a type filter, not relevance ranking |
| A second rendering model for one screen family | An SSR path for the funds view was drafted and dropped. `hld.md` §13 gives the reasoning and the measurement that would reopen it |

---

## Server-Side Language and Framework

**Java on Spring Boot.** Not chosen so much as inherited, and correctly so: the estate runs it, the
outbox and scheduling arrangements FMS depends on are Spring's, and the integration clients for
TechExcel, Juspay and Profile already exist in it.

*Rejected — a separate service in a different runtime.* Would isolate FMS's failures from the rest of
the money surface, which sounds attractive until the balance path crosses a network boundary and Rule
B12's single-definition guarantee has to survive a serialisation format. Module boundaries inside the
monolith, enforced by an architecture test as the estate already does, give the isolation that matters
without that cost.

*Rejected — microservices per bounded context.* Six modules, one team, ~21 requests per second, and a
correctness problem rather than a scale problem. The operational cost is real and the benefit is
speculative.

---

## Client-Side Framework

**React with TypeScript, client-rendered, React Query for server state.** Existing, retained.

*Rejected — server-side rendering for the funds view's first paint.* Drafted, then dropped on evidence.
`hld.md` §13 records the argument for it, the reason it lost, and the measurement that would justify
revisiting it for that one route.

*Rejected — a mobile application.* The PRD states there is none, which is why SMS carries load that push
would otherwise share. Adding one is a product decision, not a stack decision.

---

## Data Storage

**PostgreSQL, single primary.** Existing.

FMS's own tables hold requests, attempts, state history, derivation snapshots, consent and the delivery
log — the low tens of gigabytes. The ~770 GB of ledger data estimated in `hld.md` §5 sits in TechExcel
and is not FMS's to store.

*Rejected — a document store.* Every table here has a fixed shape, the movement engine needs multi-row
transactions, and one business rule is enforced by a uniqueness constraint.

*Rejected — sharding or a partition key across accounts.* Nothing approaches a single node's limits.
A key chosen now would be a guess that constrains later, which is the definition of premature.

---

## Testing and Verification

| Layer | Approach |
|---|---|
| Balance derivation | Property-based. For every generated input the derivation must reconcile to the figure, and the withdrawable figure must never be negative — the one exception Rule B9 permits |
| Movement idempotency | Every external callback replayed; a duplicate confirmation must produce one credit and one entry |
| End-of-day payout run | All five outcomes of Communications §4.4, including the rail-unavailable case that leaves a request open and cancellable |
| Module boundaries | Architecture test enforcing package boundaries, as the estate already does |
| Accessibility | Browser tests asserting keyboard reachability of every money action, because WCAG 2.1 AA is a stated requirement rather than an aspiration |
| Contract | Integration tests against recorded TechExcel and Juspay responses, including the reason-code shapes assumptions A5 and A6 depend on |

---

## Open Stack Questions

1. **The calendar source (EB-9).** The only missing capability. Gates Phase 1.
2. **TechExcel's settlement reason-code contract (A5).** REQ-308 needs an outcome specific enough to
   name which deduction accounts for a gap. A bare status code satisfies the control and fails the
   requirement.
3. **Juspay's per-route headroom and failure reason codes (A6).** REQ-701 enforces caps per day per
   route, and REQ-614 gives each failure its own message. Both need the gateway to expose more than a
   pass or fail.
4. **The step-up authentication integration.** `hld.md` §8.1 builds the seam; the control belongs to the
   authentication team and Phase 3 is gated on their ruling.
