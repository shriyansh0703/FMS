'use strict';

/**
 * post-tool.js — Claude Code PostToolUse hook.
 *
 * Runs after a successful file write. Records artifact metadata (version,
 * checksum, timestamps), cascades staleness to downstream artifacts, validates
 * split-PRD completeness, and tells the model which traceability cells it now
 * owes.
 *
 * Registered in .claude/settings.json against matcher "Write|Edit|MultiEdit".
 *
 * Input:  Claude Code PostToolUse payload on stdin
 * Output: {} or { hookSpecificOutput: { additionalContext: '...' } }
 */

const path = require('path');
const {
  getArtifactName,
  ARTIFACT_OWNER,
  ARTIFACT_DIR,
  getDownstreamArtifacts,
  findArtifact,
  WORKSPACE_ROOT,
} = require('./utils/config');
const { hashFile } = require('./utils/checksum');
const { readState, updateState } = require('./utils/state-manager');
const { log } = require('./utils/logger');
const { validatePrdParts } = require('./utils/prd-parts');
const { canonicalKey, subStageOf } = require('./utils/stage-keys');
const { extractScope, extractVerdict } = require('./utils/artifact-schema');
const traceability = require('./utils/traceability');
const io = require('./utils/hook-io');

const fs = require('fs');

async function main() {
  const startTime = Date.now();

  const { ok, input, error } = await io.readInput();
  if (!ok) {
    log({
      hook: 'postToolUse',
      action: 'parse_error',
      decision: 'noop',
      reason: `Failed to parse stdin: ${error}`,
    });
    io.respondPostToolUse();
    return;
  }

  const toolName = io.getToolName(input);
  const toolInput = io.getToolInput(input);
  const cwd = input.cwd || WORKSPACE_ROOT;

  if (!io.isFileWriteTool(toolName)) {
    io.respondPostToolUse();
    return;
  }

  const targetFile = io.getTargetFile(toolName, toolInput);
  if (!targetFile) {
    io.respondPostToolUse();
    return;
  }

  const absolutePath = io.toAbsolutePath(targetFile, WORKSPACE_ROOT, cwd);
  const artifactName = getArtifactName(absolutePath);

  if (!artifactName) {
    io.respondPostToolUse();
    return;
  }

  const state = readState();
  const scope = state.scope || null;
  const owningStage = ARTIFACT_OWNER[artifactName] || null;
  const checksum = hashFile(absolutePath);
  const now = new Date().toISOString();

  // Messages surfaced back to the model.
  const notices = [];

  // -----------------------------------------------------------------------
  // PRD index: parts completeness + scope extraction
  // -----------------------------------------------------------------------
  let prdPartsInfo = null;
  let declaredScope = null;
  const basename = path.basename(absolutePath);

  if (basename === 'product-requirements.md' || basename === 'requirements.md') {
    prdPartsInfo = validatePrdParts(absolutePath);
    if (!prdPartsInfo.valid) {
      notices.push(
        `PRD split-file completeness FAILED: ${prdPartsInfo.errors.join('; ')}. ` +
        `Every file listed in the frontmatter \`parts:\` array must exist and be non-empty.`
      );
      log({
        hook: 'postToolUse',
        stage: owningStage,
        scope,
        artifact: artifactName,
        action: 'prd_parts_incomplete',
        decision: 'noop',
        reason: prdPartsInfo.errors.join('; '),
        duration: Date.now() - startTime,
      });
    }

    // Capture the declared scope — it is the hard switch for stages 3 and 5.
    try {
      declaredScope = extractScope(fs.readFileSync(absolutePath, 'utf8'));
    } catch { /* unreadable — leave null */ }
  }

  // -----------------------------------------------------------------------
  // Review artifacts: record the parsed verdict so the gate is queryable
  // -----------------------------------------------------------------------
  let recordedVerdict = null;
  if (/^(prd|hld|lld)-review\.md$|^review\.md$/.test(artifactName)) {
    try {
      const v = extractVerdict(fs.readFileSync(absolutePath, 'utf8'));
      if (v.found && v.canonical) {
        recordedVerdict = v.verdict;
        if (v.verdict === 'CHANGES_REQUESTED') {
          notices.push(
            `${artifactName} verdict is CHANGES_REQUESTED. Rule #6 makes this ` +
            `hard-blocking — downstream stages are now gated until the findings ` +
            `are resolved and the review is re-run.`
          );
        }
      }
    } catch { /* ignore */ }
  }

  // -----------------------------------------------------------------------
  // Update state
  // -----------------------------------------------------------------------
  try {
    updateState((s) => {
      if (!s.artifactVersions) s.artifactVersions = {};

      const prev = s.artifactVersions[artifactName];
      const previousChecksum = prev ? prev.checksum : null;

      s.artifactVersions[artifactName] = {
        version: prev && prev.version ? prev.version + 1 : 1,
        createdAt: prev && prev.createdAt ? prev.createdAt : now,
        updatedAt: now,
        stage: owningStage,
        subStage: subStageOf(owningStage, scope),
        status: (prdPartsInfo && !prdPartsInfo.valid) ? 'incomplete' : 'generated',
        checksum,
        lastModifiedBySkill: s.currentSkill || null,
        approvalStatus: 'pending',
        ...(recordedVerdict ? { verdict: recordedVerdict } : {}),
        ...(prdPartsInfo ? { parts: prdPartsInfo.parts, partsCompleteness: prdPartsInfo.valid } : {}),
      };

      // Promote a newly declared scope to the top level of the state file.
      if (declaredScope && s.scope !== declaredScope) {
        const previousScope = s.scope;
        s.scope = declaredScope;
        s.parts = prdPartsInfo ? prdPartsInfo.parts : [];
        notices.push(
          previousScope
            ? `Scope changed ${previousScope} -> ${declaredScope}. Per the Dependency ` +
              `Cascade rule, sub-stages that newly enter or leave scope must be ` +
              `re-approved; out-of-scope artifacts are marked STALE, never deleted.`
            : `Scope locked to "${declaredScope}". Only the matching 3a/3b and 5a/5b ` +
              `sub-stages may run.`
        );
      }

      // --- Scope-aware staleness cascade ---
      if (previousChecksum && checksum && checksum !== previousChecksum) {
        const downstream = getDownstreamArtifacts(artifactName, s.scope || 'fullstack');
        const newlyStale = [];

        for (const downArtifact of downstream) {
          const entry = s.artifactVersions[downArtifact];
          // Only cascade to artifacts that actually exist and were produced.
          if (entry && entry.version > 0 && entry.status !== 'not_created') {
            entry.status = 'stale';
            entry.approvalStatus = 'stale';
            entry.updatedAt = now;
            newlyStale.push(downArtifact);
          }
        }

        if (!s.staleArtifacts) s.staleArtifacts = [];
        s.staleArtifacts = [...new Set([...s.staleArtifacts, ...newlyStale])];

        if (newlyStale.length > 0) {
          notices.push(
            `${artifactName} changed — downstream artifacts marked STALE: ` +
            `${newlyStale.join(', ')}. They must be regenerated and re-approved ` +
            `before the workflow can complete.`
          );
          log({
            hook: 'postToolUse',
            stage: owningStage,
            scope: s.scope,
            artifact: artifactName,
            action: 'cascade_stale',
            decision: 'noop',
            reason: `Marked STALE: ${newlyStale.join(', ')}`,
            duration: Date.now() - startTime,
          });
        }
      }

      // This artifact was just regenerated — it is no longer stale.
      if (s.staleArtifacts) {
        s.staleArtifacts = s.staleArtifacts.filter((a) => a !== artifactName);
      }
    });

    log({
      hook: 'postToolUse',
      stage: owningStage,
      subStage: subStageOf(owningStage, scope),
      scope,
      skill: state.currentSkill || null,
      artifact: artifactName,
      action: 'update_metadata',
      decision: 'noop',
      reason: `Checksum ${checksum ? checksum.slice(0, 12) + '…' : 'null'}`,
      duration: Date.now() - startTime,
    });
  } catch (err) {
    log({
      hook: 'postToolUse',
      stage: owningStage,
      scope,
      artifact: artifactName,
      action: 'state_update_error',
      decision: 'noop',
      reason: err.message,
      duration: Date.now() - startTime,
    });
  }

  // -----------------------------------------------------------------------
  // Traceability: report the cells this artifact now owes.
  //
  // Deliberately advisory, not auto-filled — see utils/traceability.js for why
  // manufacturing coverage links would be worse than leaving them empty.
  // -----------------------------------------------------------------------
  const tracePath = findArtifact('traceability.md') || path.join(ARTIFACT_DIR, 'traceability.md');
  const pending = traceability.pendingCells(artifactName, tracePath);

  if (pending.owed > 0) {
    const shown = pending.reqIds.slice(0, 12).join(', ');
    const more = pending.reqIds.length > 12 ? ` (+${pending.reqIds.length - 12} more)` : '';
    notices.push(
      `Traceability: "${pending.column}" is still empty for ${pending.owed} ` +
      `requirement(s): ${shown}${more}. Update traceability.md now — Stage 9 ` +
      `hard-blocks on any in-scope requirement with an empty coverage cell.`
    );
  }

  io.respondPostToolUse(notices.length ? notices.join('\n\n') : undefined);
}

main().catch((err) => {
  log({
    hook: 'postToolUse',
    action: 'fatal_error',
    decision: 'noop',
    reason: `${err.message}\n${err.stack || ''}`,
  });
  io.respondPostToolUse();
  process.exit(0);
});
