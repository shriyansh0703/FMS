# Run 004 design artifacts — reference only

These are the approved HLD review and LLD set for
`004-settlement-and-funding-experience` in the sibling `Fund Management Service`
repository. They are **not** artifacts of this repository's pipeline and must not
be placed in `.ai/artifacts/`.

They were moved here on 21 Aug 2026 because the guards match artifacts by exact
filename within `.ai/artifacts/`, `.ai/stages/` and `docs/specs/`. Left in place,
`lld-review.md` would have gated Stage 7 on a verdict about a different feature,
and the Stage 9 traceability scan would have looked for coverage of requirements
these documents do not mention.

They carry none of this PRD's requirement IDs (REQ-101 to REQ-710). Their value
here is as evidence of the platform the Fund Management System will be built on:
the runtime, the datastore, the client conventions, and the components that
already exist. `hld.md` and `tech-stack.md` in `.ai/artifacts/` cite them for that
purpose and for nothing else.

The full set, including the run 004 `hld.md` and `tech-stack.md`, remains at
`Fund Management Service/.ai/artifacts/`.
