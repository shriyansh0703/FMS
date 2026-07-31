'use strict';

/**
 * run-tests.js — Proves the workflow guards actually fire under Claude Code.
 *
 * Every test pipes a synthetic Claude Code hook payload into a real hook entry
 * point as a child process and asserts on the JSON it writes to stdout. Nothing
 * is mocked — this is the same code path Claude Code executes.
 *
 * Run:  node hooks/test/run-tests.js
 *
 * State safety: workflow-state.json is backed up before the run and restored
 * afterwards, and every fixture artifact is removed in a finally block.
 */

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const HOOKS = path.join(ROOT, 'hooks');
const ARTIFACTS = path.join(ROOT, '.ai', 'artifacts');
const STATE_PATH = path.join(ROOT, '.ai', 'state', 'workflow-state.json');

let passed = 0;
let failed = 0;
const failures = [];

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

/**
 * Run a hook with a payload and return its parsed stdout.
 */
function runHook(hookFile, payload) {
  const res = spawnSync('node', [path.join(HOOKS, hookFile)], {
    input: JSON.stringify(payload),
    encoding: 'utf8',
    timeout: 20000,
  });

  if (res.error) {
    return { _spawnError: res.error.message, _stderr: res.stderr };
  }

  const out = (res.stdout || '').trim();
  if (!out) return { _empty: true, _stderr: res.stderr };

  try {
    return JSON.parse(out);
  } catch {
    return { _unparseable: out, _stderr: res.stderr };
  }
}

function preToolPayload(toolName, toolInput) {
  return {
    session_id: 'test-session',
    transcript_path: '/tmp/transcript.jsonl',
    cwd: ROOT,
    hook_event_name: 'PreToolUse',
    tool_name: toolName,
    tool_input: toolInput,
  };
}

function postToolPayload(toolName, toolInput) {
  return {
    session_id: 'test-session',
    cwd: ROOT,
    hook_event_name: 'PostToolUse',
    tool_name: toolName,
    tool_input: toolInput,
    tool_response: { success: true },
  };
}

function stopPayload(stopHookActive) {
  return {
    session_id: 'test-session',
    transcript_path: '/tmp/transcript.jsonl',
    cwd: ROOT,
    hook_event_name: 'Stop',
    stop_hook_active: !!stopHookActive,
  };
}

function check(name, condition, detail) {
  if (condition) {
    passed++;
    console.log(`  \x1b[32mPASS\x1b[0m  ${name}`);
  } else {
    failed++;
    failures.push({ name, detail });
    console.log(`  \x1b[31mFAIL\x1b[0m  ${name}`);
    if (detail) console.log(`        ${detail}`);
  }
}

function decisionOf(result) {
  return result && result.hookSpecificOutput
    ? result.hookSpecificOutput.permissionDecision
    : undefined;
}

function reasonOf(result) {
  return (result && result.hookSpecificOutput
    ? result.hookSpecificOutput.permissionDecisionReason
    : '') || '';
}

