# Distributed Frontend Architecture (Micro-Frontends & Module Federation)

Consulted from Lens 3 whenever the system under review uses, or is explicitly considering, a
micro-frontend or module-federation architecture. Monolith-vs-micro-frontend as a single high-level
decision is covered in Lens 2's trade-off ledger; this file covers the failure modes specific to
*actually running* a distributed frontend, which are commonly missing from HLDs that treat "we'll use
micro-frontends" as a single line item rather than an architecture with its own deep set of concerns.

## Shared dependency duplication
If multiple remotes/micro-frontends each bundle their own copy of React, a design system, or other
shared libraries, users pay for the same code multiple times. Check whether the document addresses a
shared-dependency strategy (e.g., federated shared modules, singleton enforcement) — silence here for
a module-federation architecture is a real, easy-to-miss performance and maintainability gap.

## Runtime version drift
Independently deployed remotes can drift onto different versions of shared dependencies (React, the
design system) over time, even with a shared-dependency strategy in place, if there's no enforcement.
Check for a stated compatibility contract or version-range enforcement — its absence means a remote's
independent deploy can silently break the shell or another remote at runtime, which is a **Blocker**-
level operational risk once it happens in production.

## CSS isolation failures
Independently built and deployed remotes can leak global styles into each other or into the shell (a
reset, a global class name collision) with no build-time signal that it happened. Check whether the
document addresses style isolation (CSS Modules, Shadow DOM, scoped/namespaced class strategies, or an
equivalent) — treating this as a solved problem with no stated mechanism is a common source of hard-to-
debug visual bugs across teams.

## Remote module resilience
- **What happens if a remote fails to load** (network failure, a broken deploy, a version mismatch)?
  Does the shell degrade gracefully (an isolated error boundary around that remote) or does one
  remote's failure take down the whole application? A document with no answer to this is a real gap —
  the entire premise of independent deployability is undermined if failures aren't isolated.
- **Loading/fallback UX** for a remote that's slow to load should be addressed, not just the happy
  path.

## Shell/remote contracts
- Is there a stated contract (props/API surface) between the shell and each remote, and is it
  versioned? An undocumented, implicit contract is fragile — a remote team can change something the
  shell silently depended on with no build-time warning.
- Is routing ownership clear — does the shell own top-level routing with remotes mounting into
  designated regions, or can remotes register their own routes, and if so, how are conflicts avoided?

## Deployment independence (the actual point of this architecture — verify it's real)
- Can a remote actually be deployed without redeploying the shell or other remotes? If the document
  describes micro-frontends but the actual build/deploy pipeline still requires a coordinated release
  of shell + all remotes together, the architecture isn't delivering its primary claimed benefit, and
  that's worth calling out directly — this is a common gap between the stated goal and the actual
  deployment design.

## Cross-team ownership boundaries
- Cross-reference `lenses/system-design-and-boundaries.md` — does each remote map to a clear team owner, and
  is the shell itself owned by a specific team (a "platform" team, typically) rather than being
  ownerless? An ownerless shell is a common source of stagnation and unclear decision authority.

## Severity guidance
- No shared-dependency or version-drift strategy at all → **Major**, escalate toward **Blocker** if
  the document implies remotes deploy fully independently with no compatibility enforcement.
- No remote-failure isolation (a broken remote can take down the shell) → **Major**.
- Deployment independence claimed but the actual pipeline requires coordinated releases → **Major**
  (the stated architectural benefit isn't real).
- No CSS isolation strategy → **Minor** to **Major** depending on how many independent teams style
  the UI.
