# PRD-to-Prod Workflow — LOCKED SKILL MODE (Default Agent Driver)

## Governing Rule

This workflow is the **sole and exclusive driver** of agent behavior for any feature request in this project. There is no other mode of operation. If the user asks to build, fix, add, or change a feature, this workflow runs — start to finish, stage by stage, using **only** the skills named below. No other skill, tool, or freeform approach may be substituted, blended in, or used as a fallback, regardless of how well-suited it might seem for the task at hand.

If a stage's locked skill cannot handle some part of the task, you do NOT reach for another skill to cover the gap. You HALT and report the limitation to the user. Silently substituting or supplementing with an unlisted skill is a violation of this workflow, not a helpful workaround.

---

## Scope Declaration (resolved at Stage 1, gates Stage 5)

Every feature entering this pipeline must be classified into exactly one scope before HLD begins, recorded as an explicit field in the PRD index file (`docs/specs/[NNN]-[name]/product-requirements.md`):

`scope: backend | frontend | fullstack`

### How scope gets set:
1. If the user states it explicitly ("this is a backend-only change", "frontend only, API already exists", "full feature, both sides") — use that.
2. `prd-generator-split` MUST ask mandatory clarifying questions to confirm scope (`backend`, `frontend`, or `fullstack`), target behavior, edge cases, and constraints before finalizing `docs/specs/[NNN]-[name]/product-requirements.md`.

This field is a hard switch on **Stage 5 only**. Stage 5 (Low-Level Design) splits into a backend sub-stage and a frontend sub-stage below; only the sub-stage(s) matching the declared scope may run.

**Stage 3 is NOT gated by scope.** The High-Level Design is a single unified document covering client, services, data and infrastructure together, and it runs for every scope. Scope still shapes it — a `backend` feature states its client contract without designing a UI that isn't changing — but it never splits the stage or skips it.

Getting scope wrong wastes an entire sub-stage's work, so do not guess past genuine ambiguity — ask once, then lock it. If a later stage discovers scope was wrong (e.g. Stage 5a discovers the backend change requires a UI change too), HALT and ask the user to explicitly confirm scope should change — never start running frontend skills unilaterally because it seemed necessary.

---

## Locked Skill Map (exhaustive — nothing else may run)

| Stage | Name | Runs When | Skill(s) — ONLY these, nothing else |
|---|---|---|---|
| 1 | Requirement Analysis | always | `prd-generator-split` |
| 2 | PRD Review | always | `prd-reviewing` |
| 3 | High-Level Design — Unified System | always | `system-hld-designer` |
| 4 | HLD Review | always | `hld-reviewer` |
| 5a | Low-Level Design — Backend | scope = backend or fullstack | `backend-lld-architect` |
| 5b | Low-Level Design — Frontend | scope = frontend or fullstack | `frontend-lld-designer` |
| 5c | LLD Consistency Pass | scope = fullstack only | no skill — orchestrator cross-checks API contract sections between 5a and 5b outputs; not a regeneration |
| 6 | LLD Review | always (reviews whichever of 5a/5b/5c ran) | `frontend-lld-review`, `lld-reviewer` — run only the side(s) matching scope: backend-only → `lld-reviewer` alone; frontend-only → `frontend-lld-review` alone; fullstack → both |
| 7 | Planning | always | `edited-plan-skill` |
| 8 | Implementation | always | `trading-platform-coding` |
| 9 | Code & Architecture Review | always | `code-reviewer` |
| 10 | QA Testing & Browser Validation | always | `full-stack-test-suite` |
| 11 | Security Review — FINAL GATE | always | `security-review` |
| Multi | Traceability Matrix | incremental across pipeline | no skill — orchestrator-maintained running table (`traceability.md`); not a separate skill invocation |

### Where the locked skills live

The 13 skills named above are the **only** skills in `.claude/skills/`, which is the sole directory Claude Code discovers project skills from. The lock is therefore structural, not honour-system: an unlisted skill is not merely forbidden, it is **invisible to the runtime** and cannot be invoked.