function setState(patch) {
  const base = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8'));
  fs.writeFileSync(STATE_PATH, JSON.stringify({ ...base, ...patch }, null, 2) + '\n');
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const APPROVED_PRD_REVIEW = `# PRD Review

## Summary

The PRD is complete and testable across all ten requirements.

## Findings

No CRITICAL or HIGH findings remain after the iteration pass.

## Final Verdict

**Verdict:** APPROVED
`;

const REJECTED_PRD_REVIEW = `# PRD Review

## Summary

Two HIGH findings remain unresolved.

## Findings

- HIGH: acceptance criteria for EC-04 are not testable.
- HIGH: no failure mode specified for link expiry.

## Final Verdict

**Verdict:** CHANGES_REQUESTED
`;

/** A deep, well-formed backend HLD that should pass every structural check. */
function goodHld() {
  const body = [];
  body.push('# Backend High-Level Design — Test System\n');
  body.push('## 1. System Context & Boundaries\n');
  body.push('The service sits behind the API gateway and owns document storage.\n');
  body.push('```mermaid\nsequenceDiagram\n  Client->>API: POST /documents\n  API->>Store: persist\n  Store-->>API: id\n  API-->>Client: 201\n```\n');
  body.push('## 2. Component Hierarchy & Module Design\n');
  body.push('Ingestion, retrieval, linking and audit modules are separated.\n');
  body.push('## 3. Sequence Diagrams\n');
  body.push('```mermaid\nsequenceDiagram\n  Client->>API: GET /documents/{id}\n  API->>Store: fetch\n```\n');
  body.push('## 4. Database Schema Design\n');
  body.push('Tables: documents, document_links, audit_events.\n');
  body.push('## 5. API Specification Overview\n');
  body.push('Seven endpoints, all authenticated by scoped API key.\n');
  body.push('## 6. Edge Case Handling Strategy\n');
  body.push('Orphaned records reconcile via a nightly sweep.\n');
  body.push('## 7. Scalability, Latency & SLA\n');
  body.push('P95 metadata reads under 500 ms at 200 RPS.\n');
  // Pad past the 150-line depth floor with real prose.
  for (let i = 0; i < 150; i++) {
    body.push(`Design note ${i + 1}: constraint traced to REQ-${String((i % 10) + 1).padStart(3, '0')}.`);
  }
  return body.join('\n');
}

/** Same document, but with a TBD placeholder injected. */
function hldWithPlaceholder() {
  return goodHld().replace(
    'Tables: documents, document_links, audit_events.',
    'Tables: TBD — to be decided during LLD.'
  );
}

const createdFiles = [];

function writeFixture(relPath, content) {
  const abs = path.join(ROOT, relPath);
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  fs.writeFileSync(abs, content);
  createdFiles.push(abs);
  return abs;
}

function cleanupFixtures() {
  for (const f of createdFiles.reverse()) {
    try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch { /* ignore */ }
  }
  createdFiles.length = 0;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

function testPassthrough() {
  console.log('\n\x1b[1mPassthrough — unprotected paths must not be blocked\x1b[0m');

  const r = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ROOT, 'src', 'services', 'DocumentService.java'),
    content: 'public class DocumentService {}',
  }));
  check('Source file write is allowed', decisionOf(r) === 'allow',
    `got ${JSON.stringify(r)}`);

  const r2 = runHook('pre-tool.js', {
    ...preToolPayload('Bash', { command: 'ls -la' }),
  });
  check('Bash command is allowed (not a file write)', decisionOf(r2) === 'allow',
    `got ${JSON.stringify(r2)}`);
}

function testOutputSchema() {
  console.log('\n\x1b[1mOutput schema — must match Claude Code, not Antigravity\x1b[0m');

  const r = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ROOT, 'README.md'),
    content: '# hi',
  }));
  check('PreToolUse emits hookSpecificOutput.permissionDecision',
    r && r.hookSpecificOutput && typeof r.hookSpecificOutput.permissionDecision === 'string',
    `got ${JSON.stringify(r)}`);
  check('PreToolUse declares hookEventName',
    r && r.hookSpecificOutput && r.hookSpecificOutput.hookEventName === 'PreToolUse',
    `got ${JSON.stringify(r)}`);
}

function testOwnershipGate() {
  console.log('\n\x1b[1mOwnership gate — a stage may only write its own artifact\x1b[0m');

  // Pipeline sits at Stage 1, but we try to write the Stage 3a artifact.
  setState({ currentStage: 'requirement', scope: 'backend', workflowStatus: 'in_progress' });

  const r = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld(),
  }));

  check('Stage 1 writing the Stage 3a artifact is DENIED', decisionOf(r) === 'deny',
    `got ${decisionOf(r)}`);
  check('Denial names the ownership violation', /Ownership/i.test(reasonOf(r)),
    `reason: ${reasonOf(r).slice(0, 200)}`);
}

