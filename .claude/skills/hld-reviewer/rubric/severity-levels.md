# Severity Levels

Every finding gets exactly one of these four labels. Four is enough to be decisive, few enough to stay
meaningful — don't invent extra tiers. If the person you're reviewing for prefers the shorter P0/P1/P2
convention, map it directly: **P0 = 🔴 Blocker**, **P1 = 🟠 Major**, **P2 = 🟡 Minor / ⚪ Nit** — but
default to the four-level scale below, since it distinguishes "should be fixed before build" (Minor)
from "worth a mention but not urgent" (Nit) more usefully than a three-level scale does.

## 🔴 Blocker
Would cause real harm if built as-is: financial loss, a data breach, a safety issue, or a foundational
error that makes downstream decisions moot.

Examples across domains:
- Client-side-only or caller-asserted-only authorization with no stated server/service enforcement.
- Secrets, API keys, or privileged tokens shippable into browser-loaded code, or over-broadly-scoped
  service credentials.
- Order/payment state inferred client-side, or left inconsistent across services, with no
  authoritative reconciliation.
- A server-side singleton (e.g., a shared client instance, cache, or in-memory store) reused across
  SSR requests with no per-request isolation, risking cross-user data leakage.
- Total absence of a Security section on a system that handles auth, payments, or sensitive data.
- A multi-service write for a single logical financial/safety operation with no compensating-action
  or reconciliation mechanism, risking a permanently inconsistent state.
- A shared/remote module in a micro-frontend or microservice architecture with no
  version-compatibility contract, where an independent deploy can silently break a consumer.

A single Blocker means the overall verdict cannot be "Approve," full stop.

## 🟠 Major
Real pain at the system's stated scale, team size, or stage — or a genuinely unjustified decision on
something consequential — but not unsafe by itself.

Examples across domains:
- Two competing sources of truth for the same piece of data — client-side (a value cached in one store
  and separately mirrored in another) or server-side (two services both able to write the same
  entity) — with no stated precedence rule.
- Module/service/team boundaries described that don't match the document's own stated team structure.
- No idempotency story for a mutating endpoint, or a mutating async message, that obviously needs one.
- No retry/timeout/circuit-breaker story for a dependency the system's own availability target relies
  on.
- Hydration mismatch risk left unaddressed for a component that clearly renders differently on server
  vs. client in an SSR system.
- A major third-party dependency (auth provider, payment processor) with no discussion of outage
  handling or migration difficulty if it needs to be replaced.
- Cost implications of a chosen rendering/real-time/infra strategy never discussed for a system at
  meaningful scale.
- A datastore chosen with no reasoning tied to the system's own stated access pattern.

Multiple Majors, or any Major with no proposed fix path, pushes the verdict to at least "Approve with
required changes."

## 🟡 Minor
Should be fixed but won't cause an incident on its own.

Examples: a missing number where a range would suffice; a diagram that adds no information beyond the
prose; a design-system or shared-package governance model that's implied but not written down for a
still-small team; a vendor-risk discussion that names the vendor but not the fallback plan, on a
low-stakes dependency; a missing pagination story on a list endpoint unlikely to grow large soon.

## ⚪ Nit
Style, polish, or preference — inconsistent terminology, a section longer than it needs to be. Include
a handful if genuinely present; don't pad the list to look thorough.

## Calibration test

When a severity feels ambiguous, ask: if a real production incident, a real cross-team merge conflict
over ownership, or a real cost overrun traced back to exactly this gap, would the postmortem call it a
root cause (Blocker/Major) or a contributing style issue (Minor/Nit)? Use that test to break ties.

## A note on stage-relative severity

The same gap can be a different severity depending on the maturity stage established in
`rubric/scoring-rubric.md`. Missing module/service-ownership governance is a Minor (or not a finding
at all) for a two-person prototype and a Major for a Growth-stage system with three teams across
frontend and backend. Always state the stage you're calibrating against when severity depends on it.
