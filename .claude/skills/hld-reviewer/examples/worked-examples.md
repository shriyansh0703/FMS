# Worked Examples (Calibration Reference)

Short, independently-constructed fictional scenarios for calibrating tone, depth, and the
Requirement → Risk → Evidence → Fix chain — not templates to copy structurally (the report template
defines structure).

---

## Example A — a Major finding in a strong-overall document (B2B SaaS analytics dashboard, Growth stage)

> **Two sources of truth for row-level data** — *Section 12 (State Management) & Section 18 (Real-Time
> Updates)*
> - **Requirement:** users need to trust that a dashboard row reflects the current value, especially
>   when live updates are a headline feature.
> - **Risk:** a stale poll response arriving after a live socket update could silently roll a row's
>   displayed value backward, with no error and no way for the user to notice.
> - **Evidence:** Section 12 states TanStack Query owns all server-derived state with a 30-second stale
>   time. Section 18 separately describes a WebSocket pushing row-level updates into "the same query
>   cache," with no stated precedence rule between the two writers.
> - **Recommended fix:** state an explicit precedence rule (e.g., socket events carry a monotonic
>   sequence number; a query refetch response is only applied if newer) or bar HTTP refetch from write
>   access to any key the socket also owns.

*(Severity: Major. Dimension: Data & State Architecture. This is exactly the "shadow state"
anti-pattern — each mechanism is individually reasonable, the conflict only surfaces on
cross-reference.)*

## Example B — a Blocker in an otherwise polished-looking document (fintech mobile-web trading app)

> **Client-side-only order confirmation** — *Section 9 (Order Flow)*
> - **Requirement:** a user must never see an order as "confirmed" unless the broker has actually
>   confirmed it — this is the single highest-stakes correctness property in a trading UI.
> - **Risk:** if the client marks an order confirmed based on the POST request succeeding (rather than
>   waiting for an explicit server confirmation event), a request that succeeds at the network layer
>   but is rejected by risk checks downstream would show the user a false "order placed" state.
> - **Evidence:** Section 9's sequence diagram shows the UI transitioning to "Confirmed" immediately
>   after `POST /orders` returns 200, with the order-confirmation WebSocket event arriving afterward
>   but not gating the UI state change.
> - **Recommended fix:** the UI should show "Submitted" (not "Confirmed") until the server-pushed
>   confirmation event arrives; on timeout, show "Checking order status," never silently upgrade to
>   Confirmed based on the HTTP response alone.

*(Severity: Blocker. Dimension: Data & State Architecture (client mutation-outcome handling), with a
cross-reference to Reliability, Failure Handling & DR — this single gap caps the entire review's verdict
at "Do not build from this yet" even if every other section is excellent, per the rubric's
Blocker-caps-verdict rule.)*

## Example C — a Blocker on the backend side of the same kind of gap (order-processing microservices, Growth stage)

> **No dual-write protection between the orders DB write and the `order.placed` event publish** —
> *Section 11 (Order Service)*
> - **Requirement:** downstream services (inventory, fulfillment, billing) must never miss an order
>   that was actually committed, and must never process one that wasn't.
> - **Risk:** Section 11 describes the order service writing the order row to Postgres, then
>   separately publishing an `order.placed` event to Kafka in the next line of the same request
>   handler. If the process crashes between the DB commit and the publish (or the publish call itself
>   fails), the order exists with no downstream service ever notified — a silent, unrecoverable gap
>   with no reconciliation job mentioned anywhere in the document.
> - **Recommended fix:** adopt the transactional outbox pattern — write the event to an outbox table in
>   the same DB transaction as the order, and have a separate relay process publish from the outbox to
>   Kafka, so the write and the publish share a single atomicity boundary. Alternatively, add an
>   explicit reconciliation job that scans for orders with no corresponding published event.

*(Severity: Blocker. Dimension: Reliability, Failure Handling & DR, with a cross-reference to Data & State
Architecture — the backend mirror of Example B: a mutation that can silently produce a state the rest
of the system disagrees about, with no reconciliation path.)*

## Example D — a borderline call, explained (internal admin tool, Prototype stage)

> **No module-ownership model** — *System Design & Team/Module Boundaries lens, no dedicated section
> in the document*
> - **Requirement:** at Prototype stage with a stated single developer, no cross-team coordination
>   problem exists yet.
> - **Risk:** none *currently* — the risk only materializes if/when a second developer or team starts
>   owning part of this tool.
> - **Evidence:** the document has no module/ownership discussion anywhere, and doesn't need one yet
>   given its stated scope.
> - **Recommended fix (forward-looking, not urgent):** if this tool grows past a single owner, revisit
>   feature-boundary rules before adding a second regular contributor — no action needed now.

*(Severity: downgraded to Nit, not Major, specifically because of the stated Prototype stage — this is
the maturity-stage calibration from the rubric in action. The same gap in a stated Growth-stage,
three-team document would be a Major.)*

## Example E — what "genuinely strong" looks like when praised specifically (same dashboard as Example A)

> The routing strategy is a highlight — dynamic imports for chart-heavy routes are called out with an
> actual bundle-budget number (150KB gzip) and a stated fallback (skeleton loader, not a blank
> screen), and the reasoning explicitly ties this to the dashboard's own reported "users on corporate
> VPNs with variable bandwidth" constraint from the requirements section. On the backend side, the
> same document's API rate-limiting section is equally concrete: a stated 100 req/min per-tenant quota,
> a 429 response carrying a `Retry-After` header, and an explicit note that this was chosen over a
> global limit specifically because one noisy tenant should never degrade another's experience. This is
> exactly the kind of requirement-to-decision traceability the rest of the document should be held to,
> on both sides of the stack.

---

Note the pattern across all five: specific section references, a concrete mechanism-level explanation
of *why* something is a finding (never just "this is wrong"), a severity explicitly justified against
either the Blocker/Major/Minor/Nit definitions or the maturity-stage calibration, and — for findings —
an actionable fix. Praise gets the same specificity as criticism; neither is generic. Note also that
Examples B and C are the same underlying failure pattern (a mutation whose outcome the rest of the
system can't trust) appearing on the client and on the backend respectively — the review should
recognize and name that symmetry when it shows up, rather than treating client-side and server-side
findings as unrelated categories of concern.