function testVerdictGate() {
  console.log('\n\x1b[1mVerdict gate — Rule #6 is hard-blocking\x1b[0m');

  setState({ currentStage: 'hld_backend', scope: 'backend', workflowStatus: 'in_progress' });

  // No gating review on disk at all.
  const r0 = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld(),
  }));
  check('HLD write DENIED when prd-review.md is absent', decisionOf(r0) === 'deny',
    `got ${decisionOf(r0)}`);

  // Gating review exists but says CHANGES_REQUESTED.
  writeFixture('.ai/artifacts/prd-review.md', REJECTED_PRD_REVIEW);
  const r1 = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld(),
  }));
  check('HLD write DENIED when prd-review is CHANGES_REQUESTED', decisionOf(r1) === 'deny',
    `got ${decisionOf(r1)}`);
  check('Denial cites the verdict gate', /CHANGES_REQUESTED/.test(reasonOf(r1)),
    `reason: ${reasonOf(r1).slice(0, 200)}`);

  // Gating review now APPROVED — the same write should pass.
  fs.writeFileSync(path.join(ARTIFACTS, 'prd-review.md'), APPROVED_PRD_REVIEW);
  const r2 = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld(),
  }));
  check('HLD write ALLOWED once prd-review is APPROVED', decisionOf(r2) === 'allow',
    `got ${decisionOf(r2)} — ${reasonOf(r2).slice(0, 300)}`);
}

function testDepthAndPlaceholders() {
  console.log('\n\x1b[1mZERO-SHORTCUTS gate — depth, sections, placeholders, diagrams\x1b[0m');

  setState({ currentStage: 'hld_backend', scope: 'backend', workflowStatus: 'in_progress' });
  fs.writeFileSync(path.join(ARTIFACTS, 'prd-review.md'), APPROVED_PRD_REVIEW);

  // Shallow artifact.
  const shallow = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: '# HLD\n\n## Components\n\nWe will use microservices.\n',
  }));
  check('Shallow HLD is DENIED', decisionOf(shallow) === 'deny', `got ${decisionOf(shallow)}`);
  check('Denial cites the depth floor', /floor|lines/i.test(reasonOf(shallow)),
    `reason: ${reasonOf(shallow).slice(0, 200)}`);
  check('Denial lists missing sections', /Missing required section/i.test(reasonOf(shallow)),
    `reason: ${reasonOf(shallow).slice(0, 300)}`);

  // Placeholder injected into an otherwise good artifact.
  const placeheld = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: hldWithPlaceholder(),
  }));
  check('HLD containing "TBD" is DENIED', decisionOf(placeheld) === 'deny',
    `got ${decisionOf(placeheld)}`);
  check('Denial names the placeholder', /TBD/.test(reasonOf(placeheld)),
    `reason: ${reasonOf(placeheld).slice(0, 200)}`);

  // Missing diagram.
  const noDiagram = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld().replace(/```mermaid[\s\S]*?```/g, 'See attached.'),
  }));
  check('HLD without a diagram is DENIED', decisionOf(noDiagram) === 'deny',
    `got ${decisionOf(noDiagram)}`);
}

