'use strict';

/**
 * pre-tool.js — Claude Code PreToolUse hook.
 *
 * Intercepts every file write before it executes and enforces the workflow's
 * hard rules: artifact ownership, structural depth, placeholder bans, verdict
 * gates, and JSON/schema validity.
 *
 * Registered in .claude/settings.json against matcher "Write|Edit|MultiEdit".
 *
 * Input:  Claude Code PreToolUse payload on stdin
 * Output: { hookSpecificOutput: { permissionDecision: 'allow'|'deny'|'ask', ... } }
 *
 * FAIL-OPEN POLICY: any internal error allows the write. A broken guard must
 * never brick a teammate's session — it logs loudly to hooks/logs instead.
 */

const path = require('path');
const {
  isProtectedPath,
  getArtifactName,
  ARTIFACT_DIR,
  WORKSPACE_ROOT,
  PROJECT_JSON_PATH,
  WORKFLOW_STATE_PATH,
} = require('./utils/config');
const { readState } = require('./utils/state-manager');
const { log } = require('./utils/logger');
const { canonicalKey, stageLabel, subStageOf } = require('./utils/stage-keys');
const { validateArtifact } = require('./utils/artifact-schema');
const io = require('./utils/hook-io');

// Lazy-load validators so a syntax error in a rarely-used one cannot break
// the common path.
function getValidators() {
  return {
    validateJson: require('./validators/json-validator'),
    validateProjectJson: require('./validators/schema-validator').validateProjectJson,
    validateWorkflowState: require('./validators/schema-validator').validateWorkflowState,
    validateStatusTransition: require('./validators/transition-validator'),
    validateArtifactOwnership: require('./validators/artifact-validator').validateArtifactOwnership,
    validateVerdictGate: require('./validators/verdict-validator').validateVerdictGate,
  };
}

