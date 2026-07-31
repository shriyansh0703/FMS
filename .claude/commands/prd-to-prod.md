---
description: Start or resume the locked 10-stage PRD-to-Production pipeline
argument-hint: <feature description, or blank to resume>
---

# Run the PRD→Prod pipeline

Current workflow state:

!`cat .ai/state/workflow-state.json`

Feature request from the user: **$ARGUMENTS**

---

## Your instructions

1. **Read `.ai/workflows/prd-to-prod.md` in full** before doing anything else. It
   is the authoritative spec and it overrides your defaults.

2. **Determine the entry point from the state above:**
   - `workflowStatus: "not_started"` (or no `$ARGUMENTS` and no state) → begin at **Stage 1**.
   - Otherwise → resume at the recorded `currentStage`. If `waitingForApproval` is
     set, do **not** redo the work — present that artifact and re-open its gate.
   - Never start mid-pipeline unless the user explicitly names a stage this session.

3. **Run the stage's locked skill and nothing else.** The skill map is in
   `CLAUDE.md` and in the workflow doc. If the locked skill cannot cover part of
   the task, HALT and report — do not substitute another skill.

4. **Stage 1 must resolve `scope`** (`backend` | `frontend` | `fullstack`) by
   asking the user. It is a hard switch gating stages 3 and 5, and it must appear
   as a `scope:` field in the PRD. Ask clarifying questions — a generic,
   surface-level PRD is a workflow violation.

5. **End every stage at its gate.** Present the artifact, then call
   `AskUserQuestion` with APPROVE / ITERATE / REJECT / JUMP. Wait for the answer.
   Do not proceed on your own judgement.

6. **Update `.ai/state/workflow-state.json`** as you go: `currentStage`,
   `currentSkill`, `scope`, `approvedStages`, `waitingForApproval`.

7. **Expect the guards to push back.** `hooks/pre-tool.js` will deny writes that
   are too shallow, contain placeholders, miss required sections, or violate the
   verdict gate. That feedback is the quality bar — satisfy it, never route around it.

Begin.
