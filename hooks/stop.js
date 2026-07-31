'use strict';

/**
 * stop.js — Claude Code Stop hook.
 *
 * Prevents the agent from ending its turn while the workflow is in an
 * incomplete state: missing in-scope artifacts, stale artifacts, incomplete
 * split PRDs, or (at Stage 9) traceability gaps.
 *
 * Registered in .claude/settings.json under "Stop".
 *
 * Input:  Claude Code Stop payload on stdin (includes `stop_hook_active`)
 * Output: {"decision":"block","reason":"..."} to prevent stopping; {} to allow.
 *
 * The `reason` is fed back to the model as its next instruction, so it is
 * written as a directive rather than a description.
 */

const fs = require('fs');
const path = require('path');
const {
  STAGE_ARTIFACTS,
  ARTIFACT_DIR,
  SPECS_DIR,
  STAGES_DIR,
  findArtifact,
  getInScopeArtifacts,
} = require('./utils/config');
const { readState } = require('./utils/state-manager');
const { log } = require('./utils/logger');
const { validatePrdParts } = require('./utils/prd-parts');
const { canonicalKey, stageLabel, listIncludesStage, inScopeStages } = require('./utils/stage-keys');
const traceability = require('./utils/traceability');
const io = require('./utils/hook-io');

/**
 * Locate every PRD index file across all recognised roots.
 * @returns {string[]}
 */
function findPrdIndexFiles() {
  const files = [];
  const push = (p) => { if (p && fs.existsSync(p) && !files.includes(p)) files.push(p); };

  push(path.join(ARTIFACT_DIR, 'requirements.md'));
  push(path.join(STAGES_DIR, 'requirement', 'requirements.md'));

  if (fs.existsSync(SPECS_DIR)) {
    try {
      for (const entry of fs.readdirSync(SPECS_DIR, { withFileTypes: true })) {
        if (entry.isDirectory()) {
          push(path.join(SPECS_DIR, entry.name, 'product-requirements.md'));
        }
      }
    } catch { /* ignore */ }
  }

  return files;
}