function testVerdictFormatEnforcement() {
  console.log('\n\x1b[1mReview artifacts must carry a canonical verdict\x1b[0m');

  setState({ currentStage: 'prd_review', scope: 'backend', workflowStatus: 'in_progress' });

  const noVerdict = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'prd-review.md'),
    content: '# PRD Review\n\n## Summary\n\n' + 'Looks fine to me.\n'.repeat(45),
  }));
  check('Review with no verdict line is DENIED', decisionOf(noVerdict) === 'deny',
    `got ${decisionOf(noVerdict)}`);

  const badVerdict = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'prd-review.md'),
    content: '# PRD Review\n\n## Summary\n\n' + 'Detail line.\n'.repeat(45) +
             '\n## Final Verdict\n\n**Verdict:** `Ready with Conditions`\n',
  }));
  check('Non-canonical verdict ("Ready with Conditions") is DENIED',
    decisionOf(badVerdict) === 'deny', `got ${decisionOf(badVerdict)}`);

  const goodVerdict = runHook('pre-tool.js', preToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'prd-review.md'),
    content: '# PRD Review\n\n## Summary\n\n' + 'Detail line.\n'.repeat(45) +
             '\n## Findings\n\nNone outstanding.\n\n## Final Verdict\n\n**Verdict:** APPROVED_WITH_CONDITIONS\n',
  }));
  check('Canonical APPROVED_WITH_CONDITIONS is ALLOWED', decisionOf(goodVerdict) === 'allow',
    `got ${decisionOf(goodVerdict)} — ${reasonOf(goodVerdict).slice(0, 300)}`);
}

function testEditFragmentHandling() {
  console.log('\n\x1b[1mEdit handling — fragments must not cause false denials\x1b[0m');

  setState({ currentStage: 'hld_backend', scope: 'backend', workflowStatus: 'in_progress' });
  fs.writeFileSync(path.join(ARTIFACTS, 'prd-review.md'), APPROVED_PRD_REVIEW);
  writeFixture('.ai/artifacts/hld-backend.md', goodHld());

  // A tiny edit to a valid document. The fragment alone would fail every
  // structural check; reconstructing the full file must not.
  const r = runHook('pre-tool.js', preToolPayload('Edit', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    old_string: 'P95 metadata reads under 500 ms at 200 RPS.',
    new_string: 'P95 metadata reads under 400 ms at 250 RPS.',
  }));
  check('Small Edit to a valid artifact is ALLOWED', decisionOf(r) === 'allow',
    `got ${decisionOf(r)} — ${reasonOf(r).slice(0, 300)}`);

  // An edit that introduces a placeholder must still be caught.
  const bad = runHook('pre-tool.js', preToolPayload('Edit', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    old_string: 'Tables: documents, document_links, audit_events.',
    new_string: 'Tables: TBD.',
  }));
  check('Edit that introduces "TBD" is DENIED', decisionOf(bad) === 'deny',
    `got ${decisionOf(bad)}`);
}

function testStopHook() {
  console.log('\n\x1b[1mStop hook — must actually block, and must never loop\x1b[0m');

  // Loop guard takes precedence over everything.
  setState({
    currentStage: 'hld_backend', scope: 'backend', workflowStatus: 'in_progress',
    approvedStages: [], waitingForApproval: null,
  });
  const loop = runHook('stop.js', stopPayload(true));
  check('stop_hook_active=true always allows stop (no infinite loop)',
    !loop.decision, `got ${JSON.stringify(loop)}`);

  // Unapproved in-progress stage must block.
  const blocked = runHook('stop.js', stopPayload(false));
  check('Unapproved in-progress stage BLOCKS the stop', blocked.decision === 'block',
    `got ${JSON.stringify(blocked).slice(0, 200)}`);
  check('Block reason instructs the agent to open a gate',
    /AskUserQuestion/.test(blocked.reason || ''),
    `reason: ${(blocked.reason || '').slice(0, 200)}`);

  // Waiting on a human gate — stopping is correct.
  setState({ waitingForApproval: 'hld-backend.md' });
  const waiting = runHook('stop.js', stopPayload(false));
  check('Waiting on an approval gate ALLOWS the stop', !waiting.decision,
    `got ${JSON.stringify(waiting)}`);

  // Not started — nothing to enforce.
  setState({ currentStage: null, workflowStatus: 'not_started', waitingForApproval: null });
  const idle = runHook('stop.js', stopPayload(false));
  check('Not-started workflow ALLOWS the stop', !idle.decision,
    `got ${JSON.stringify(idle)}`);
}

