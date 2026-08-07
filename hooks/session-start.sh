#!/bin/sh
#
# session-start.sh — SessionStart hook. Greeting + preflight + orientation.
#
# Emits TWO channels, because they have different audiences:
#
#   systemMessage  -> shown to the HUMAN in the terminal. The greeting, the
#                     command cheat-sheet, where the pipeline stands, and any
#                     loud warning.
#   additionalContext -> injected into the MODEL's context. Terser, and phrased
#                     as instructions rather than as a welcome banner.
#
# WHY THIS IS SHELL, NOT NODE
# ---------------------------
# The failure this guards against is "Node is not installed". A Node-based check
# could not report that. macOS ships /bin/sh always, so this runs even on a
# machine where every other hook is silently failing to launch.
#
# That silent failure is the dangerous one: without Node, every guard command
# fails to start, the workflow appears to run normally, and NOTHING is being
# enforced. This hook makes that loud.

set -u

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
STATE_FILE="$PROJECT_DIR/.ai/state/workflow-state.json"
SKILL_DIR="$PROJECT_DIR/.claude/skills"

NODE_OK=0
command -v node >/dev/null 2>&1 && NODE_OK=1

SKILL_COUNT=0
[ -d "$SKILL_DIR" ] && SKILL_COUNT=$(find "$SKILL_DIR" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')

# ---------------------------------------------------------------------------
# Hook liveness probe
# ---------------------------------------------------------------------------
# Node being on PATH proves only that the guards can be LAUNCHED, not that they
# RUN. A hook that crashes on load — a syntax error, a missing dependency, or a
# module-system mismatch — exits non-zero having enforced nothing, and Claude
# Code treats the failure as non-blocking. The pipeline then reports every stage
# as passing while no guard is executing.
#
# That is not hypothetical. Adding "type": "module" to the repository's
# package.json silently killed all three hooks, which are CommonJS: every write
# was permitted, every gate opened, for an entire session. The artifacts happened
# to comply because they were authored to the standard, not because anything
# checked them.
#
# So this probe actually EXECUTES each hook with an empty payload and looks for
# a load-time crash. It costs three process spawns at session start and converts
# the worst failure mode the workflow has — silent non-enforcement — into a
# banner that cannot be missed.
DEAD_HOOKS=""
DOCTOR_FAILS=0
DOCTOR_SUMMARY=""
if [ "$NODE_OK" -eq 1 ] && [ -f "$PROJECT_DIR/hooks/doctor.cjs" ]; then
  DOCTOR_JSON=$(node "$PROJECT_DIR/hooks/doctor.cjs" --json 2>/dev/null)
  if [ -n "$DOCTOR_JSON" ]; then
    DOCTOR_FAILS=$(printf '%s' "$DOCTOR_JSON" | node -e '
      let s="";process.stdin.on("data",c=>s+=c).on("end",()=>{
        try{process.stdout.write(String(JSON.parse(s).failCount||0));}catch{process.stdout.write("0");}
      });' 2>/dev/null || echo 0)
    DOCTOR_SUMMARY=$(printf '%s' "$DOCTOR_JSON" | node -e '
      let s="";process.stdin.on("data",c=>s+=c).on("end",()=>{
        try{
          const d=JSON.parse(s);
          const bad=d.results.filter(r=>r.level!=="ok");
          process.stdout.write(bad.map(r=>"  ["+r.level.toUpperCase()+"] "+r.message+(r.fix?"\n         fix: "+r.fix:"")).join("\n"));
        }catch{}
      });' 2>/dev/null)
    [ "$DOCTOR_FAILS" -gt 0 ] 2>/dev/null && DEAD_HOOKS=" see preflight below"
  fi
fi

# ---------------------------------------------------------------------------
# Pipeline position — one line, human phrasing
# ---------------------------------------------------------------------------
STATE_LINE="Pipeline not started."
NEXT_LINE="Run  /prd-to-prod <feature>  to begin."

if [ "$NODE_OK" -eq 1 ] && [ -f "$STATE_FILE" ]; then
  EVAL=$(node -e '
    try {
      const s = require(process.argv[1]);
      if (!s.currentStage || s.workflowStatus === "not_started") {
        console.log("STATE=Pipeline not started.");
        console.log("NEXT=Run  /prd-to-prod <feature>  to begin.");
      } else {
        const scope = s.scope || "unset";
        // Render the human label ("Stage 3 — High-Level Design"), not the
        // internal key ("hld"). Falls back to the raw value if unresolvable.
        let label = String(s.currentStage);
        try {
          const sk = require(process.argv[2]);
          label = sk.stageLabel(s.currentStage, s.scope);
        } catch (e) { }
        let st = label + " · " + (s.currentSkill || "no skill set") + " · scope " + scope + ".";
        const stale = s.staleArtifacts || [];
        if (stale.length) st += " STALE: " + stale.join(", ") + ".";
        console.log("STATE=" + st);
        console.log("NEXT=" + (s.waitingForApproval
          ? "AWAITING YOUR APPROVAL on " + s.waitingForApproval + " - run /prd-to-prod to re-open the gate."
          : "Run  /prd-to-prod  to resume, or  /workflow-status  for detail."));
      }
    } catch (e) { }
  ' "$STATE_FILE" "$PROJECT_DIR/hooks/utils/stage-keys.js" 2>/dev/null)
  case "$EVAL" in
    *STATE=*)
      STATE_LINE=$(printf '%s\n' "$EVAL" | sed -n 's/^STATE=//p')
      NEXT_LINE=$(printf '%s\n' "$EVAL" | sed -n 's/^NEXT=//p')
      ;;
  esac
fi

# ---------------------------------------------------------------------------
# Human-facing greeting
# ---------------------------------------------------------------------------
# Horizontal rules rather than a closed box: the skill count is variable-width,
# so a right-hand border would drift out of alignment.
GREETING="──────────────────────────────────────────────────────────────
  PRD → Production Pipeline
  Locked 11-stage SDLC · ${SKILL_COUNT} skills · guards enforced at write time
──────────────────────────────────────────────────────────────

  /prd-to-prod <feature>   start, or resume where you left off
  /workflow-status         current stage, pending gate, blockers
  /workflow-reset          back to Stage 1 (archives, never deletes)

  node hooks/doctor.cjs             preflight: is anything actually enforcing?
  node hooks/test/run-tests.js     prove the guards fire (51 checks, sandboxed)
  node .ai/dashboard/server.js     live dashboard

  Every stage HALTS for your approval. Artifacts too thin, missing a
  required section, or containing TBD are denied at write time.

  ▸ ${STATE_LINE}
  ▸ ${NEXT_LINE}"

if [ "$NODE_OK" -eq 0 ]; then
  GREETING="*** WORKFLOW GUARDS ARE NOT RUNNING ***

  Node.js was not found on PATH. Every hook launches via 'node', so all of
  them are failing to start. The pipeline will APPEAR to work while
  enforcing nothing.

      brew install node

  Then verify:  node hooks/test/run-tests.js   (expect 51 passed)"
elif [ "$DOCTOR_FAILS" -gt 0 ] 2>/dev/null; then
  GREETING="*** WORKFLOW PREFLIGHT FAILED — ${DOCTOR_FAILS} PROBLEM(S) ***

${DOCTOR_SUMMARY}

  Hook failures are NON-BLOCKING, so nothing else will tell you about this.
  Until it is fixed, a stage that appears to pass is not evidence of anything.

  Full report:  node hooks/doctor.cjs
  Guard proof:  node hooks/test/run-tests.js   (expect 51 passed, sandboxed)"
else
  NODE_MAJOR=$(node -p "process.versions.node.split('.')[0]" 2>/dev/null || echo 99)
  if [ "$NODE_MAJOR" -lt 12 ] 2>/dev/null; then
    GREETING="*** Node $(node --version) is too old for the workflow guards ***
  Minimum is Node 12; Node 18+ LTS recommended.  brew install node

${GREETING}"
  fi
fi

# ---------------------------------------------------------------------------
# Model-facing orientation
# ---------------------------------------------------------------------------
if [ "$NODE_OK" -eq 0 ]; then
  CONTEXT="WORKFLOW GUARDS ARE NOT RUNNING: Node.js is not on PATH, so every hook fails to launch and nothing is enforced. Tell the user to run 'brew install node' before starting any pipeline work."
elif [ "$DOCTOR_FAILS" -gt 0 ] 2>/dev/null; then
  CONTEXT="WORKFLOW PREFLIGHT FAILED with ${DOCTOR_FAILS} problem(s); run 'node hooks/doctor.cjs' for the report. Guards may not be enforcing, and hook failures are non-blocking so nothing else will tell you. Report this to the user before doing any pipeline work, and do not treat a passing stage as evidence until it is fixed."
else
  CONTEXT="${STATE_LINE} ${NEXT_LINE} Read .ai/workflows/prd-to-prod.md before acting on any feature request; it is the sole driver. Do not redo work already recorded as approved in .ai/state/workflow-state.json."
fi

# ---------------------------------------------------------------------------
# Emit
# ---------------------------------------------------------------------------
if [ "$NODE_OK" -eq 1 ]; then
  GREETING="$GREETING" CONTEXT="$CONTEXT" node -e '
    process.stdout.write(JSON.stringify({
      systemMessage: process.env.GREETING,
      hookSpecificOutput: {
        hookEventName: "SessionStart",
        additionalContext: process.env.CONTEXT
      }
    }) + "\n");
  '
else
  # No Node: hand-escape. Only the warning path reaches here.
  esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "%s\\n", $0}'; }
  printf '{"systemMessage":"%s","hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
    "$(esc "$GREETING")" "$(esc "$CONTEXT")"
fi