async function main() {
  const startTime = Date.now();

  const { ok, input, error } = await io.readInput();
  if (!ok) {
    log({
      hook: 'stop',
      action: 'parse_error',
      decision: 'stop',
      reason: `Failed to parse stdin: ${error}`,
    });
    io.respondStop(false);
    return;
  }

  // -----------------------------------------------------------------------
  // Loop guard — MUST come first.
  //
  // Claude Code sets stop_hook_active when the agent is already continuing
  // because of a previous Stop-hook block. Without this, the "stage not yet
  // approved" check below can trap a session in an unbreakable loop.
  // -----------------------------------------------------------------------
  if (input.stop_hook_active === true) {
    log({
      hook: 'stop',
      action: 'allow_loop_guard',
      decision: 'stop',
      reason: 'stop_hook_active is true — allowing stop to prevent a loop',
      duration: Date.now() - startTime,
    });
    io.respondStop(false);
    return;
  }

  // -----------------------------------------------------------------------
  // Read state
  // -----------------------------------------------------------------------
  let state;
  try {
    state = readState();
  } catch (err) {
    log({
      hook: 'stop',
      action: 'state_read_error',
      decision: 'stop',
      reason: `Cannot read workflow state: ${err.message}`,
      duration: Date.now() - startTime,
    });
    io.respondStop(false);
    return;
  }

  const scope = state.scope || null;
  const currentStage = state.currentStage;
  const stageKey = canonicalKey(currentStage, scope);
  const issues = [];

  // -----------------------------------------------------------------------
  // Workflow not started — nothing to enforce
  // -----------------------------------------------------------------------
  if (!currentStage || state.workflowStatus === 'not_started') {
    log({
      hook: 'stop',
      action: 'allow_idle_stop',
      decision: 'stop',
      reason: 'Workflow has not started',
      duration: Date.now() - startTime,
    });
    io.respondStop(false);
    return;
  }

  // -----------------------------------------------------------------------
  // Waiting on a human approval gate — stopping is CORRECT here.
  // The agent presented the gate; the user must now answer.
  // -----------------------------------------------------------------------
  if (state.waitingForApproval) {
    log({
      hook: 'stop',
      stage: stageKey,
      scope,
      action: 'allow_approval_wait',
      decision: 'stop',
      reason: `Waiting for approval on: ${state.waitingForApproval}`,
      duration: Date.now() - startTime,
    });
    io.respondStop(false);
    return;
  }

  // -----------------------------------------------------------------------
  // Check 1: required artifacts for the current stage exist
  //
  // This is the check that never ran before: STAGE_ARTIFACTS is keyed by name
  // ('hld_review') but currentStage held the number 4, so the lookup returned
  // undefined and `|| []` swallowed it.
  // -----------------------------------------------------------------------
  const requiredArtifacts = (stageKey && STAGE_ARTIFACTS[stageKey]) || [];

  for (const artifact of requiredArtifacts) {
    if (artifact === 'requirements.md') {
      if (findPrdIndexFiles().length === 0) {
        issues.push(
          'The PRD index (requirements.md / product-requirements.md) does not exist. ' +
          'Produce it with the prd-generator-split skill before ending the turn.'
        );
      }
    } else if (!findArtifact(artifact)) {
      issues.push(
        `Required artifact "${artifact}" for ${stageLabel(stageKey)} does not exist ` +
        `in any recognised artifact directory. Produce it before ending the turn.`
      );
    }
  }

  // -----------------------------------------------------------------------
  // Check 2: split PRD completeness
  // -----------------------------------------------------------------------
  for (const indexPath of findPrdIndexFiles()) {
    const partsResult = validatePrdParts(indexPath);
    if (!partsResult.valid) {
      issues.push(...partsResult.errors.map(
        (e) => `${e} — write the missing part file before ending the turn.`
      ));
    }
  }

  // -----------------------------------------------------------------------
  // Check 3: no stale artifacts, scope-aware
  //
  // A sub-stage that scope says should never run is not a defect.
  // -----------------------------------------------------------------------
  if (Array.isArray(state.staleArtifacts) && state.staleArtifacts.length > 0) {
    const inScope = new Set(getInScopeArtifacts(scope || 'fullstack'));
    const relevantStale = state.staleArtifacts.filter((a) => inScope.has(a));
    if (relevantStale.length > 0) {
      issues.push(
        `Stale artifacts must be regenerated and re-approved: ${relevantStale.join(', ')}.`
      );
    }
  }

  // -----------------------------------------------------------------------
  // Check 4: state consistency
  // -----------------------------------------------------------------------
  if (!state.workflowStatus) {
    issues.push('workflowStatus is missing from .ai/state/workflow-state.json.');
  }

  if (
    state.workflowStatus === 'in_progress' &&
    stageKey &&
    !listIncludesStage(state.approvedStages, stageKey, scope)
  ) {
    issues.push(
      `${stageLabel(stageKey)} is in progress but has not been approved. Present its ` +
      `artifact and call AskUserQuestion with APPROVE / REJECT / ITERATE / JUMP.`
    );
  }

  // -----------------------------------------------------------------------
  // Check 5: Stage 9 traceability gap gate (prd-to-prod.md §Traceability)
  //
  // Before Stage 9 may hand off to Stage 10, every in-scope requirement must
  // have HLD, LLD and Code coverage recorded.
  // -----------------------------------------------------------------------
  if (stageKey === 'review') {
    const tracePath = findArtifact('traceability.md');
    if (!tracePath) {
      issues.push(
        'traceability.md does not exist. Stage 9 cannot hand off to QA without the ' +
        'coverage matrix.'
      );
    } else {
      const gapResult = traceability.findGaps(
        tracePath,
        scope || 'fullstack',
        ['hld coverage', 'lld coverage', 'code coverage'] // test coverage is Stage 10's job
      );

      if (gapResult.errors.length) {
        issues.push(`traceability.md is malformed: ${gapResult.errors.join('; ')}`);
      }

      if (gapResult.gaps.length) {
        const byReq = {};
        for (const g of gapResult.gaps) {
          (byReq[g.reqId] = byReq[g.reqId] || []).push(g.column);
        }
        const summary = Object.entries(byReq)
          .slice(0, 15)
          .map(([req, cols]) => `${req} (missing: ${cols.join(', ')})`)
          .join('; ');
        const extra = Object.keys(byReq).length > 15
          ? ` …and ${Object.keys(byReq).length - 15} more requirements`
          : '';
        issues.push(
          `Traceability gaps block the Stage 9 → Stage 10 handoff: ${summary}${extra}. ` +
          `Fill every in-scope coverage cell, or HALT and report the gap to the user.`
        );
      }
    }
  }

  // -----------------------------------------------------------------------
  // Decision
  // -----------------------------------------------------------------------
  if (issues.length > 0) {
    const reason =
      `The prd-to-prod workflow is not in a completable state at ` +
      `${stageLabel(stageKey || currentStage)}. Resolve these before finishing:\n` +
      issues.map((i, n) => `  ${n + 1}. ${i}`).join('\n') +
      `\n\nIf a genuine blocker prevents this, HALT and report it to the user via ` +
      `AskUserQuestion rather than ending silently.`;

    log({
      hook: 'stop',
      stage: stageKey,
      scope,
      action: 'block_premature_stop',
      decision: 'block',
      reason: issues.join('; '),
      duration: Date.now() - startTime,
    });
    io.respondStop(true, reason);
  } else {
    log({
      hook: 'stop',
      stage: stageKey,
      scope,
      action: 'allow_stop',
      decision: 'stop',
      reason: 'All completion checks passed',
      duration: Date.now() - startTime,
    });
    io.respondStop(false);
  }
}

main().catch((err) => {
  log({
    hook: 'stop',
    action: 'fatal_error',
    decision: 'stop',
    reason: `${err.message}\n${err.stack || ''}`,
  });
  // Never trap the agent on a guard bug.
  io.respondStop(false);
  process.exit(0);
});
