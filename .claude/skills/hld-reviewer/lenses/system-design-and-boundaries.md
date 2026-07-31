# Lens 3 — System Design & Team/Module Boundaries

**Core question:** will this architecture stay maintainable as the codebase, the data, and the
organization grow — or does it work technically today while quietly guaranteeing every team steps on
every other team's work in a year? This lens treats the system as an *engineering system*, not just a
technical diagram — most architectures that fail in practice fail here, not at the
framework/service-choice level, and the failure is rarely confined to one layer.

## Component, module & service ownership

- [ ] **Boundaries are drawn as an enforceable structure**, not just a folder/repo naming convention —
      is there a stated rule for what a module/service is allowed to call or import from, and what it
      must not reach into directly (e.g., "features may not import another feature's internals," "no
      service reads another service's database directly")?
- [ ] **Ownership is mapped**, at least loosely, to the team structure — if the document mentions more
      than one team (or growth toward one), does it say which team owns which module/service? Silence
      here for a system that clearly has or will have multiple teams is a real gap, on either side of
      the stack.
- [ ] **Shared vs. owned is distinguished** — is there a clear line between team-owned components/
      services (safe to change unilaterally) and shared infrastructure (design system, shared
      libraries, shared data stores, platform services — require cross-team coordination to change)? A
      document that treats everything as equally shared, or equally private, usually breaks down in
      practice.
- [ ] **Dependency direction is enforced, not just described** — is there a stated mechanism (lint
      rule, module-boundary tool, service-mesh policy, CI check, API gateway routing) that would
      actually catch a violation, or is the boundary purely a documentation convention nothing
      enforces?

## Shared library / platform governance

- [ ] **Shared code or shared platform capabilities have a stated packaging/versioning story** — is
      shared UI, a shared client library, or a shared internal API published as a versioned artifact
      with a compatibility contract, or just copy-pasted/tightly coupled across consumers?
- [ ] **A change-and-review process for shared surfaces is at least sketched** — who can approve a
      breaking change to a shared package, a shared schema, or a platform API that other teams depend
      on?
- [ ] **The design system's governance model is addressed**, if a UI layer with a design system
      exists — who decides what gets added, how breaking changes propagate to consumers, and how
      design and engineering stay in sync. Mentioned only as a technology choice ("we use our design
      system") with no governance story is thin.

## Team scaling patterns

- [ ] **The document names a concrete trigger for structural change** as the team grows (e.g.,
      "reconsider splitting the shared package once more than two teams depend on it independently,"
      "reconsider a service split once a domain is independently staffed and released") — not just an
      acknowledgment that growth will happen.
- [ ] **Release/deploy independence is addressed** if multiple teams will ship into the same system —
      can one team ship without blocking on another's release/deploy, or is there a single shared
      deploy gate (frontend monolith, shared service, shared migration pipeline) that will become a
      bottleneck?

## Cross-layer responsibility boundaries (client / BFF / backend services / data / infrastructure)

One of the most consequential and most commonly skipped areas — poor boundary decisions create
long-term architectural debt that's expensive to unwind. For each, the document should say explicitly
where the responsibility lives, not leave it implied:

- [ ] **Validation ownership** — is input validation duplicated appropriately (client for UX,
      server/BFF as the actual authority), or does the document imply client-side validation is
      sufficient on its own?
- [ ] **Authorization ownership** — the "is it enforced server-side" question is covered in the
      Security lens; here, check the *boundary*: does the client (or an upstream service) attempt
      business-rule authorization logic (e.g., computing whether a discount applies, whether a
      transfer is permitted) that really belongs in the service that owns that data?
- [ ] **Aggregation ownership** — if a UI or a downstream service calls multiple backend services, is
      response aggregation/shaping done in a BFF/aggregation layer (recommended for consistency and to
      avoid waterfall requests / N-plus-one fan-out) or scattered across client-side calls or
      service-to-service chatter with business logic embedded along the way?
- [ ] **Business logic placement** — is there a stated rule for what belongs in the client
      (presentation, interaction, client-only derived state) vs. what must live server-side (anything
      where the answer being wrong or bypassed has real consequences)? Silence here, combined with
      financial/health/compliance-relevant logic appearing to live only client-side, is a real finding.
- [ ] **Data ownership** — does exactly one service own the write path for each entity, or can two
      services both write to the same table/record with no stated coordination (a distributed-systems
      analogue of the frontend "two sources of truth" problem — see Lens 4)?
- [ ] **Permission enforcement boundary** — restated for this lens specifically: is there exactly one
      place permission checks are authoritative across the whole call chain, and does the document
      avoid implying an earlier layer's check is sufficient on its own?

## Severity guidance

- No module/service boundary rule at all for a system beyond a single small team → **Major**.
- Business-logic or authorization logic clearly placed only in the client (or only in a non-owning
  service) for anything with real financial/compliance consequences → **Major**, escalate to
  **Blocker** if it's the sole enforcement point with no authoritative backstop at all.
- Two services (or a service and a client) both able to write the same entity with no stated
  coordination rule → **Major**, escalate to **Blocker** if the entity is financial or safety-relevant.
- Shared component/design-system/platform-API governance entirely unaddressed for a multi-team
  Growth/Enterprise system → **Major**; for a Prototype/MVP-stage system → **Minor** or not a finding,
  per the maturity calibration in `rubric/scoring-rubric.md`.
- No stated trigger for revisiting module/service structure as the org grows → **Minor** to **Major**
  depending on how clearly growth is already implied by the document's own stated team size/timeline.

## When this system uses or is considering distributed decomposition

- If the system uses or is considering **micro-frontends / module federation**, read
  `knowledge/distributed-frontend-architecture.md` and apply it as an extension of this lens.
- If the system uses or is considering **microservices / distributed transactions**, read
  `knowledge/distributed-backend-architecture.md` and apply it as an extension of this lens.
