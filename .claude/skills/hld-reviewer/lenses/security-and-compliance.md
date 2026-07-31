# Lens 6 — Security, Compliance & Data Privacy

**Core question:** would this survive a real attacker probing every layer — client, API, service,
data store, infra — or a real compliance audit, or does it just use the vocabulary of security without
the mechanism?

## Authentication & session

- [ ] Token/session storage named explicitly and defensible — localStorage/sessionStorage for
      access/refresh tokens on the client is a red flag on any system with real stakes. HttpOnly/
      Secure/SameSite cookies via a BFF, or short-lived in-memory tokens, are the safer patterns.
- [ ] Session lifecycle addressed: expiry, renewal, forced invalidation (logout everywhere,
      admin-revoked sessions), and how that invalidation actually propagates to every service that
      checks auth (not just the edge).
- [ ] MFA/step-up authentication addressed if the domain plausibly needs it (financial, health,
      privileged admin actions).
- [ ] CSRF addressed if cookie-based sessions are used — its total absence is a real gap.
- [ ] Service-to-service authentication is addressed (mTLS, signed service tokens, a service mesh's
      identity layer) — "internal network = trusted" with no authentication between services is a
      common, serious gap in backend designs.

## Authorization

- [ ] **The server (or the owning service) is the source of truth.** Hiding UI for unauthorized
      actions, or trusting a caller's claimed role without independent verification, without
      independently enforcing it at the authoritative layer, is a **Blocker** — client-side or
      caller-asserted authorization alone is not authorization.
- [ ] A capability/permission model is at least sketched, and it's revocable/re-checkable, not cached
      indefinitely at any layer.
- [ ] Cross-reference `lenses/system-design-and-boundaries.md`'s cross-layer boundary checks — does
      business or authorization logic that should live at the authoritative service appear to live
      only in the client or in a non-owning layer?
- [ ] For service-to-service calls: does the *calling* service's identity get checked, or does any
      authenticated caller get treated as fully trusted regardless of what it's asking for
      (over-broad internal trust boundaries)?

## Data exposure

- [ ] Secrets/keys never ship to the browser, and never sit in plaintext in config/environment that a
      broader-than-necessary set of services can read — any privileged credential in client-side code
      or over-broadly-scoped service config is a **Blocker**.
- [ ] Sensitive data (PII, financial, health) is explicitly scrubbed from telemetry/analytics/logs at
      every layer that touches it, not just at the edge.
- [ ] Encryption is addressed both **in transit** (TLS between every hop, including internal
      service-to-service) and **at rest** (datastore-level encryption, and field-level encryption for
      especially sensitive fields where plausibly required).
- [ ] CSP / injection defense addressed for any system rendering user-generated or third-party
      content; SQL/NoSQL injection and deserialization risks addressed for any backend accepting
      structured input.
- [ ] If the system uses SSR, check for server-side singleton or shared-instance leakage across
      requests (see `knowledge/rendering-failure-modes.md`) — a data-exposure risk specific to
      server-rendered frontends, easy to miss in a review that only thinks in terms of client-side
      XSS/CSRF.
- [ ] **Audit logging** is addressed for sensitive/privileged actions (who did what, when) — required
      for most regulated domains and genuinely useful for any system handling money or access control,
      independent of regulation.

## Compliance, Data Privacy & Residency (only where the domain plausibly requires it)

- [ ] Regulatory frame at least acknowledged for financial/health/regulated data (even a "validate
      exact rules with Legal/Compliance" note counts — silence does not).
- [ ] **Applicable privacy regimes are named**, not just "we'll be compliant" — GDPR/UK-GDPR for EU/UK
      users, CCPA/CPRA for California residents, HIPAA for US health data, or an equivalent regional
      law, based on the stated or inferred user base.
- [ ] **Data subject rights are addressed** where a privacy regime applies — access, export/portability,
      correction, and erasure/"right to be forgotten" — including whether erasure actually propagates to
      backups, caches, logs, and downstream/vendor copies, or only to the primary datastore.
- [ ] **Lawful basis / consent capture is addressed** for personal data collection and any secondary use
      (analytics, model training, marketing) — is consent explicit, scoped, and revocable, or assumed?
- [ ] **Data residency/localization is addressed** if the domain or regulation requires data to stay in
      a specific region — is storage and processing (including backups, caches, and third-party/vendor
      processing) actually pinned to that region, or does the design silently replicate data cross-border
      (cross-reference the retention checks in Lens 4)?
- [ ] **Cross-border transfer mechanism is named** if data legitimately needs to leave its region of
      origin (e.g., SCCs, an adequacy decision, or an equivalent named mechanism) rather than left
      implicit.
- [ ] **Data classification/tiering is addressed** — does the document distinguish which data is
      regulated/sensitive vs. not, so that the above checks can actually be scoped to the right fields?

## Severity guidance

- Client-side-only or caller-asserted-only authorization, anywhere in the call chain → **Blocker**.
- Shippable secrets/keys, or over-broadly-scoped service credentials → **Blocker**.
- Total absence of a Security section on a system handling auth, payments, or sensitive data →
  **Blocker** (see the dimension-absent handling in `rubric/scoring-rubric.md`).
- No service-to-service authentication story on an internal network for a system with real stakes →
  **Major**, escalate to **Blocker** if a compromised low-trust service could reach high-value data
  with no additional check.
- Sensitive data with no telemetry-scrubbing story, at any layer → **Major**, escalate to **Blocker**
  if the document actively describes logging sensitive fields.
- Token storage in localStorage for a real-stakes system, unqualified → **Major**, escalate to
  **Blocker** for financial/health systems.
- No at-rest encryption story for sensitive data in the primary datastore → **Major**, escalate to
  **Blocker** for regulated data with a named compliance requirement.
- CSRF unaddressed with cookie-session auth → **Major**.
- No named privacy regime (GDPR/CCPA/HIPAA/equivalent) for a system that clearly processes EU/UK,
  California, or health data → **Major**, escalate to **Blocker** if the system already handles real
  user data in production with no compliance story at all.
- A stated data-residency/localization requirement not actually enforced in storage or vendor
  processing (e.g., EU-resident data replicated to a non-EU region with no named transfer mechanism)
  → **Major**, escalate to **Blocker** for regulated data with no transfer mechanism at all.
- Erasure/right-to-be-forgotten addressed for the primary datastore only, with no mention of backups,
  caches, logs, or downstream vendor copies → **Minor** to **Major** depending on how sensitive the
  data is.