Skill folders that remain in `.ai/skills/` (currently `prd-generator`, `requirements-analysis-2`, `backend-hld-architect`, `frontend-hld-designer`, `hld-reviewer-v1-single-file`) and the legacy stage skills under `.ai/stages/*/SKILL.md` are **explicitly disabled for this workflow**. They are retained for reference only. Their presence does not authorize their use here, and Claude Code will not surface them.

Skill file contents are treated as immutable by this port — the locked skills were relocated byte-for-byte and must not be edited to accommodate tooling. Path and runtime concerns are handled by the hooks (`hooks/utils/config.js` recognises artifacts under `.ai/artifacts/`, `.ai/stages/**` and `docs/specs/**`), never by rewriting a skill.

### Stage 1 skill: why `prd-generator-split`

Stage 1 originally used `prd-generator`, which is now disabled. That skill's `SKILL.md`
referenced two support files it could not actually load — `good-prd.md` (which lived at
`reference/good-prd.md`) and `split.md` (which did not exist in that folder at all). The
missing `split.md` is the file defining the multi-file layout, naming and cross-linking
rules used when a PRD trips the size gate, so the `parts:` splitting behaviour this
workflow depends on had no rules backing it.

`prd-generator-split` carries the same instructions with all four support files present and
correctly referenced (`template.md`, `validation.md`, `split.md`, `good-prd.md`). Its
support filenames are lowercase and its frontmatter `name` matches its directory, so the
skill resolves unambiguously.

---

## Non-Negotiable Rules

1. **Skill lock is absolute.** Each stage may invoke only the skill(s) listed for it above. Not "primarily," not "mostly" — only.
2. **No silent skipping, no silent reuse.** Every stage is a hard gate. An artifact existing on disk is never itself approval — it must be shown to the user and explicitly approved this session before the workflow proceeds.
3. **No fabrication.** If a required input is missing, halt and state exactly what's missing. Never generate a stand-in or infer content to keep moving.
4. **No unauthorized jump-backs.** If a locked skill discovers a blocker mid-stage, halt and ask the user to explicitly confirm the next step. Never decide this unilaterally.
5. **No default entry-point guessing.** Only start at a stage other than 1 if the user explicitly names it this session.
6. **Verdict gates are hard-blocking.** A `CHANGES_REQUESTED` verdict cannot be worked around by proceeding anyway.
7. **This workflow is the only behavior.** The agent does not switch into generic assistant mode, general coding help, or ad hoc skill invocation for feature-development requests in this project. Every feature request routes through this pipeline.
8. **No unauthorized scope changes.** Scope, once set in Stage 1, does not silently change downstream — a scope change requires explicit user confirmation, same as a jump-back does.
9. **ZERO LAZINESS & ZERO SHORTCUTS POLICY ACROSS ALL STAGES:**
   - The agent MUST exhaustively analyze, think through, and document every stage without taking shortcuts, using placeholders, summarizing prematurely, or generating partial templates.
   - Speed or quick completion is strictly secondary to completeness and rigorous technical depth.
   - Any attempt to produce "high-level summaries" where detailed specs are required, or "sample code/scaffolds" instead of production-ready implementations, is a direct workflow violation.
   - This policy applies identically to every sub-stage (5a, 5b, 5c) — splitting a stage into sub-stages does not permit shallower output in either half; each sub-stage is held to full depth on its own. Stage 3 is a single unified document and is held to the depth of both halves combined, never to the depth of one.

---

## Approval Protocol (Interactive UI Mode)

At the completion of EACH AND EVERY gate (especially after generating or reviewing an artifact), the agent MUST use the **`AskUserQuestion` tool** to present an interactive UI modal with the following options. 

**DO NOT wait for text input in the chat.** You MUST invoke the `AskUserQuestion` tool (`multiSelect: false`) with these exact options as the answers:

