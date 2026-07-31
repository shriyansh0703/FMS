#!/bin/sh
#
# session-start.sh — SessionStart hook. Preflight + orientation.
#
# WHY THIS IS SHELL, NOT NODE
# ---------------------------
# The failure this guards against is "Node is not installed". A Node-based
# check could not report that. macOS ships /bin/sh always, so this runs even on
# a machine where every other hook is silently failing to launch.
#
# That silent failure is the dangerous one: without Node, every guard command
# fails to start, the workflow appears to run normally, and NOTHING is being
# enforced. This hook makes that loud.
#
# Output: SessionStart JSON with additionalContext injected into the session.

set -u

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
STATE_FILE="$PROJECT_DIR/.ai/state/workflow-state.json"

MSG=""
add() { MSG="$MSG$1
"; }

# ---------------------------------------------------------------------------
# 1. Is Node available at all?
# ---------------------------------------------------------------------------
if ! command -v node >/dev/null 2>&1; then
  add "*** WORKFLOW GUARDS ARE NOT RUNNING ***"
  add ""
  add "Node.js was not found on PATH. Every hook in .claude/settings.json"
  add "launches via 'node', so all of them are failing to start:"
  add "  - pre-tool.js  : artifact depth, placeholders, ownership, verdict gates"
  add "  - post-tool.js : checksums, versioning, staleness cascade"
  add "  - stop.js      : premature-termination and traceability gates"
  add ""
  add "The prd-to-prod pipeline will APPEAR to work while enforcing nothing."
  add "Tell the user to install Node before continuing:"
  add "    brew install node"
  add "Then verify with: node hooks/test/run-tests.js  (expect 44 passed)"
else
  NODE_MAJOR=$(node -p "process.versions.node.split('.')[0]" 2>/dev/null || echo 0)
  if [ "$NODE_MAJOR" -lt 12 ] 2>/dev/null; then
    add "*** Node $(node --version) is too old for the workflow guards. ***"
    add "Minimum is Node 12; Node 18+ LTS recommended. Run: brew install node"
  fi
fi

# ---------------------------------------------------------------------------
# 2. Where is the pipeline?
# ---------------------------------------------------------------------------
if [ -f "$STATE_FILE" ] && command -v node >/dev/null 2>&1; then
  SUMMARY=$(node -e '
    try {
      const s = require(process.argv[1]);
      if (!s.currentStage || s.workflowStatus === "not_started") {
        console.log("Pipeline: not started. Begin with /prd-to-prod <feature>.");
      } else {
        let line = "Pipeline: stage " + s.currentStage +
                   " (" + (s.currentSkill || "no skill set") + "), scope " + (s.scope || "unset") + ".";
        if (s.waitingForApproval) {
          line += " AWAITING APPROVAL on " + s.waitingForApproval +
                  " - present it and call AskUserQuestion. Do not redo the work.";
        }
        const stale = (s.staleArtifacts || []);
        if (stale.length) line += " STALE: " + stale.join(", ") + ".";
        console.log(line);
      }
    } catch (e) { console.log(""); }
  ' "$STATE_FILE" 2>/dev/null)
  [ -n "$SUMMARY" ] && add "$SUMMARY"
elif [ ! -f "$STATE_FILE" ]; then
  add "Pipeline: not started (no state file yet). Begin with /prd-to-prod <feature>."
fi

# ---------------------------------------------------------------------------
# Emit
# ---------------------------------------------------------------------------
if [ -z "$MSG" ]; then
  printf '{}\n'
  exit 0
fi

# JSON-escape via node when available; fall back to a plain sed escape.
if command -v node >/dev/null 2>&1; then
  printf '%s' "$MSG" | node -e '
    let d = "";
    process.stdin.on("data", c => d += c);
    process.stdin.on("end", () => {
      process.stdout.write(JSON.stringify({
        hookSpecificOutput: {
          hookEventName: "SessionStart",
          additionalContext: d.trim()
        }
      }) + "\n");
    });
  '
else
  ESCAPED=$(printf '%s' "$MSG" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "%s\\n", $0}')
  printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$ESCAPED"
fi
