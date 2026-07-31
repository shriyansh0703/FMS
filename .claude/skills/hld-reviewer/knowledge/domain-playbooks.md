# Domain Playbooks

Use whichever section is the closest match to the system under review — don't force-fit a domain that
doesn't apply, and combine sections if the system genuinely spans more than one.

## Financial / trading / payments
- Order, trade, or payment state must never be inferred client-side — server-confirmed state, clearly
  distinguishing draft/submitted/pending/confirmed/failed/unknown.
- Every state-changing financial action needs an idempotency story and an explicit "timeout means
  unknown, not failure" handling path. No automatic retry without idempotency.
- PII, account identifiers, balances, and order details excluded from client-side telemetry.
- Regulatory awareness at least acknowledged.

## Real-time / high-frequency data (trading feeds, live dashboards, collaborative editing, chat)
- Rendering decoupled from ingestion — batching, external store, or worker-side processing named.
- Subscriptions scoped to what's actually visible/relevant.
- Explicit reconnection sequence: detect loss → backoff-and-reconnect → re-authenticate if needed →
  fetch authoritative snapshot → resume. Visible freshness/staleness indicator to the user.
- Cross-check `lenses/data-and-state-architecture.md`'s reconciliation-rule requirement — this is where it
  matters most.

## E-commerce / marketplaces
- Cart/checkout consistency across tabs/devices; defined behavior for price/availability changes
  between cart-add and checkout.
- Payment step follows the financial-domain rules above even if the rest of the app is simple.
- SEO-appropriate rendering (static/ISR) expected for catalog pages — fully CSR is a real finding.
- Load handling for sale-event traffic spikes addressed.

## Healthcare / health data
- PHI scrubbed from telemetry at least as rigorously as financial PII; regulatory frame acknowledged.
- Clear separation of server-authoritative clinical data from client-cached presentation.
- Session timeout and access logging expectations addressed even without certainty of the exact rule.

## Content / marketing / SEO-first sites
- Static/ISR expected for public pages — a fully CSR public site is a real finding here.
- Concrete LCP/CLS mechanisms, not bare targets.
- Lighter real-time/state-consistency scrutiny is appropriate unless an authenticated app layer is
  bolted on — check whether the document conflates the two surfaces.

## Internal tools / admin dashboards / low-external-stakes systems
- Don't penalize for skipping SEO or heavy public-facing performance budgets — correct scoping.
- Do check real server-side authZ (a common blind spot precisely because it's assumed low-stakes) and
  basic debuggability.
- Watch for the opposite anti-pattern: over-engineering (microservices, complex real-time
  infrastructure, premature micro-frontends) for a handful of internal users with no real scale
  pressure — this is as much a finding as under-engineering.

## Multi-tenant SaaS
- Tenant isolation enforced server-side, never just reflected client-side.
- Feature-flag/entitlement gating never substitutes for real server-side authorization — a common
  Blocker-level gap (client trusts a tenant ID/role fetched once and never re-validated).

## AI / LLM-native products
A growing category with its own architecture concerns, distinct from generic web-app patterns:
- **Streaming responses** — is token-by-token/chunked streaming addressed as a rendering concern
  (incremental render without layout thrash, cancel-on-navigate), not just a transport detail?
- **Prompt/context management** — for anything beyond a single-turn interaction, is there a stated
  approach to conversation/context state (what's kept client-side vs. server-side, how context is
  trimmed or summarized as it grows)?
- **Generative UI** — if the product renders dynamically-generated UI/components from a model
  response, is there a sandboxing/validation story (a model should not be able to produce output that
  executes arbitrary code or breaks the page) — treat an unaddressed gap here as a security-adjacent
  finding, not just a UX one.
- **Model fallback strategy** — what happens if the primary model/provider is unavailable, rate-
  limited, or returns a low-confidence/malformed response? Silence here is a Tier-1-equivalent vendor
  risk (see `knowledge/vendor-and-cost-risk.md`).
- **Latency management** — AI responses are typically slower than a normal API call; is loading/
  perceived-performance UX (streaming, progressive disclosure, cancellation) addressed specifically,
  rather than treated like a normal fast REST call?
- **Cost-aware architecture** — inference cost per request/user is a first-order cost concern for
  these products; check this explicitly against `knowledge/vendor-and-cost-risk.md`'s cost-analysis
  bar, weighted heavily regardless of stage, since AI inference cost scales directly with usage in a
  way most other frontend costs don't.
- **Client-side context caching** — if conversation history or retrieved context is cached client-side,
  is there a staleness/invalidation story, and is sensitive context excluded from persistence the same
  way other sensitive data would be?

## Data platforms / analytics pipelines / ETL
- Data lineage and freshness SLAs should be stated per dataset, not just "the pipeline runs daily."
- Schema evolution for upstream sources is addressed — what happens to downstream consumers when an
  upstream schema changes?
- Idempotent, replayable pipeline stages are expected — a failed run should be safely re-runnable
  without double-counting or duplicating output.
- PII handling in raw vs. processed layers is addressed if any source data contains it.

## B2B / platform APIs (systems whose primary consumer is another engineering team)
- API-first design — the contract (Lens 5) is effectively the product; versioning and backward
  compatibility deserve full rigor regardless of stage.
- Rate limiting and fair-usage/quota behavior per consumer is addressed, not just per-request.
- Sandbox/test environments and API keys for external consumers are addressed separately from
  production credentials.
- Webhook delivery (if used) has a retry/backoff and signature-verification story — treat an
  unauthenticated or non-retried webhook as a real gap.

## Offline-first / PWA products
- **Service Worker update strategy** — how do users get a new version (immediate vs. "update on next
  visit"), and is there a stated plan for avoiding users getting stuck on a stale cached version
  indefinitely?
- **Cache invalidation** — for both the Service Worker's asset cache and any data cache (IndexedDB,
  localStorage) — a stated versioning/invalidation approach, not an assumption that caching "just
  works."
- **IndexedDB schema migrations** — if structured client-side data is stored, is there a migration
  story for schema changes across app versions, analogous to a server-side database migration?
- **Offline mutation reconciliation** — mutations queued while offline need a defined replay/reconcile
  strategy once connectivity returns, including conflict handling if the server-side data changed in
  the meantime. This is the offline-specific version of the mutation-outcome-space requirement in
  `lenses/data-and-state-architecture.md`, and deserves the same rigor.
- **Sync conflicts** — if the same record can be edited both offline and on another device, is there a
  stated conflict-resolution rule (last-write-wins, merge, user-prompted)? Silence here is a real gap
  for any product that markets true offline editing rather than just offline reading.
- **Stale-while-revalidate pitfalls** — if this caching pattern is used, does the document address the
  UX of briefly showing stale data (is it visibly marked as such, or silently presented as current)?