1. **APPROVE** — record stage complete; proceed to next stage.
2. **ITERATE** — fix issues or re-run current stage's locked skill(s) with stated changes.
3. **REJECT** — explicitly reject the artifact; halt the workflow or rollback.
4. **JUMP** — explicitly name target stage to jump to.

**CANCEL** (completely abort the workflow) is reached via the "Other" free-text option, which `AskUserQuestion` provides automatically. This is not a downgrade of the option — `AskUserQuestion` accepts a maximum of **4 explicit options per question** plus the auto-provided "Other", so CANCEL is mapped there deliberately rather than dropped.

**ABSOLUTE MANDATORY RULE:** You must literally pop up the interactive UI using `AskUserQuestion`. If the user chooses anything other than APPROVE, you follow their selection. This ensures a strict, click-to-approve gating system for all stages.

This applies individually to 5a and 5b as well — each sub-stage that actually runs gets its own `AskUserQuestion` gate. A fullstack feature does not get a single combined approval covering both backend and frontend LLD; it gets one gate per sub-stage that ran, plus one for the 5c consistency pass if applicable. Stage 3 is a single node and therefore has exactly one gate regardless of scope.

---

## Required Inputs Per Stage

| Stage | Requires | Produces |
|---|---|---|
| 1. Requirement Analysis | — (raw user input) | `docs/specs/[NNN]-[name]/product-requirements.md` (index file, with scope field, and optional feature files in `parts:`), `traceability.md` (initial table) |
| 2. PRD Review | `docs/specs/[NNN]-[name]/product-requirements.md` (+ any feature files listed in frontmatter `parts:`) | `prd-review.md` |
| 3. High-Level Design — Unified System | `prd-review.md` (APPROVED) | `hld.md`, `tech-stack.md`, `traceability.md` (updated) |
| 4. HLD Review | `hld.md`, `tech-stack.md`, `docs/specs/[NNN]-[name]/product-requirements.md` (+ any feature files listed in frontmatter `parts:`) | `hld-review.md` |
| 5a. Low-Level Design — Backend | `hld-review.md` (APPROVED), scope includes backend | `lld-backend.md`, `traceability.md` (updated) |
| 5b. Low-Level Design — Frontend | `hld-review.md` (APPROVED), scope includes frontend | `lld-frontend.md`, `traceability.md` (updated) |
| 5c. LLD Consistency Pass | 5a and 5b both APPROVED, scope = fullstack | `lld.md` |
| 6. LLD Review | output of 5a/5b/5c (whichever ran), `hld.md` | `lld-review.md` |
| 7. Planning | `lld-review.md` (APPROVED) | `planning.md`, `tasks.json` |
| 8. Implementation | `planning.md` / `tasks.json` (APPROVED) | Source Code, `traceability.md` (updated) |
| 9. Code & Architecture Review | Source Code, LLD output + `tasks.json` | `review.md` |
| 10. QA Testing & Browser Validation | `review.md` (APPROVED), `docs/specs/[NNN]-[name]/product-requirements.md` (+ all feature files in `parts:`) acceptance criteria | `test-report.md`, `browser-report.md`, `traceability.md` (updated) |
| 11. Security Review — FINAL GATE | `test-report.md` + `browser-report.md` (APPROVED), Source Code, `review.md`, LLD output, OpenAPI/Swagger spec (when the feature has an HTTP surface) | `security-review.md` |
| Incremental | Artifact produced in stages 1, 3, 5a/5b, 8, 10 | `traceability.md` (appended per stage) |

"APPROVED" means explicitly approved this session via the UI Approval Protocol.

---

## Traceability Matrix Artifact (`.ai/artifacts/traceability.md`)

The traceability matrix is a single running table, NOT regenerated from scratch each stage, only appended to:

| Requirement ID | Requirement Summary | HLD Coverage | LLD Coverage | Code Coverage | Test Coverage |
|---|---|---|---|---|---|
| REQ-001 | ... | hld.md#section-X | lld-backend.md#section-Y | src/services/X.ts | test-report.md#REQ-001 |

