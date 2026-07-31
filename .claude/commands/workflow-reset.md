---
description: Reset the pipeline to Stage 1, archiving (never deleting) existing artifacts
---

# Reset the workflow to Stage 1

Current state:

!`cat .ai/state/workflow-state.json 2>/dev/null || echo "no state file"`

Existing artifacts:

!`find .ai/artifacts docs/specs -type f -name '*.md' 2>/dev/null | sort || echo "none"`

---

## Your instructions

1. **Confirm first.** Use `AskUserQuestion` to confirm the reset before touching
   anything. Show exactly which artifacts will be archived.

2. **Archive, never delete.** Move existing artifacts to
   `.ai/examples/archived-<short-slug>/`, preserving their relative layout. This
   repo's standing rule is that artifacts are never destroyed — stale ones are
   superseded, not removed.

3. **Reset state** by copying `.ai/state/workflow-state.template.json` over
   `.ai/state/workflow-state.json`.

4. **Confirm** the reset: report the archive location and the fresh state.

Do not begin Stage 1 as part of this command — the user runs `/prd-to-prod` when
they are ready.
