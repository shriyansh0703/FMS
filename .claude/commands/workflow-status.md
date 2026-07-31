---
description: Show the current pipeline stage, pending gate, and any blockers
---

# Workflow status

State file:

!`cat .ai/state/workflow-state.json 2>/dev/null || echo "NO STATE FILE — workflow has not started"`

Artifacts on disk:

!`find .ai/artifacts docs/specs .ai/stages -type f -name '*.md' -not -name 'SKILL.md' 2>/dev/null | sort || echo "none"`

Recent guard decisions:

!`tail -n 15 hooks/logs/hook-events.jsonl 2>/dev/null || echo "no hook events logged yet"`

---

## Your instructions

Summarise for the user, concisely:

1. **Where the pipeline is** — current stage, its locked skill, declared scope.
2. **What it is waiting on** — the pending approval gate, if any.
3. **Blockers** — stale artifacts, missing in-scope artifacts, `CHANGES_REQUESTED`
   verdicts, or traceability gaps.
4. **The next legitimate action** — the single next step per `.ai/workflows/prd-to-prod.md`.

Report only. Do **not** advance the workflow, produce artifacts, or open a gate
from this command.