### Rules for this artifact:
- Stage 1 creates the table with Requirement ID + Summary columns populated, other columns empty.
  - **Split PRD Awareness:** When populating Stage 1 Requirement IDs, the orchestrator MUST read the index file (`docs/specs/[NNN]-[name]/product-requirements.md`), check its frontmatter `parts:` list, and source Requirement IDs from the index PLUS every listed feature file (`product-requirements-[feature-name].md`) when split.
- Each subsequent stage (3, 5a/5b, 8, 10) fills in ONLY its own column for rows it covers, using its own artifact as the source — it does not re-derive or re-check earlier columns.
- Before Stage 9 hands off to Stage 10 (QA), add one check (not a full skill invocation, similar in spirit to the existing 5c Consistency Pass) that scans `traceability.md` for any requirement row with a gap (an empty HLD/LLD/Code column despite the requirement being in scope) and HALTs with that gap listed if found, rather than letting it silently proceed to QA.
- This is the mechanism that gives you end-to-end "nothing missed" coverage cheaply — one scan of a table, instead of every reviewer re-reading the entire upstream chain from scratch.

---

## The 11-Stage Pipeline (Locked)

1. **Stage 1 — Requirement Analysis** (`docs/specs/[NNN]-[name]/product-requirements.md`)
   - *Skill*: `prd-generator-split` only.
   - *STRICT MANDATORY DIRECTIVE*: No shortcuts. The agent MUST ask clarifying questions to the user during this stage to flesh out functional/non-functional requirements, edge cases, user personas, failure modes, acceptance criteria, and exact scope (`backend` | `frontend` | `fullstack`). Generic or surface-level PRDs are forbidden.
   - The primary entry-point artifact is written to `docs/specs/[NNN]-[name]/product-requirements.md` (the index file).
   - If a size gate trips (roughly 800 lines / 6,000 words, >4 personas, >6 flows, or >10 Must-Have features), `prd-generator-split` generates split feature files (`product-requirements-[feature-name].md`) and lists them in the index's frontmatter `parts:` array.
   - This stage must also resolve and explicitly state the `scope` field (`backend` | `frontend` | `fullstack`) at the top of the PRD index file per the Scope Declaration section above.
   - *Gate*: HALT. Present `docs/specs/[NNN]-[name]/product-requirements.md` (and all split parts if present). Use `AskUserQuestion` tool for approval.

2. **Stage 2 — PRD Review** (`prd-review.md`)
   - *Skills*: `prd-reviewing` only.
   - *STRICT MANDATORY DIRECTIVE*: Exhaustively audit every requirement for ambiguities, missing edge cases, security flaws, and feasibility issues. Rubber-stamp reviews are strictly prohibited.
   - *Parts-Aware Review Requirement*: The reviewer MUST first read the index file (`docs/specs/[NNN]-[name]/product-requirements.md`) and check its frontmatter `parts:` list. If `parts:` is non-empty, the reviewer MUST read the index file PLUS every listed feature file (`product-requirements-[feature-name].md`) as one unified PRD. In addition to the standard review directive, when `parts:` is non-empty, the reviewer MUST run the explicit `VALIDATE.md -> Split-File Consistency` checklist to verify cross-file breadcrumbs, TOC links, and single-source-of-truth non-duplication.
   - *Gate*: HALT. Present `prd-review.md`. Use `AskUserQuestion` tool for approval.

3. **Stage 3 — High-Level Design: Unified System** (`hld.md`)
   - Runs for EVERY scope. There is no backend/frontend split at this stage — one document covers client, services, data and infrastructure together.
   - *Skill*: `system-hld-designer` only.
   - *STRICT MANDATORY DIRECTIVE*: Must map complete component hierarchies, system context, sequence diagrams, integration boundaries, capacity estimates, data/privacy lifecycle, and the technology stack — for the whole system. Producing a backend-only or frontend-only design here is a workflow violation: client and server decisions constrain each other and must be made together. High-level hand-waving or skipping architectural diagrams is prohibited.
   - *Scope note*: `scope` does NOT gate this stage. It still gates Stage 5 (5a/5b/5c) and it still governs which layers the design must go deep on — a `backend` feature still states its client contract, it simply does not design a UI that isn't changing.
   - *Gate*: HALT. Present `hld.md` & `tech-stack.md`. Use `AskUserQuestion` tool for approval.