function testStaleCascade() {
  console.log('\n\x1b[1mPostToolUse — metadata, checksums and staleness cascade\x1b[0m');

  setState({
    currentStage: 'prd_review', scope: 'backend', workflowStatus: 'in_progress',
    currentSkill: 'prd-reviewing', waitingForApproval: null,
  });

  const reviewPath = writeFixture('.ai/artifacts/prd-review.md', APPROVED_PRD_REVIEW);

  const r1 = runHook('post-tool.js', postToolPayload('Write', {
    file_path: reviewPath,
    content: APPROVED_PRD_REVIEW,
  }));
  check('PostToolUse returns a valid JSON object', r1 && !r1._unparseable && !r1._spawnError,
    `got ${JSON.stringify(r1).slice(0, 200)}`);

  const state1 = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8'));
  const entry = state1.artifactVersions['prd-review.md'];
  check('Artifact version recorded', entry && entry.version >= 1,
    `got ${JSON.stringify(entry)}`);
  check('Checksum recorded', entry && typeof entry.checksum === 'string' && entry.checksum.length > 10,
    `got ${entry && entry.checksum}`);
  check('Verdict recorded on the review artifact', entry && entry.verdict === 'APPROVED',
    `got ${entry && entry.verdict}`);

  // Now write a downstream artifact, then change the upstream one.
  writeFixture('.ai/artifacts/hld-backend.md', goodHld());
  runHook('post-tool.js', postToolPayload('Write', {
    file_path: path.join(ARTIFACTS, 'hld-backend.md'),
    content: goodHld(),
  }));

  fs.writeFileSync(reviewPath, APPROVED_PRD_REVIEW + '\n<!-- revised -->\n');
  const r2 = runHook('post-tool.js', postToolPayload('Write', {
    file_path: reviewPath,
    content: APPROVED_PRD_REVIEW + '\n<!-- revised -->\n',
  }));

  const state2 = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8'));
  check('Upstream change marks downstream STALE',
    (state2.staleArtifacts || []).includes('hld-backend.md'),
    `staleArtifacts: ${JSON.stringify(state2.staleArtifacts)}`);
  check('Staleness is surfaced back to the model',
    /STALE/i.test(JSON.stringify(r2)),
    `got ${JSON.stringify(r2).slice(0, 300)}`);

  // Scope-awareness: a backend run must not mark frontend artifacts stale.
  check('Out-of-scope frontend artifact NOT marked stale',
    !(state2.staleArtifacts || []).includes('hld-frontend.md'),
    `staleArtifacts: ${JSON.stringify(state2.staleArtifacts)}`);
}

function testStageKeyNormalisation() {
  console.log('\n\x1b[1mStage keys — the bug that silently disabled every check\x1b[0m');

  const sk = require(path.join(HOOKS, 'utils', 'stage-keys.js'));

  check('Numeric 4 resolves to hld_review', sk.canonicalKey(4) === 'hld_review',
    `got ${sk.canonicalKey(4)}`);
  check('Sub-stage "3a" resolves to hld_backend', sk.canonicalKey('3a') === 'hld_backend',
    `got ${sk.canonicalKey('3a')}`);
  check('Bare 3 + backend scope resolves to hld_backend',
    sk.canonicalKey(3, 'backend') === 'hld_backend', `got ${sk.canonicalKey(3, 'backend')}`);
  check('Bare 3 + frontend scope resolves to hld_frontend',
    sk.canonicalKey(3, 'frontend') === 'hld_frontend', `got ${sk.canonicalKey(3, 'frontend')}`);
  check('Mixed-type approvedStages match correctly',
    sk.listIncludesStage([1, 2, '3a'], 'hld_backend') === true,
    'listIncludesStage([1,2,"3a"], "hld_backend")');
  check('Unapproved stage does not falsely match',
    sk.listIncludesStage([1, 2, '3a'], 'hld_review') === false,
    'listIncludesStage([1,2,"3a"], "hld_review")');

  const cfg = require(path.join(HOOKS, 'utils', 'config.js'));
  check('STAGE_ARTIFACTS lookup now resolves (was always undefined)',
    Array.isArray(cfg.STAGE_ARTIFACTS[sk.canonicalKey(4)]),
    `got ${JSON.stringify(cfg.STAGE_ARTIFACTS[sk.canonicalKey(4)])}`);
}

