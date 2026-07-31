# Traceability Matrix — <Feature Name>

> **Rules:** Stage 1 populates REQ ID + Summary only. Each subsequent stage fills
> in its own column using its own artifact as the source. No column is re-derived
> from scratch; the table is only appended to. Empty cells in later columns mean
> coverage has not yet been established by that stage.
>
> **This is a gate, not paperwork.** `hooks/stop.js` blocks the Stage 9 → Stage 10
> handoff while any in-scope requirement has an empty HLD, LLD or Code cell. Fill
> cells honestly — a fabricated coverage link defeats the gate rather than
> satisfying it.

---

## Scope: `<backend | frontend | fullstack>`

| REQ ID | Requirement Summary | HLD Coverage | LLD Coverage | Code Coverage | Test Coverage |
|--------|---------------------|--------------|--------------|---------------|---------------|
| REQ-001 | <one-line summary from the PRD> | | | | |
| REQ-002 | | | | | |

### Column ownership

| Column | Filled by | Source artifact |
|---|---|---|
| REQ ID / Summary | Stage 1 | `product-requirements.md` (+ all `parts:` files) |
| HLD Coverage | Stage 3a / 3b | `hld-backend.md` / `hld-frontend.md` |
| LLD Coverage | Stage 5a / 5b | `lld-backend.md` / `lld-frontend.md` |
| Code Coverage | Stage 8 | source files |
| Test Coverage | Stage 10 | `test-report.md` |

Cell format: a link to the specific section or file that covers the requirement,
e.g. `hld-backend.md#4-database-schema-design` or `src/services/DocumentService.java`.

---

## Metadata

- **Created:** <ISO date, Stage 1>
- **Last updated:** <ISO date>
- **PRD parts:** <none | list of product-requirements-*.md files>