5. **Stage 4 — HLD Review** (`hld-review.md`)
   - *Skills*: `hld-reviewer` only.
   - Reviews the single unified `hld.md`. The reviewer assesses every layer the document actually covers; it must not flag an absent UI design as a defect when scope is `backend` and no UI is changing.
   - *STRICT MANDATORY DIRECTIVE*: Rigorously red-team the proposed system architecture for scalability bottlenecks, security gaps, and maintainability concerns.
   - *Parts-Aware Coverage Check*: The reviewer must explicitly check each functional requirement in the PRD maps to something in the HLD, and flag any requirement with no corresponding architectural coverage. To conduct this check, the reviewer MUST read the PRD index file (`docs/specs/[NNN]-[name]/product-requirements.md`), check its frontmatter `parts:` list, and map requirements across the index PLUS every feature file listed in `parts:` when split.
   - *Gate*: HALT. Present `hld-review.md`. Use `AskUserQuestion` tool for approval.

6. **Stage 5a — Low-Level Design: Backend** (`lld-backend.md`)
   - Runs only if `scope` = `backend` or `fullstack`.
   - *Skill*: `backend-lld-architect` only.
   - *STRICT MANDATORY DIRECTIVE*: Must detail 100% of data models, DB schemas, exact API signatures, request/response bodies, state management patterns, and error handlers for the backend. No placeholder schemas or "TBD" parameters allowed.
   - *Gate*: HALT. Present `lld-backend.md`. Use `AskUserQuestion` tool for approval.

7. **Stage 5b — Low-Level Design: Frontend** (`lld-frontend.md`)
   - Runs only if `scope` = `frontend` or `fullstack`.
   - *Skill*: `frontend-lld-designer` only.
   - *STRICT MANDATORY DIRECTIVE*: Must detail 100% of component specs, state management, TypeScript models, and API contracts for the frontend. No placeholder schemas or "TBD" parameters allowed.
   - *Gate*: HALT. Present `lld-frontend.md`. Use `AskUserQuestion` tool for approval.

8. **Stage 5c — LLD Consistency Pass** (`lld.md`)
   - Runs only if `scope` = `fullstack`, after both 5a and 5b are individually APPROVED.
   - No locked skill runs here — this is the orchestrator cross-checking that the backend LLD's API specs and the frontend LLD's API Contracts section actually agree in shape. This is not a regeneration of either document and must not be treated as an excuse to shorten either one.
   - *Gate*: HALT. Present the consistency findings (agreements/discrepancies) as `lld.md`. Use `AskUserQuestion` tool for approval.

9. **Stage 6 — LLD Review** (`lld-review.md`)
   - *Skills*: `frontend-lld-review`, `lld-reviewer` only — run only the side(s) matching scope. Backend-only → `lld-reviewer` alone. Frontend-only → `frontend-lld-review` alone. Fullstack → both.
   - *STRICT MANDATORY DIRECTIVE*: Perform line-by-line verification of API definitions, state mutations, and data integrity. Every missing field or state edge case must be flagged. The reviewer must check the LLD doesn't drift from what the approved HLD specified.
   - *Gate*: HALT. Present `lld-review.md`. Use `AskUserQuestion` tool for approval.

