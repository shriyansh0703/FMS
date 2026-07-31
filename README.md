# Strict PRD → Production Pipeline

A locked, gate-enforced SDLC pipeline for **Claude Code**. Clone it, open Claude
Code, type `/prd-to-prod <your feature>`, and every feature is driven from
requirements to tested production code through 10 mandatory stages — each halting
for your explicit approval, each artifact validated by deterministic hooks the
model cannot talk its way past.

## Quick start

```sh
git clone -b claude-code https://github.com/Thinq-Money/prd-to-prod.git
cd prd-to-prod
claude                      # open Claude Code in the repo root
```

> **Branch matters.** `claude-code` is the Claude Code build. `main` is the
> original Google Antigravity build, where none of these guards execute under
> Claude Code. Make sure you are on `claude-code`.

Then, in Claude Code:

```
/prd-to-prod add document sharing with expiring links
```

That's the whole setup. **No `npm install`, no build step, no dependencies** —
the hooks and dashboard use only Node built-ins.

### Prerequisite: Node.js

**macOS does not ship with Node.** If it is missing, every hook fails to launch
and the pipeline runs with **no enforcement at all** while appearing to work
normally — the worst possible failure mode for this repo.

```sh
node --version      # if this errors:
brew install node
```

Minimum Node 12 (the guards use nothing newer); Node 18+ LTS recommended.

A `SessionStart` hook checks this for you on every session — written in POSIX
`sh` precisely so it still runs when Node is absent. If Node is missing you will
see a loud `*** WORKFLOW GUARDS ARE NOT RUNNING ***` banner rather than silent
non-enforcement. It also tells you where the pipeline currently stands, so every
session opens oriented.

**Requirements:** macOS, Node.js, and Claude Code. Nothing else.

### Verify the guards are live (recommended first step)

```sh
node hooks/test/run-tests.js
```

Expected: `Result: 44 passed, 0 failed`. Each hook runs as a real child process
against synthetic Claude Code payloads. If this passes, enforcement is genuinely
active — not merely documented. Takes about 5 seconds and leaves no trace.

---

## Your first run — what to expect

The pipeline is deliberately slow and interrogative at the start. That is the
design, not a malfunction.

**1. It will ask you questions before writing anything.**
Stage 1 is forbidden from producing a generic PRD. `prd-generator` will ask about
personas, edge cases, failure modes, acceptance criteria — and crucially about
**scope**: `backend`, `frontend`, or `fullstack`. That answer is a hard switch
that decides which sub-stages exist for the rest of the run. Getting it wrong
wastes an entire sub-stage, so answer deliberately.

**2. It will stop at every stage and wait for you.**
After each artifact, you get an interactive prompt:

| Choice | Meaning |
|---|---|
| **APPROVE** | Stage complete, proceed |
| **ITERATE** | Re-run this stage's skill with your stated changes |
| **REJECT** | Halt or roll back |
| **JUMP** | Go to a stage you name explicitly |
| *Other →* CANCEL | Abort the workflow |

Nothing advances without your click. An artifact already sitting on disk is
**never** treated as approval.

**3. It will sometimes refuse to write.**
If an artifact is too thin, missing a required section, or contains `TBD`, the
hook denies the write and tells the model exactly why. You will see it correct
itself and retry. This is the quality bar doing its job — do not disable it.

**4. It will refuse to stop early.**
If you try to end the turn mid-stage, the Stop hook blocks and states what is
outstanding. It always allows stopping when it is *your* turn to approve.

### Resuming later

State lives in `.ai/state/workflow-state.json`. Just reopen Claude Code and run
`/prd-to-prod` with no arguments — it reads the state and resumes at the recorded
stage. Check where you are at any time with `/workflow-status`.

### Starting over

```
/workflow-reset
```

Archives existing artifacts to `.ai/examples/` and resets to Stage 1. Nothing is
ever deleted.

## Commands

| Command | What it does |
|---|---|
| `/prd-to-prod <feature>` | Start or resume the pipeline |
| `/workflow-status` | Current stage, pending gate, blockers |
| `/workflow-reset` | Reset to Stage 1 (archives, never deletes) |
| `node .ai/dashboard/server.js` | Live browser dashboard |
| `node hooks/test/run-tests.js` | Prove the guards fire |

## The pipeline

Scope (`backend` / `frontend` / `fullstack`) is resolved at Stage 1 and acts as a
hard switch — only the matching sub-stages run.