async function main() {
  const startTime = Date.now();

  // -----------------------------------------------------------------------
  // Parse the Claude Code payload
  // -----------------------------------------------------------------------
  const { ok, input, error } = await io.readInput();
  if (!ok) {
    log({
      hook: 'preToolUse',
      action: 'parse_error',
      decision: 'allow',
      reason: `Failed to parse stdin: ${error}`,
    });
    io.respondPreToolUse('allow', 'Hook input parse error — allowing by default');
    return;
  }

  const toolName = io.getToolName(input);
  const toolInput = io.getToolInput(input);
  const cwd = input.cwd || WORKSPACE_ROOT;

  // -----------------------------------------------------------------------
  // Only file writes are validated
  // -----------------------------------------------------------------------
  if (!io.isFileWriteTool(toolName)) {
    io.respondPreToolUse('allow');
    return;
  }

  const targetFile = io.getTargetFile(toolName, toolInput);
  if (!targetFile) {
    io.respondPreToolUse('allow');
    return;
  }

  const absolutePath = io.toAbsolutePath(targetFile, WORKSPACE_ROOT, cwd);

  // -----------------------------------------------------------------------
  // Unprotected paths (source code, tests, configs) pass straight through.
  // Stage 8 writes hundreds of source files — they must not pay this cost.
  // -----------------------------------------------------------------------
  if (!isProtectedPath(absolutePath)) {
    io.respondPreToolUse('allow');
    return;
  }

  const state = readState();
  const scope = state.scope || null;
  const currentStage = state.currentStage;
  const errors = [];

  // Reconstruct the full post-write file content. For Edit this reads the file
  // and applies the replacement in memory, so whole-document validators are
  // never handed a fragment.
  const resolved = io.resolveFullContent(toolName, toolInput, absolutePath);

  // -----------------------------------------------------------------------
  // Check 1: JSON validity for any protected .json file
  // -----------------------------------------------------------------------
  const validators = getValidators();

  if (absolutePath.endsWith('.json') && resolved.complete && resolved.content !== null) {
    const jsonResult = validators.validateJson(resolved.content);
    if (!jsonResult.valid) {
      errors.push(...jsonResult.errors.map((e) => `JSON validation: ${e}`));
    }
  }

  // -----------------------------------------------------------------------
  // Check 2: Schema validation for project.json
  // -----------------------------------------------------------------------
  if (absolutePath === PROJECT_JSON_PATH && resolved.complete && resolved.content) {
    try {
      const parsed = JSON.parse(resolved.content);
      const schemaResult = validators.validateProjectJson(parsed);
      if (!schemaResult.valid) {
        errors.push(...schemaResult.errors.map((e) => `Schema (project.json): ${e}`));
      }

      const stageKey = canonicalKey(currentStage, scope);
      if (stageKey && parsed.stages && parsed.stages[stageKey]) {
        const currentStatus = state.artifactVersions ? 'in_progress' : 'not_started';
        const newStatus = parsed.stages[stageKey].status;
        if (newStatus && newStatus !== currentStatus) {
          const transResult = validators.validateStatusTransition(currentStatus, newStatus);
          if (!transResult.valid) {
            errors.push(...transResult.errors.map((e) => `Transition: ${e}`));
          }
        }
      }
    } catch { /* JSON parse failure already reported by Check 1 */ }
  }

  // -----------------------------------------------------------------------
  // Check 3: Schema validation for workflow-state.json
  // -----------------------------------------------------------------------
  if (absolutePath === WORKFLOW_STATE_PATH && resolved.complete && resolved.content) {
    try {
      const parsed = JSON.parse(resolved.content);
      const wsResult = validators.validateWorkflowState(parsed);
      if (!wsResult.valid) {
        errors.push(...wsResult.errors.map((e) => `Schema (workflow-state): ${e}`));
      }
    } catch { /* already reported */ }
  }

  // -----------------------------------------------------------------------
  // Artifact-specific checks
  // -----------------------------------------------------------------------
  const artifactName = getArtifactName(absolutePath);

  if (artifactName) {
    // --- Check 4: Ownership — is this stage allowed to write this artifact? ---
    if (currentStage !== null && currentStage !== undefined) {
      const ownerResult = validators.validateArtifactOwnership(artifactName, currentStage, scope);
      if (!ownerResult.valid) {
        errors.push(...ownerResult.errors.map((e) => `Ownership: ${e}`));
      }
    }

    // --- Check 5: Verdict gate (Rule #6, hard-blocking) ---
    // The stage that OWNS this artifact must have a passing upstream review.
    const owningStage = require('./utils/config').ARTIFACT_OWNER[artifactName];
    if (owningStage && owningStage !== '*') {
      const verdictResult = validators.validateVerdictGate(owningStage, ARTIFACT_DIR);
      if (!verdictResult.valid) {
        errors.push(...verdictResult.errors.map((e) => `Verdict gate: ${e}`));
      }
    }

    // --- Check 6: Structural depth (ZERO SHORTCUTS policy) ---
    //
    // Split-PRD feature files collapse to `requirements.md` for ownership, but they
    // carry a part's structure, not the index's — so they get their own schema.
    // See the note on 'product-requirements-part.md' in utils/artifact-schema.js.
    const schemaName = /^product-requirements-.+\.md$/.test(path.basename(absolutePath))
      ? 'product-requirements-part.md'
      : artifactName;

    if (resolved.complete && typeof resolved.content === 'string') {
      const structResult = validateArtifact(schemaName, resolved.content);
      if (!structResult.valid) {
        errors.push(...structResult.errors.map((e) => `Depth/structure: ${e}`));
      }
      // Warnings are surfaced but never block.
      if (structResult.warnings.length) {
        log({
          hook: 'preToolUse',
          stage: canonicalKey(currentStage, scope),
          subStage: subStageOf(currentStage, scope),
          scope,
          artifact: artifactName,
          action: 'structure_warning',
          decision: 'allow',
          reason: structResult.warnings.join('; '),
        });
      }
    }
  }

  // -----------------------------------------------------------------------
  // Decision
  // -----------------------------------------------------------------------
  const logBase = {
    hook: 'preToolUse',
    stage: canonicalKey(currentStage, scope),
    subStage: subStageOf(currentStage, scope),
    scope,
    skill: state.currentSkill || null,
    artifact: artifactName || path.basename(absolutePath),
    duration: Date.now() - startTime,
  };

  if (errors.length > 0) {
    const reason =
      `Blocked by prd-to-prod workflow guards at ${stageLabel(currentStage, scope)}:\n` +
      errors.map((e) => `  • ${e}`).join('\n') +
      `\n\nFix the issues above and retry. Do not work around this by writing ` +
      `elsewhere or lowering the artifact's scope.`;

    log({ ...logBase, action: 'deny', decision: 'deny', reason: errors.join('; ') });
    io.respondPreToolUse('deny', reason);
  } else {
    log({ ...logBase, action: 'allow', decision: 'allow', reason: 'All validations passed' });
    io.respondPreToolUse('allow', 'All workflow validations passed');
  }
}

main().catch((err) => {
  log({
    hook: 'preToolUse',
    action: 'fatal_error',
    decision: 'allow',
    reason: `${err.message}\n${err.stack || ''}`,
  });
  io.respondPreToolUse('allow', `Hook error (failing open): ${err.message}`);
  process.exit(0);
});