10. **Stage 7 — Planning** (`planning.md`, `tasks.json`)
    - *Skill*: `edited-plan-skill` only.
    - *STRICT MANDATORY DIRECTIVE*: Construct complete, atomic, granular implementation tasks with clear dependencies and file targets. Broad or vague umbrella tasks are forbidden. The skill computes execution order deterministically — the Stage 8 coding agent never decides implementation order, dependency resolution, staging, or parallelisation for itself.
    - *Required output sections* (the skill declares an output missing any of these to be INVALID, and `pre-tool.js` enforces the mandatory ones): Task Breakdown · Dependency Matrix · Execution Stages · Critical Path · Optimized Execution Plan · Layered Mermaid DAG · Dependency Graph · Visual Execution Flow · **Structured Architecture Execution Graph (Spark ASCII DAG)** · **Coding Agent Execution Rules** (must be the final section, all 11 rules intact).
    - *`tasks.json` is orchestrator-derived*: `edited-plan-skill` produces the plan document only — it never writes `tasks.json`. The orchestrator serialises Section 1 (Task Breakdown) into `tasks.json`, one entry per task carrying Task ID, name, purpose, input dependencies, output produced, files/modules affected, and module ownership. This is a transcription of the approved plan, not a re-derivation: never invent, merge, split or reorder tasks while serialising. Stage 9 checks every entry in `tasks.json` has corresponding code, so the two must stay in exact correspondence.
    - *Architecture is fixed here, not decided here*: the plan defines implementation order only. It never authorises architectural change. Module boundaries come from the approved LLD and are immutable at this stage.
    - *Gate*: HALT. Present `planning.md` and `tasks.json`. Use `AskUserQuestion` tool for approval.

11. **Stage 8 — Implementation** (Source Code)
    - *Skill*: `trading-platform-coding` only.
    - *STRICT MANDATORY EXECUTION DIRECTIVES*:
      1. **No Short-cuts or Speed Rushing**: The agent MUST thoroughly inspect `lld.md` (or `lld-backend.md` / `lld-frontend.md`, whichever exist per scope), `hld.md`, and `tech-stack.md` before writing a single line of code. Rushing through implementation to trigger approval gates is strictly forbidden.
      2. **Complete & Rigorous Production Code First**: The agent MUST write 100% of all controllers, services, repositories, models, DTOs, configurations, and framework annotations (`@RestController`, `@RequestMapping`, `@PostMapping`, etc.) up-front. Partial, sample, or unannotated code scaffolds are strictly prohibited.
      3. **Quality Over Velocity**: The goal is to deliver complete, production-ready, fully functional software. Speed or pipeline progression is NEVER a justification for incomplete code.
    - *Gate*: HALT. Verify that every task in `tasks.json` has its full, annotated source files written. Present a comprehensive summary of all created files with clickable file links. Use `AskUserQuestion` tool for approval.

12. **Stage 9 — Code & Architecture Review** (`review.md`)
    - *Skill*: `code-reviewer` only.
    - *STRICT MANDATORY DIRECTIVE*: Thoroughly inspect all written source files for bug risks, memory leaks, missing error handling, and style violations. Must reject code containing stubs or placeholders. The reviewer must check implementation actually matches the LLD's API signatures/schemas and that every task in `tasks.json` has corresponding code.
    - *Gate*: HALT. Before presenting `review.md` for approval, scan `traceability.md` for any Requirement ID that is in-scope (per the declared scope field) but has an empty HLD, LLD, or Code Coverage column. If any gap is found, HALT and present the specific gap(s) via `AskUserQuestion` instead of presenting `review.md` — do not proceed to the standard Stage 9 approval gate until this scan passes clean. If changes are requested, route back to Stage 8. Use `AskUserQuestion` tool for approval.

13. **Stage 10 — QA Testing & Browser Validation** (`test-report.md`, `browser-report.md`)
    - *Skill*: `full-stack-test-suite` only.
    - *STRICT MANDATORY DIRECTIVE*: Execute comprehensive automated tests and browser checks. Fake test reports or skipped edge-case validations are strictly prohibited. The QA skill must test against acceptance criteria across the full PRD set (`docs/specs/[NNN]-[name]/product-requirements.md` + all feature files in `parts:`), not just general functionality.
    - *Gate*: HALT. Use `AskUserQuestion` tool for approval.

