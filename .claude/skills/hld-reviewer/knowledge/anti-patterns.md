# Common HLD Anti-Patterns (Full-Stack)

Patterns that recur across a large share of architecture documents, regardless of domain or which
layer they show up on. Actively hunt for these — they're often invisible on a section-by-section read
because each individual section looks fine in isolation, and many have a near-identical sibling on the
other side of the stack.

## 1. The buzzword section
Technology/pattern names with no "why" attached. Test: could you delete the justification clause and
lose zero information? If yes, it wasn't a real justification. Applies equally to "we use React Query"
and "we use Kafka."

## 2. The label-only compliance claim
"WCAG 2.2 AA compliant," "SOC2-ready," "GDPR-compliant," "PCI-DSS compliant" stated as fact with zero
supporting mechanism.

## 3. Symmetric depth regardless of stakes
Every section roughly the same length whether discussing the auth model or a date-formatting utility,
or the payments service and a low-traffic internal reporting endpoint. Real architecture has uneven
depth — consequential, hard-to-reverse decisions should visibly get more scrutiny (see the trade-off
ledger in Lens 2).

## 4. False global uniformity
One rendering/caching/state strategy declared for an app that obviously has both public/static and
authenticated/dynamic surfaces — or one consistency model declared for a system that obviously has both
strongly-consistent (payments) and eventually-consistent (activity feed) needs.

## 5. The optimistic-everything mutation model
Every write treated as instant/optimistic — on the client, or as fire-and-forget between services —
with no handling for server disagreement, network failure, partial failure, or duplicate submission.

## 6. Security-by-vocabulary
Right words (CSRF, XSS, CSP, mTLS, "zero trust") with no actual mechanism described.

## 7. The infinite-scale hand-wave
"Scales to millions," "horizontally scalable," with no discussion of what breaks first (the database?
a stateful component? a single-partition queue?) or what it costs to get there.

## 8. Orphaned diagrams
Diagrams that restate the prose exactly, or sit disconnected from the section they illustrate, or
disagree with it outright (see the diagram-text consistency check in `SKILL.md`).

## 9. Team-size theater
Team size mentioned once in assumptions, never changes a single recommendation afterward — no module
boundary, no service-ownership split, no deploy-independence story that actually follows from it.

## 10. Contradiction by omission
Two sections don't literally disagree, but one silently assumes something the other explicitly ruled
out — most common between rendering/caching and auth/token-storage claims on the frontend, and between
a stated consistency model and a stated latency/availability target on the backend.

## 11. Shadow state / shadow ownership
Two mechanisms both quietly claim ownership of the same piece of data with no stated precedence rule.
On the client: a query cache and a manually-synced global store, or Context and localStorage. On the
backend: two services both able to write the same entity, or a cache treated as authoritative when the
database disagrees. One of the single most common real-world causes of bugs at scale — actively hunt
for it in Lens 4, not just accept it if each mechanism's existence is individually justified.

## 12. The unforced trade-off
A decision presented with a "why we chose X" paragraph that never actually names what was rejected or
what it costs — reads as confident and thorough while contributing zero falsifiable content. Distinct
from anti-pattern #1 (buzzwords): the language here can be entirely sound engineering vocabulary; the
tell is the missing rejected-alternative and missing cost, not missing jargon.

## 13. Ownerless shared surfaces
A design system, shared package, shared platform service, or (in a distributed architecture) the
shell app / API gateway described with no stated owner and no stated change-approval process. Works
fine with one team; becomes a governance vacuum the moment a second team starts depending on it.

## 14. Rendering/consistency vocabulary without the mechanism
An SSR/streaming SSR/RSC-based frontend that never addresses hydration-mismatch risk or server-side
singleton leakage (see `knowledge/rendering-failure-modes.md`); or a backend that claims "eventual
consistency" or "exactly-once processing" without describing the actual mechanism (idempotency key,
outbox pattern, ordering guarantee) that would make that true. Both are the mechanism-layer sibling of
anti-pattern #6.

## 15. Business logic creep to the wrong layer
Business rules (discount eligibility, permission computation, financial calculations) implemented
client-side for UX responsiveness, or duplicated into a non-owning service for convenience, with no
explicit statement that the authoritative layer independently re-derives and enforces the same rule.
Common in growing codebases where a "just compute it here for now" shortcut never gets revisited —
worth flagging even when framed as a minor convenience, because it tends to become the accidental
source of truth over time.

## 16. The all-or-nothing distributed write
A logical operation that spans more than one service or datastore, described as if it succeeds or
fails as a single atomic unit, with no saga/outbox/compensating-action pattern behind that claim. The
backend counterpart of the optimistic-everything mutation model (#5) — the failure mode surfaces the
first time step 2 of 3 fails and nothing rolls back step 1.

## 17. The synchronous critical path with no isolation
A request-handling path where a slow or failing non-critical dependency (an analytics call, a
notification send, a recommendation lookup) is called synchronously and can block or fail the entire
user-facing request, with no timeout, circuit breaker, or async decoupling. Easy to miss because each
individual call "makes sense" — the finding only surfaces when you trace the full call chain for the
critical path end to end.
