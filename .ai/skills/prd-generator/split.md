# Splitting a PRD Into Multiple Files

Use this file once SKILL.md's size gate is tripped. The section *content* requirements never change — everything defined in [template.md](template.md) still applies word-for-word. This file only defines where each feature physically lives, how the files link to each other, and what stays common to the whole PRD regardless of how many files it's spread across.

**Split shape: by feature/module.** Each file is named after — and owns everything about — one feature, never a generic section-type grouping. This mirrors how the business actually owns and reviews a PRD (one feature owner, one file), so a reader never has to hop across multiple files to understand a single feature end-to-end.

## Directory & Naming Convention

All feature files live together in the same spec directory as a normal single-file PRD would, and each one is named after the feature it owns:

```
docs/specs/[NNN]-[name]/
  product-requirements.md                    # index (always present, always this exact name)
  product-requirements-order-lifecycle.md    # everything about Order Lifecycle
  product-requirements-payments.md           # everything about Payments
  product-requirements-notifications.md      # everything about Notifications
```

Name each file `product-requirements-[feature-name].md`, using the feature's own name in kebab-case — not a generic label like "part 2" or "functional". The file name alone should tell a reader which feature is inside without opening it. Keep names stable once chosen; renaming a feature file later breaks every cross-reference into it.


## What Each Feature File Owns

A feature file (e.g., `product-requirements-order-lifecycle.md`) pulls together everything related to that feature, across every section-type in [template.md](template.md) — not just its Functional Requirements entry:

| Content | Notes |
|---|---|
| Persona tie-in | Which persona(s) this feature serves — link to a shared personas file if personas span multiple features, or write inline if the feature has a dedicated persona |
| User Flows | Every flow belonging to this feature (e.g., "Place Order," "Cancel Order," "Reorder"), each with happy path, alternate branches, and error paths |
| Functional Requirements | The feature's user story + EARS acceptance criteria, under its MoSCoW category |
| Non-Functional Requirements | Any performance/reliability/security targets specific to this feature |
| Detailed Feature Specification | Business rules and feature-specific edge cases |
| Scope tag | Whether this feature is MVP / Future / Out of Scope for this PRD |

A feature file is a unit — never split one feature's content across two files. If a single feature is itself too large even as its own file, break it at a feature-internal boundary — a full Flow, a full Business Rule, a full Edge Case — never mid-flow or mid-acceptance-criterion. Name the continuation `product-requirements-order-lifecycle-2.md` and add a breadcrumb back to both the index and the first file.

### Feature Boundary Example

A "feature boundary" split means the cut falls *between* two whole Features — never inside one. Everything belonging to a single Feature (its user story, its EARS acceptance criteria, and any feature-specific edge cases) stays together in the same file. This applies whether the boundary is between two different feature files, or between a feature file and its own `-2.md` continuation.

Say a PRD has grown to 15 Must-Have features and needs to split into feature files:

```
product-requirements-order-lifecycle.md   → Order Lifecycle (all flows, requirements, edge cases)
product-requirements-payments.md          → Payments (all flows, requirements, edge cases)
product-requirements-notifications.md     → Notifications (all flows, requirements, edge cases)
```

- **Correct:** Everything about Order Lifecycle — its user story, acceptance criteria, flows, and edge cases — lives entirely in `product-requirements-order-lifecycle.md`.
- **Not allowed:** Order Lifecycle's user story lands in `product-requirements-order-lifecycle.md` but its acceptance criteria spill into `product-requirements-payments.md` — that's a mid-feature cut across the wrong boundary.

If one single feature file (e.g., Order Lifecycle alone) is too large even on its own — say it has 6 flows and needs a continuation — the same rule holds at a finer grain:

```
product-requirements-order-lifecycle.md      → Flow 1 ... Flow 4
product-requirements-order-lifecycle-2.md    → Flow 5, Flow 6
```

The cut falls between whole Flows, never mid-flow or mid-acceptance-criterion. The same principle applies to any section that needs a `-2.md` continuation: find the nearest whole sub-unit boundary (a full Flow, a full Business Rule, a full Edge Case) and cut there.

## What Stays Out of Feature Files

Cross-cutting sections that span multiple features don't belong to any single feature file. They stay in the index, or in one shared file if there are several (e.g., `product-requirements-metrics-timeline.md`):

- Success Metrics / Business Metrics (a KPI like overall payment success rate usually spans features)
- Timeline & Roadmap
- Risks & Constraints
- Open Questions
- Cross-cutting Edge Cases that don't belong to one feature

A feature file can still reference these — e.g., `product-requirements-order-lifecycle.md` links to `See [Timeline & Roadmap](product-requirements.md#timeline--roadmap) for phasing.` — rather than restating them.

## Index File Skeleton (`product-requirements.md`)