14. **Stage 11 — Security Review** (`security-review.md`) — **FINAL GATE**
    - *Skill*: `security-review` only.
    - This is the last stage in the pipeline. Nothing ships past it: an approved security review is the pipeline's terminal condition, and no stage runs afterwards.
    - Runs only after QA is APPROVED, so the review judges the code in the state QA actually exercised — including any fix QA forced. A security review run before QA can be invalidated by the very next test-driven change; running it last removes that window.
    - *STRICT MANDATORY DIRECTIVE*: Conduct a dedicated, code-anchored security review of the implemented source: server-side authorization at object, property and function level; input validation at trust boundaries; secrets/PII exposure in logs, errors and responses; dependency risk; language-specific hazards (Rust `unsafe`, concurrency, FFI, cancellation safety where in scope); and a systematic OWASP API Security Top 10 walk of every REST endpoint against its OpenAPI contract — including the "boring" CRUD endpoints. Every finding must be severity-graded (CRITICAL/HIGH/MEDIUM/LOW) and anchored to `file:line` or a spec path. Static review only — this stage never probes a running system. A CRITICAL or HIGH finding forces `CHANGES_REQUESTED` and routes back to Stage 8; softening a severity to keep the pipeline moving is a workflow violation.
    - *Re-entry rule*: when a CRITICAL/HIGH finding sends work back to Stage 8, the fix invalidates QA as well. Stage 10 must re-run and be re-approved before this stage is re-entered — a security fix that never went back through the tests is exactly the change most likely to break behavior silently.
    - *Distinct from Stage 9*: Stage 9 judges correctness, completeness and architecture conformance; this stage judges only whether the code is safe to expose. It must not re-litigate style or LLD-conformance findings unless they are also security defects.
    - *Gate*: HALT. Present `security-review.md`. Use `AskUserQuestion` tool for approval.
    - *Named approval on open findings*: if the report carries any open finding and the user still chooses APPROVE, a second `AskUserQuestion` MUST collect the approver's name (offer `git config user.name` as the recommended option, "Other" for any other name — never auto-filled). The skill then appends an `## Approval Record` section to `security-review.md` (approver, date, verdict, accepted findings, stated basis) and one appended note-row to `traceability.md`, so the acceptance is a named, durable record rather than an anonymous click. This is the pipeline's final sign-off, so the record is the last thing written to the artifact set. A clean zero-finding APPROVED report needs no name prompt.

---

## Dependency Update Note

If upstream artifacts change after downstream stages were approved, all downstream stages are immediately marked UNVERIFIED and must be re-approved through the UI Approval Protocol before continuing. For fullstack features, a change to 5a (backend LLD) also marks the 5c Consistency Pass and Stage 6 review UNVERIFIED, even if 5b (frontend LLD) itself did not change. If `docs/specs/[NNN]-[name]/product-requirements.md` (or any of its feature files listed in `parts:`) changes, `traceability.md`'s requirement rows must be reconciled (new/changed/removed requirement IDs), not just downstream artifacts marked stale.

---

## Claude Code Hook Guards

This workflow is protected by deterministic Claude Code hooks, registered in `.claude/settings.json` and implemented in `hooks/`. They enforce workflow rules at runtime — the agent **cannot** violate stage ordering, artifact integrity, depth requirements, or verdict gates even if the LLM drifts.

| Hook | Matcher | Script | Purpose |
|---|---|---|---|
| **PreToolUse** | `Write\|Edit\|MultiEdit\|NotebookEdit` | `hooks/pre-tool.js` | Denies invalid writes before execution: JSON/schema validity, artifact ownership, **structural depth**, **placeholder bans**, **verdict gates** |
| **PostToolUse** | `Write\|Edit\|MultiEdit\|NotebookEdit` | `hooks/post-tool.js` | Records versions/checksums, cascades staleness (scope-aware), extracts declared `scope` and verdicts, reports owed traceability cells |
| **Stop** | — | `hooks/stop.js` | Blocks premature termination: missing in-scope artifacts, incomplete PRD `parts:`, stale artifacts, unapproved stage, Stage 9 traceability gaps |