| Stage | Skill | Produces |
|---|---|---|
| 1 Requirement Analysis | `prd-generator` | `product-requirements.md`, `traceability.md` |
| 2 PRD Review | `prd-reviewing` | `prd-review.md` |
| 3a HLD Backend | `backend-hld-architect` | `hld-backend.md`, `tech-stack.md` |
| 3b HLD Frontend | `frontend-hld-designer` | `hld-frontend.md`, `tech-stack.md` |
| 4 HLD Review | `hld-reviewer` | `hld-review.md` |
| 5a LLD Backend | `backend-lld-architect` | `lld-backend.md` |
| 5b LLD Frontend | `frontend-lld-designer` | `lld-frontend.md` |
| 5c LLD Consistency | *(orchestrator)* | `lld.md` |
| 6 LLD Review | `lld-reviewer` / `frontend-lld-review` | `lld-review.md` |
| 7 Planning | `edited-plan-skill` | `planning.md`, `tasks.json` |
| 8 Implementation | `trading-platform-coding` | production source code |
| 9 Code & Arch Review | `code-reviewer` | `review.md` |
| 10 QA & Browser | `full-stack-test-suite` | `test-report.md`, `browser-report.md` |

Full spec: **`.ai/workflows/prd-to-prod.md`**.

## What makes this different from "just prompting well"

The rules are not suggestions in a prompt the model may drift away from. Three
Claude Code hooks enforce them at runtime.

### `hooks/pre-tool.js` — denies bad writes before they land

- **Depth floors.** An HLD under 150 lines is rejected; an LLD under 200. The
  floors sit far below what a serious artifact needs (see `.ai/examples/` — the
  reference PRD is 686 lines against a floor of 120). They catch laziness; they
  do not define the target.
- **Mandatory sections.** No sequence diagrams in an HLD → denied. No error
  handling section in an LLD → denied.
- **Placeholder ban.** `TBD`, `<placeholder>`, `[fill in]`, `// ... rest` in a
  design document → denied.
- **Ownership.** Stage 1 cannot write the Stage 5 artifact.
- **Verdict gate.** A downstream artifact cannot be written while its gating
  review says `CHANGES_REQUESTED`.

### `hooks/post-tool.js` — tracks integrity

SHA-256 checksums, version increments, scope-aware staleness cascade, split-PRD
completeness, verdict extraction, and a running report of which traceability
cells are still owed.

### `hooks/stop.js` — prevents finishing early

Blocks the turn from ending while in-scope artifacts are missing, PRD parts are
incomplete, artifacts are stale, the current stage is unapproved, or — at Stage 9
— any in-scope requirement still has an empty coverage cell.

All three **fail open**: a bug in a guard logs loudly and allows the operation. A
broken hook must never brick a teammate's session.

## Reviewer verdicts

Every review artifact must carry a machine-readable verdict line:

```
**Verdict:** APPROVED
```

Valid values: `APPROVED`, `APPROVED_WITH_CONDITIONS`, `CHANGES_REQUESTED`.
Anything else is rejected at write time — this is what makes the hard-blocking
rule enforceable rather than aspirational.

## Layout

```
CLAUDE.md                      # auto-loaded; makes the workflow the default behavior
.claude/
  settings.json                # hook registration
  commands/                    # /prd-to-prod, /workflow-status, /workflow-reset
  skills/                      # the 13 LOCKED skills — the only discoverable ones
.ai/
  workflows/prd-to-prod.md     # authoritative spec
  artifacts/                   # generated stage artifacts
  state/workflow-state.json    # pipeline state (gitignored, per-developer)
  examples/                    # archived reference run — read this for the quality bar
  dashboard/                   # zero-dependency live dashboard
  skills/                      # DISABLED skills, retained for reference only
  stages/                      # legacy stage skills, retained for reference only
hooks/
  pre-tool.js  post-tool.js  stop.js
  utils/                       # config, stage-keys, hook-io, artifact-schema, traceability
  validators/                  # ownership, verdict, schema, JSON, markdown, transitions
  test/run-tests.js            # 44-assertion guard verification
docs/specs/                    # PRDs live here
```

## Team notes

- **`workflow-state.json` is gitignored.** Each developer runs their own pipeline;
  a tracked state file would conflict on every stage transition.
  `workflow-state.template.json` is committed, and the state file is recreated
  from defaults on first read.
- **Artifacts are never deleted.** Stale ones are superseded and re-approved;
  `/workflow-reset` archives rather than removes.
- **Skill files are immutable.** The locked skills were relocated byte-for-byte
  during the Claude Code port. Runtime and path concerns belong in the hooks —
  `hooks/utils/config.js` recognises artifacts under `.ai/artifacts/`,
  `.ai/stages/**` and `docs/specs/**` — never in a skill rewrite.
- **Tuning strictness:** depth floors and required sections live in
  `hooks/utils/artifact-schema.js`. Raise them if shallow artifacts still get
  through; lower them if a genuinely small feature is being blocked.

## Porting history

Originally built for the Google Antigravity runtime, where none of the guards
actually executed under Claude Code — wrong hook directory, wrong tool matchers,
wrong I/O schema, and skills the runtime could not discover.
`.ai/CLAUDE-CODE-PORT-CHECKLIST.md` documents the full audit and every change,
including several bugs that had silently disabled entire checks.

The Antigravity config (`.agents/hooks.json`) is retained on disk but inert.