```markdown
---
title: "[Feature title]"
status: draft
version: "1.0"
parts:
  - product-requirements-order-lifecycle.md
  - product-requirements-payments.md
  - product-requirements-notifications.md
---

# Product Requirements Document: [Feature title]

> **Tech-Agnostic Rule:** applies to every file in this PRD, not just this one. See SKILL.md → "Core Principle: Tech-Agnostic Always."

## Contents

This PRD is split across multiple files. Read them in this order:

1. **This file** — Engineering Digest, Executive Summary, Problem Statement, Goals, Stakeholders
2. [Order Lifecycle](product-requirements-order-lifecycle.md)
3. [Payments](product-requirements-payments.md)
4. [Notifications](product-requirements-notifications.md)

## Validation Checklist

[Identical structure to template.md's Validation Checklist — CRITICAL GATES and QUALITY CHECKS. Checked items must reflect the state of the PRD across ALL files, not just this one.]

## Engineering Digest

[Same content and rules as template.md. This is the one section most likely to reference facts drafted in other files — pull the final numbers/features from each feature file once they're done, not before.]

## Executive Summary

[Same as template.md.]

## Problem Statement

[Same as template.md — Context, Problem, Why Now, Reality-Check Gate note.]

## Goals

[Same as template.md — Product Goals, Non-Goals.]

## Stakeholders

[Same as template.md.]
```

## Feature File Skeleton

Every feature file opens with the same breadcrumb before its first heading, and nothing else precedes it:

```markdown
[← Back to PRD index](product-requirements.md)

---

# Order Lifecycle

## User Personas
[Persona(s) this feature serves, or a link if defined elsewhere]

## User Flows
### Flow 1: Place Order
...
### Flow 2: Cancel Order
...

## Functional Requirements
### Feature: Order Lifecycle
- **User Story:** ...
- **Acceptance Criteria (EARS):** ...

## Non-Functional Requirements
[Only the targets specific to this feature]

## Detailed Feature Specification
**Business Rules:** ...
**Feature-Specific Edge Cases:** ...

## Scope
MVP Scope | Future Scope | Out of Scope — [state which, and why]
```

Use the exact section headings from template.md — don't rename, reorder within a file, or add framing prose above the breadcrumb. The breadcrumb line and the feature's own top-level heading are the only structural additions beyond what template.md already specifies.

## Cross-Referencing Between Parts

Files will need to reference each other (e.g., a User Flow in one feature file references another feature; a KPI in the index references a tracking event defined in a feature file; Estimation Blockers references an Open Question). Always link, never restate:

- `See [Payments](product-requirements-payments.md#functional-requirements) for acceptance criteria.`
- `Blocks Order Lifecycle — see [Open Questions](product-requirements.md#open-questions).`

This is the same Single-Source-of-Truth rule SKILL.md already applies within one file, extended across file boundaries: a fact lives in exactly one file, and every other file links to it rather than paraphrasing it. If you find the same requirement stated in two files, collapse it to whichever file owns that feature.

## What Stays Whole-PRD, Not Per-File

These apply to the PRD as a single conceptual document, evaluated across every file together, even though they're written once (in the index):

- **Domain Invariants Gate** — run once, logged in the index's Engineering Digest or Problem Statement, but its resolutions (features, NFRs, Out-of-Scope entries) may live in whichever feature file actually owns that content.
- **Reality-Check Gate** — lives in the index's Problem Statement, same as an unsplit PRD.
- **Multi-Angle Final Validation** — run across every file produced as one pass; a gap found in any feature file is reported and fixed the same as a gap in the index.
- **[NEEDS CLARIFICATION] markers** — none may remain in any file, not just the index.

## Split Completeness Check

Before reporting a split PRD as done, verify nothing was lost or dropped in moving from a single conceptual document into separate files. This check runs against the full set of template.md sections, not just the ones that were top of mind while drafting:

1. **Build a checklist of every required section from [template.md](template.md)** (Validation Checklist, Engineering Digest, Executive Summary, Problem Statement, Goals, Stakeholders, User Personas, User Flows, Functional Requirements, Non-Functional Requirements, Detailed Feature Specifications, Edge Cases, MVP/Future/Out of Scope, Estimation Blockers, Success Metrics, Timeline & Roadmap, Risks & Constraints, Open Questions, Supporting Research).
2. **Locate each one in the file set** — either in the index or inside a specific feature file — and confirm it actually has content, not a placeholder or an omission.
3. **Confirm every feature discussed during drafting has its own file (or a clearly-owned entry)** — cross-check the index's `parts:` frontmatter list and the Engineering Digest's "Features at a glance" line-up against what's actually on disk. A feature that came up in conversation but never got a file is a gap, not a scoping decision, unless it was explicitly marked Out of Scope.
4. **Confirm every cross-reference resolves** — every `See [X](file.md#anchor)` link points to a file and heading that actually exists; a dangling link usually means the referenced content was never written, not just misfiled.
5. **Confirm no content was duplicated instead of moved** — if the same requirement, flow, or edge case appears in two files, that's not "extra coverage," it's a Single-Source-of-Truth violation and a sign the split wasn't done cleanly; collapse it to the one file that owns it.
6. **If anything is missing, write it before reporting completion** — a split PRD is not done because all files exist; it's done because everything the single-file version would have contained is present somewhere in the set.

## Reporting a Split PRD

When presenting completion status, list every file produced (see SKILL.md → Output Format). Never present only the index and imply the PRD is done — a split PRD isn't complete until every feature file exists and passes validation.