Verify the guards at any time with `node hooks/test/run-tests.js` — 51 assertions run each hook as a real child process against synthetic Claude Code payloads.

### Enforcement contract

**Depth and placeholders.** `hooks/utils/artifact-schema.js` defines, per artifact: a minimum line floor, mandatory sections, whether a diagram is required, and whether placeholders are banned. A write that falls short is **denied**, not warned. Design artifacts (`hld-*`, `lld-*`, `tech-stack`, `planning`) additionally reject `TBD`, `<placeholder>`, `[fill in]`, `Lorem ipsum` and elided-implementation markers, implementing the ZERO SHORTCUTS policy mechanically.

**Verdicts must be machine-readable.** Every review artifact must contain a line of the form:

```
**Verdict:** APPROVED
```

Permitted values: `APPROVED`, `APPROVED_WITH_CONDITIONS`, `CHANGES_REQUESTED`. A review with no verdict, or a non-canonical one (e.g. `Ready with Conditions`), is **denied**. `CHANGES_REQUESTED` is hard-blocking per Rule #6 — the gated downstream artifact cannot be written until the review is re-run.

**Scope awareness.** A skipped sub-stage (e.g. `5b` on a backend-only run) is never flagged as missing or stale, and `stop.js` never blocks waiting for an artifact that scope says should not exist.

**Split PRDs.** Hooks parse the index frontmatter `parts:` array and independently verify every listed part file exists and is non-empty; `stop.js` blocks termination and `post-tool.js` flags a completeness error otherwise.

**Traceability.** `post-tool.js` reports which coverage cells an artifact now owes but **deliberately does not auto-fill them** — a hook cannot know which requirement maps to which section, and a fabricated link would defeat the Stage 9 gate rather than satisfy it. The gate itself is real: `stop.js` blocks the Stage 9 → Stage 10 (QA) handoff while any in-scope requirement has an empty HLD/LLD/Code cell.

**Fail-open policy.** Any internal hook error allows the operation and logs loudly. A broken guard must never brick a teammate's session.

---

## Artifact Versioning

Every artifact automatically maintains: `version`, `createdAt`, `updatedAt`, `stage`, `status`, `checksum` (SHA-256), `lastModifiedBySkill`, `approvalStatus`. This metadata is stored in `.ai/state/workflow-state.json`. This includes a `scope` field at the top level of the state file, a `subStage` field per artifact where applicable (e.g. `"5a"`, `"5b"`, `"5c"`), and a `parts` list when a PRD is split.

---

## Dependency Cascade

If an upstream artifact changes (e.g., `docs/specs/[NNN]-[name]/product-requirements.md` or any of its feature files in `parts:` is regenerated), all downstream artifacts (`prd-review.md` → `hld.md` → … → `test-report.md`) are automatically marked STALE. Stale artifacts are never deleted — they must be regenerated and re-approved. If the PRD index is regenerated and its `scope` field changes, this cascade also applies retroactively to whichever sub-stages (`5a`/`5b`) newly become in-scope or newly become out-of-scope — an out-of-scope sub-stage's prior artifact (if one exists from before the scope change) is marked STALE, not deleted, and must not be silently reused if scope later reverts. `traceability.md` updates are cumulative and never deleted, consistent with the existing "stale artifacts are never deleted" rule already in that section.

---

## Structured Logging

All hook decisions are logged to `hooks/logs/hook-events.jsonl` with: `timestamp`, `hook`, `stage`, `skill`, `artifact`, `action`, `decision`, `reason`, `duration`. Log entries include a `subStage` field (`null` for non-split stages) and a `scope` field.

The log is gitignored — it is per-developer and regenerated on every run. Inspect the recent decision trail with `/workflow-status`, or directly:

```sh
tail -n 20 hooks/logs/hook-events.jsonl
```

These hooks enforce — not replace — the workflow rules above. See `hooks/` for implementation.