function testTraceabilityGapScan() {
  console.log('\n\x1b[1mTraceability gap scan — the Stage 9 → 10 gate\x1b[0m');

  const trace = require(path.join(HOOKS, 'utils', 'traceability.js'));

  const withGaps = `# Traceability

| REQ ID | Summary | HLD Coverage | LLD Coverage | Code Coverage | Test Coverage |
|---|---|---|---|---|---|
| REQ-001 | Ingestion | hld-backend.md#3 | lld-backend.md#2 | src/Ingest.java | |
| REQ-002 | Retrieval | hld-backend.md#4 | | | |
`;
  const gapFile = writeFixture('.ai/artifacts/traceability.md', withGaps);

  const res = trace.findGaps(gapFile, 'backend', ['hld coverage', 'lld coverage', 'code coverage']);
  const gapReqs = [...new Set(res.gaps.map((g) => g.reqId))];

  check('REQ-002 flagged as having coverage gaps', gapReqs.includes('REQ-002'),
    `gaps: ${JSON.stringify(res.gaps)}`);
  check('REQ-001 not flagged (fully covered pre-QA)', !gapReqs.includes('REQ-001'),
    `gaps: ${JSON.stringify(res.gaps)}`);
  check('Exactly 2 gaps found for REQ-002 (LLD + Code)',
    res.gaps.filter((g) => g.reqId === 'REQ-002').length === 2,
    `gaps: ${JSON.stringify(res.gaps)}`);

  // And the Stop hook must block on it.
  setState({
    currentStage: 'review', scope: 'backend', workflowStatus: 'in_progress',
    approvedStages: ['review'], waitingForApproval: null, staleArtifacts: [],
  });
  const blocked = runHook('stop.js', stopPayload(false));
  check('Stop hook BLOCKS the Stage 9 handoff on traceability gaps',
    blocked.decision === 'block' && /[Tt]raceability/.test(blocked.reason || ''),
    `got ${JSON.stringify(blocked).slice(0, 300)}`);
}

// ---------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------

function main() {
  console.log('\x1b[1m\nWorkflow guard verification — Claude Code hook contract\x1b[0m');
  console.log(`Repo: ${ROOT}\n${'-'.repeat(64)}`);

  const stateBackup = fs.existsSync(STATE_PATH)
    ? fs.readFileSync(STATE_PATH, 'utf8')
    : null;

  try {
    testStageKeyNormalisation();
    testPassthrough();
    testOutputSchema();
    testOwnershipGate();
    testVerdictGate();
    testDepthAndPlaceholders();
    testVerdictFormatEnforcement();
    testEditFragmentHandling();
    testStopHook();
    testStaleCascade();
    testTraceabilityGapScan();
  } finally {
    cleanupFixtures();
    if (stateBackup !== null) {
      fs.writeFileSync(STATE_PATH, stateBackup);
    }
  }

  console.log('\n' + '-'.repeat(64));
  console.log(`\x1b[1mResult: ${passed} passed, ${failed} failed\x1b[0m`);

  if (failed > 0) {
    console.log('\nFailures:');
    for (const f of failures) {
      console.log(`  • ${f.name}`);
      if (f.detail) console.log(`      ${f.detail}`);
    }
    process.exit(1);
  }

  console.log('\nAll workflow guards verified against the Claude Code hook contract.');
  console.log('State restored; fixtures removed.\n');
}

main();
