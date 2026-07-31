# Project Operating Rules — READ BEFORE ANY ACTION

This repository runs a **locked 10-stage PRD→Production pipeline**. It is not a
general-purpose codebase you improvise in.

## The one rule that overrides everything

> **Every feature request routes through `.ai/workflows/prd-to-prod.md`, start to
> finish, stage by stage, using only the skills that workflow names.**

If a user asks you to build, fix, add, or change a feature — you run the pipeline.
You do not switch into generic assistant mode, you do not reach for an unlisted
skill, and you do not skip ahead because a stage "seems unnecessary". Read
`.ai/workflows/prd-to-prod.md` in full before your first action on any feature request.

**Exception:** work *on the pipeline itself* (hooks, settings, docs, the workflow
file) is ordinary engineering work and does not run through the pipeline.

## Before you start

1. Read `.ai/workflows/prd-to-prod.md` — the authoritative spec.
2. Read `.ai/state/workflow-state.json` — where the pipeline currently is.
3. Resume at the recorded stage. **Never** start at a stage other than 1 unless
   the user explicitly names one this session.

## The 10 stages and their locked skills

| Stage | Skill | Runs when |
|---|---|---|
| 1 Requirement Analysis | `prd-generator-split` | always |
| 2 PRD Review | `prd-reviewing` | always |
| 3 HLD — Unified System | `system-hld-designer` | always |
| 4 HLD Review | `hld-reviewer` | always |
| 5a LLD Backend | `backend-lld-architect` | scope ∈ {backend, fullstack} |
| 5b LLD Frontend | `frontend-lld-designer` | scope ∈ {frontend, fullstack} |
| 5c LLD Consistency | *(orchestrator, no skill)* | scope = fullstack |
| 6 LLD Review | `lld-reviewer` and/or `frontend-lld-review` | always |
| 7 Planning | `edited-plan-skill` | always |
| 8 Implementation | `trading-platform-coding` | always |
| 9 Code & Arch Review | `code-reviewer` | always |
| 10 QA & Browser | `full-stack-test-suite` | always |

Only these 12 skills exist in `.claude/skills/`. Anything in `.ai/skills/` is
**disabled for this workflow** and is not discoverable by design.

## Approval gates — use `AskUserQuestion`

Every stage ends in a HARD GATE. Present the artifact, then call
**`AskUserQuestion`** (not `ask_question` — that tool does not exist here) with:

- **APPROVE** — record complete, proceed
- **ITERATE** — re-run this stage's locked skill with stated changes
- **REJECT** — halt or roll back
- **JUMP** — user names an explicit target stage

`AskUserQuestion` caps at 4 options plus an auto-provided "Other" — CANCEL goes
through "Other". Each sub-stage that runs (5a, 5b, 5c) gets its **own** gate; a fullstack feature never
gets one combined LLD approval. Stage 3 is one node, so it has exactly one gate.

An artifact existing on disk is **never** approval. It must be presented and
approved in this session.

## Where artifacts go

- **PRD** → `docs/specs/[NNN]-[name]/product-requirements.md` (+ any `parts:` files)
- **Everything else** → `.ai/artifacts/` (the Stage 3 HLD is a single `hld.md`)
- **Source code** → wherever the LLD specifies

The guards also recognise artifacts under `.ai/stages/**` for backward
compatibility, but `.ai/artifacts/` is the canonical target for new work.

## Quality bar — this is the whole point

`prd-to-prod.md` mandates **ZERO LAZINESS, ZERO SHORTCUTS**. Enforced mechanically
by `hooks/pre-tool.js`, which will **deny your write** if an artifact:

- falls below its minimum depth floor,
- is missing a mandatory section,
- contains `TBD` / `<placeholder>` / elided-implementation markers in a design doc,
- lacks required diagrams (HLD),
- lacks a canonical `**Verdict:**` line (reviews),
- or is produced while its gating review says `CHANGES_REQUESTED`.

When a hook denies a write, **fix the artifact**. Do not route around it by
writing somewhere unprotected, renaming the file, or thinning the scope. The
floors are calibrated well below what a serious artifact needs.

### Reviewer verdicts must be machine-readable

Every review artifact must contain a line exactly like:

```
**Verdict:** APPROVED
```

Valid values: `APPROVED`, `APPROVED_WITH_CONDITIONS`, `CHANGES_REQUESTED`.

The reviewers' own wording is normalised automatically, so these are equally valid:
`Ready for Implementation` / `Approve` → APPROVED · `Ready with Conditions` /
`Approve with required changes` → APPROVED_WITH_CONDITIONS · `Not Ready` /
`Do not build from this yet` → CHANGES_REQUESTED.

`CHANGES_REQUESTED` is hard-blocking — downstream writes are denied until resolved.

### Traceability is a gate, not paperwork

`.ai/artifacts/traceability.md` is one running table, appended to — never
regenerated. Each stage fills only its own column. **Stage 9 will not hand off to
Stage 10** while any in-scope requirement has an empty HLD/LLD/Code cell; the Stop
hook blocks it. Fill cells honestly — a fabricated coverage link is worse than an
empty one because it defeats the gate.

## Plugins: caveman + ponytail run at ULTRA here

Both are enabled repo-wide in `.claude/settings.json`, so every clone on every machine
gets them automatically. They pull in the opposite direction from the ZERO SHORTCUTS
policy above, so the boundary between them is **not** negotiable:

### Caveman ultra governs what you SAY. It never governs what you WRITE.

| Surface | Style |
|---|---|
| Chat replies, status updates, summaries, explanations | **caveman ultra** — terse, fragments, no filler |
| Artifact files on disk (`*.md`, `tasks.json`, source code) | **full prose, full depth** — caveman does not apply |

An HLD, LLD, PRD or review is a document handed to engineers who were not in this
conversation. Compressing it is a workflow violation, and `hooks/pre-tool.js` will
**deny the write** on the depth floor anyway. Never caveman an artifact to make it fit;
never thin an artifact because caveman is on.

Caveman also drops out entirely — per its own Auto-Clarity rule — for approval gates,
security findings, and irreversible-action confirmations. Every `AskUserQuestion` gate
is a decision point, so present those in plain language.

### Ponytail ultra governs HOW MUCH you build. Not how completely.

| Ponytail decides | The workflow decides |
|---|---|
| Whether a feature/abstraction is needed at all | Whether what IS specified is fully implemented |
| stdlib before a new dependency | That every controller, service, repository, DTO and annotation is written |
| One line over fifty, YAGNI | No stubs, no placeholders, no "sample" scaffolds |

So: push back on unrequested scope at Stage 1 and Stage 7, where scope is actually being
decided. Once the LLD is APPROVED, ponytail does **not** authorise implementing less than
it specifies — that is Stage 8's "Complete & Rigorous Production Code First" directive, and
`code-reviewer` will reject stubs at Stage 9.

**If the two ever genuinely conflict, the workflow wins.** These plugins are a style layer
over the pipeline, never an override of it. Turn them off for a session with
`/caveman off` and `/ponytail off` if they are getting in the way.

## Halting

If a locked skill cannot handle part of the task, or you discover scope was wrong,
or an input is missing — **HALT and ask**. Never substitute an unlisted skill,
never infer missing content, never change scope unilaterally. Halting is correct
behavior here, not failure.

## Useful commands

- `/prd-to-prod <feature>` — start or resume the pipeline
- `/workflow-status` — show the current gate and blockers
- `/workflow-reset` — reset to Stage 1 (archives existing artifacts)
- `node .ai/dashboard/server.js` — live dashboard (no dependencies)
- `node hooks/test/run-tests.js` — prove the guards actually fire
