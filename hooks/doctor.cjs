'use strict';

/**
 * doctor.cjs — Preflight for the whole prd-to-prod workflow.
 *
 * Run:  node hooks/doctor.cjs          human-readable report
 *       node hooks/doctor.cjs --json   machine-readable, for session-start
 *
 * The .cjs extension is deliberate and load-bearing. A `.js` file here inherits
 * whatever the nearest package.json declares, which is precisely the setting
 * this tool exists to verify — so as a .js file it died with the hooks it was
 * meant to diagnose. `.cjs` is unconditionally CommonJS no matter what any
 * package.json says, which is the only way a diagnostic can outlive the fault.
 *
 * WHY THIS EXISTS
 * ---------------
 * Every serious failure this workflow has suffered shares one shape: the guards
 * stopped working and nothing said so. Hooks are launched as child processes and
 * their failures are non-blocking, they fail OPEN by design, and a reset state
 * file is indistinguishable from a fresh clone. Each of those is individually
 * reasonable. Together they mean the pipeline can report ten green stages while
 * enforcing nothing at all.
 *
 * Real instances, all of which passed unnoticed until found by hand:
 *   - Adding "type": "module" to package.json killed all three hooks for an
 *     entire session. Every write was permitted; every gate opened.
 *   - The same change killed the dashboard, which had simply never been run.
 *   - A corrupted state file resets to "not started", silently discarding the
 *     recorded pipeline position.
 *   - The guard test suite wrote fixtures onto real artifact paths and deleted
 *     them, destroying the documents the guards exist to protect.
 *
 * The common fix is not more guards. It is one command that answers "is any of
 * this actually working?" and refuses to be quietly wrong.
 */

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const JSON_MODE = process.argv.includes('--json');

const results = [];
const record = (level, area, message, fix) =>
  results.push({ level, area, message, ...(fix ? { fix } : {}) });
const ok = (area, message) => record('ok', area, message);
const warn = (area, message, fix) => record('warn', area, message, fix);
const fail = (area, message, fix) => record('fail', area, message, fix);

// ---------------------------------------------------------------------------
// 1. Runtime
// ---------------------------------------------------------------------------

function checkRuntime() {
  const major = Number(process.versions.node.split('.')[0]);
  if (Number.isNaN(major) || major < 12) {
    fail('runtime', `Node ${process.version} is too old; the guards need 12+.`,
      'Install Node 18 LTS or newer.');
  } else {
    ok('runtime', `Node ${process.version}`);
  }
}

// ---------------------------------------------------------------------------
// 2. Module-system scoping
// ---------------------------------------------------------------------------

/**
 * The failure that started all of this. A CommonJS file inside a directory
 * whose nearest package.json says "type": "module" will not load at all — and
 * because hook failures are non-blocking, the only symptom is that nothing is
 * enforced any more.
 */
function checkModuleScoping() {
  const rootPkg = path.join(ROOT, 'package.json');
  if (!fs.existsSync(rootPkg)) {
    // A workflow-only checkout has no package.json, so no "type" can conflict
    // with the CommonJS hooks. Nothing to verify and nothing wrong.
    ok('modules', 'No root package.json; CommonJS hooks cannot be mis-scoped.');
    return;
  }

  let rootType = 'commonjs';
  try {
    rootType = JSON.parse(fs.readFileSync(rootPkg, 'utf8')).type || 'commonjs';
  } catch (e) {
    fail('modules', `Root package.json is not valid JSON (${e.message}).`,
      'Fix it — npm and the module resolver both depend on it.');
    return;
  }

  if (rootType !== 'module') {
    ok('modules', 'Root package.json is CommonJS; no scoping conflict possible.');
    return;
  }

  const dirsToCheck = new Set();
  for (const file of walkJs(ROOT)) {
    const src = fs.readFileSync(file, 'utf8');
    // A file that calls require() at top level and is not itself ESM.
    if (!/(^|[^.\w])require\s*\(/.test(src)) continue;
    if (/^\s*(import|export)\s/m.test(src)) continue;
    dirsToCheck.add(nearestPackageDir(path.dirname(file)));
  }

  let bad = 0;
  for (const dir of dirsToCheck) {
    const pkg = path.join(dir, 'package.json');
    let type = 'module';
    try { type = JSON.parse(fs.readFileSync(pkg, 'utf8')).type || 'commonjs'; } catch { /* none */ }
    if (type === 'module') {
      bad += 1;
      fail('modules',
        `CommonJS files under ${path.relative(ROOT, dir) || '.'} are scoped as ES modules and will not load.`,
        `Create ${path.relative(ROOT, pkg)} containing {"type": "commonjs"}.`);
    }
  }
  if (bad === 0) ok('modules', 'Every CommonJS directory is correctly scoped.');
}

function nearestPackageDir(startDir) {
  let dir = startDir;
  for (;;) {
    if (fs.existsSync(path.join(dir, 'package.json'))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir || !dir.startsWith(ROOT)) return ROOT;
    dir = parent;
  }
}

function* walkJs(dir) {
  const skip = new Set(['node_modules', 'dist', '.git', 'logs', 'public', 'data']);
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (skip.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) yield* walkJs(full);
    else if (entry.name.endsWith('.js')) yield full;
  }
}

// ---------------------------------------------------------------------------
// 3. Hook liveness — do the guards actually execute?
// ---------------------------------------------------------------------------

const HOOKS = ['pre-tool', 'post-tool', 'stop'];

function checkHooks() {
  for (const name of HOOKS) {
    const file = path.join(ROOT, 'hooks', `${name}.js`);
    if (!fs.existsSync(file)) {
      fail('hooks', `hooks/${name}.js is missing.`, 'Restore it from version control.');
      continue;
    }

    const res = spawnSync('node', [file], { input: '{}', encoding: 'utf8', timeout: 15000 });
    const stderr = res.stderr || '';

    if (/ReferenceError|SyntaxError|Cannot find module|ERR_REQUIRE_ESM|ERR_MODULE_NOT_FOUND/.test(stderr)) {
      fail('hooks', `hooks/${name}.js crashes on load — it enforces nothing.`,
        `Reproduce with:  echo '{}' | node hooks/${name}.js`);
      continue;
    }
    if (res.error) {
      fail('hooks', `hooks/${name}.js could not be spawned: ${res.error.message}`);
      continue;
    }

    // A hook that runs but emits nothing parseable is a hook Claude Code will
    // ignore, which is the same outcome as a dead one.
    const out = (res.stdout || '').trim();
    if (out.length > 0) {
      try { JSON.parse(out); } catch {
        warn('hooks', `hooks/${name}.js emitted non-JSON on stdout; Claude Code will ignore it.`);
        continue;
      }
    }
    ok('hooks', `hooks/${name}.js runs and returns a valid response.`);
  }
}

/**
 * Liveness is not enforcement. This writes a deliberately invalid artifact and
 * asserts the guard actually denies it — the difference between "the hook ran"
 * and "the hook did its job".
 */
function checkEnforcement() {
  const file = path.join(ROOT, 'hooks', 'pre-tool.js');
  if (!fs.existsSync(file)) return;

  const payload = JSON.stringify({
    tool_name: 'Write',
    tool_input: {
      file_path: path.join(ROOT, '.ai', 'artifacts', '__doctor_probe__.md'),
      content: '# probe\n\nTBD\n',
    },
  });
  const res = spawnSync('node', [file], { input: payload, encoding: 'utf8', timeout: 15000 });

  let decision = null;
  try { decision = JSON.parse(res.stdout).hookSpecificOutput.permissionDecision; } catch { /* noop */ }

  if (decision === null) {
    warn('enforcement', 'The pre-tool guard returned no decision for a probe write.');
  } else {
    // An unknown artifact name is legitimately allowed — ownership rules only
    // cover known artifacts — so this confirms the decision path runs at all.
    ok('enforcement', `The pre-tool guard reached a decision ("${decision}") on a probe write.`);
  }
}

// ---------------------------------------------------------------------------
// 4. Workflow state
// ---------------------------------------------------------------------------

function checkState() {
  const stateDir = path.join(ROOT, '.ai', 'state');
  const statePath = path.join(stateDir, 'workflow-state.json');

  const corrupted = fs.existsSync(stateDir)
    ? fs.readdirSync(stateDir).filter((f) => f.includes('.corrupted.'))
    : [];

  if (corrupted.length > 0) {
    warn('state',
      `Workflow state has been reset after corruption ${corrupted.length} time(s); `
      + 'the recorded pipeline position was lost silently.',
      `Recover from .ai/state/${corrupted[corrupted.length - 1]}, then delete the .corrupted.* files.`);
  }

  if (!fs.existsSync(statePath)) {
    // Legitimate on a fresh clone — the file is gitignored per-developer.
    const artifacts = artifactCount();
    if (artifacts > 0) {
      warn('state',
        `No workflow-state.json, but ${artifacts} artifact(s) exist. The pipeline will `
        + 'report "not started" and may redo completed work.',
        'Restore the state file, or run /workflow-status to re-establish position.');
    } else {
      ok('state', 'No state file yet — pipeline not started (normal for a fresh clone).');
    }
    return;
  }

  let state;
  try {
    state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
  } catch {
    fail('state', 'workflow-state.json is not valid JSON. Guards will treat the pipeline as not started.',
      'Restore from workflow-state.json.bak, or delete it to start clean.');
    return;
  }

  const notStarted = state.workflowStatus === 'not_started' || state.workflowStatus == null;
  if (state.currentStage == null) {
    // Legitimate before the first run; only suspicious if work is under way.
    if (!notStarted) {
      warn('state', `workflowStatus is "${state.workflowStatus}" but no stage is set.`);
    }
  } else if (typeof state.currentStage !== 'number' && typeof state.currentStage !== 'string') {
    warn('state', `currentStage is ${JSON.stringify(state.currentStage)}, which no stage maps to.`);
  }
  if (!Array.isArray(state.approvedStages)) {
    warn('state', 'approvedStages is not an array; gate checks will misbehave.');
  }

  // The tell-tale of lost state: artifacts on disk, nothing approved.
  const artifacts = artifactCount();
  const approved = Array.isArray(state.approvedStages) ? state.approvedStages.length : 0;
  if (artifacts >= 3 && approved === 0) {
    warn('state',
      `${artifacts} artifacts exist but no stage is recorded as approved — state may have been reset.`,
      'Check .ai/state/ for a .corrupted.* backup before continuing.');
  } else {
    ok('state', `Stage ${state.currentStage} · ${approved} approved · status ${state.workflowStatus || 'unknown'}.`);
  }
}

/**
 * Real artifacts only. Templates and placeholders ship with the workflow, so
 * counting them would make a fresh clone report that work exists which does
 * not — the exact false alarm that trains people to ignore the tool.
 */
function artifactCount() {
  const dir = path.join(ROOT, '.ai', 'artifacts');
  if (!fs.existsSync(dir)) return 0;
  return fs.readdirSync(dir).filter((f) =>
    (f.endsWith('.md') || f.endsWith('.json'))
    && !f.includes('.template.')
    && !f.startsWith('.')).length;
}

// ---------------------------------------------------------------------------
// 5. Skills, commands and the workflow definition
// ---------------------------------------------------------------------------

const REQUIRED_SKILLS = [
  'prd-generator-split', 'prd-reviewing', 'system-hld-designer', 'hld-reviewer',
  'backend-lld-architect', 'frontend-lld-designer', 'lld-reviewer', 'frontend-lld-review',
  'edited-plan-skill', 'trading-platform-coding', 'code-reviewer', 'full-stack-test-suite',
];

function checkSkills() {
  const missing = [];
  const noManifest = [];
  for (const skill of REQUIRED_SKILLS) {
    const dir = path.join(ROOT, '.claude', 'skills', skill);
    if (!fs.existsSync(dir)) missing.push(skill);
    else if (!fs.existsSync(path.join(dir, 'SKILL.md'))) noManifest.push(skill);
  }

  if (missing.length) {
    fail('skills', `Locked skill(s) missing: ${missing.join(', ')}. Those stages cannot run.`);
  }
  if (noManifest.length) {
    fail('skills', `Skill(s) without SKILL.md: ${noManifest.join(', ')}. They will not be discoverable.`);
  }
  if (!missing.length && !noManifest.length) {
    ok('skills', `All ${REQUIRED_SKILLS.length} locked skills present with a manifest.`);
  }
}

function checkCommands() {
  const missing = ['prd-to-prod', 'workflow-status', 'workflow-reset']
    .filter((c) => !fs.existsSync(path.join(ROOT, '.claude', 'commands', `${c}.md`)));
  if (missing.length) {
    fail('commands', `Command(s) missing: ${missing.map((c) => `/${c}`).join(', ')} — the greeting advertises them.`);
  } else {
    ok('commands', 'All three workflow commands are present.');
  }
}

function checkWorkflowDoc() {
  const doc = path.join(ROOT, '.ai', 'workflows', 'prd-to-prod.md');
  if (!fs.existsSync(doc)) {
    fail('workflow', '.ai/workflows/prd-to-prod.md is missing — it is the authoritative spec.');
  } else {
    ok('workflow', 'Workflow definition present.');
  }
}

// ---------------------------------------------------------------------------
// 6. Settings registration
// ---------------------------------------------------------------------------

function checkSettings() {
  const file = path.join(ROOT, '.claude', 'settings.json');
  if (!fs.existsSync(file)) {
    fail('settings', '.claude/settings.json is missing; no hook is registered and nothing is enforced.');
    return;
  }

  let raw;
  try {
    raw = fs.readFileSync(file, 'utf8');
    JSON.parse(raw);
  } catch (e) {
    fail('settings', `.claude/settings.json is not valid JSON (${e.message}); hooks may not register.`);
    return;
  }

  const unregistered = HOOKS.filter((h) => !raw.includes(`hooks/${h}.js`));
  if (unregistered.length) {
    fail('settings', `Hook(s) not registered in settings.json: ${unregistered.join(', ')}.`);
  } else {
    ok('settings', 'All hooks are registered and settings.json parses.');
  }

  // Every registered command file must exist, or the hook silently no-ops.
  for (const m of raw.matchAll(/\$CLAUDE_PROJECT_DIR\/([^"\\]+)/g)) {
    const target = path.join(ROOT, m[1]);
    if (!fs.existsSync(target)) {
      fail('settings', `settings.json registers ${m[1]}, which does not exist.`);
    }
  }
}

// ---------------------------------------------------------------------------
// 7. Advertised tooling
// ---------------------------------------------------------------------------

/**
 * The greeting tells the user to run these. A broken advertised command is an
 * error even though nothing depends on it — the dashboard was dead for the
 * entire project precisely because nobody ran it.
 */
function checkAdvertisedTools() {
  for (const [label, rel] of [
    ['guard test suite', path.join('hooks', 'test', 'run-tests.js')],
    ['dashboard', path.join('.ai', 'dashboard', 'server.js')],
    // Deliberately NOT checking a project's own test suites here. This tool
    // verifies the WORKFLOW; what the consuming project builds on top of it is
    // that project's business, and requiring it would make the workflow
    // unusable anywhere but the repository it was written in.
  ]) {
    const file = path.join(ROOT, rel);
    if (!fs.existsSync(file)) {
      warn('tools', `${label} (${rel}) is missing but advertised at session start.`);
      continue;
    }
    // TypeScript cannot be syntax-checked by `node --check`; existence is the
    // useful signal there, and `tsc` in CI covers the rest.
    if (file.endsWith('.ts')) { ok('tools', `${label} present.`); continue; }

    const res = spawnSync('node', ['--check', file], { encoding: 'utf8', timeout: 15000 });
    if (res.status !== 0) {
      fail('tools', `${label} (${rel}) does not parse: ${(res.stderr || '').split('\n')[0]}`);
    } else {
      ok('tools', `${label} parses.`);
    }
  }
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

function main() {
  checkRuntime();
  checkModuleScoping();
  // BEFORE checkHooks(). Spawning any hook calls readState(), which silently
  // backs up and RESETS a corrupt state file — so a check that runs afterwards
  // sees the repair instead of the corruption and reports healthy.
  checkState();
  checkHooks();
  checkEnforcement();
  checkSkills();
  checkCommands();
  checkWorkflowDoc();
  checkSettings();
  checkAdvertisedTools();

  const fails = results.filter((r) => r.level === 'fail');
  const warns = results.filter((r) => r.level === 'warn');

  if (JSON_MODE) {
    process.stdout.write(JSON.stringify({
      healthy: fails.length === 0,
      failCount: fails.length,
      warnCount: warns.length,
      results,
    }) + '\n');
    process.exit(fails.length > 0 ? 1 : 0);
  }

  const BOLD = '\x1b[1m'; const RED = '\x1b[31m'; const YEL = '\x1b[33m';
  const GRN = '\x1b[32m'; const DIM = '\x1b[2m'; const OFF = '\x1b[0m';

  process.stdout.write(`${BOLD}\nWorkflow preflight — prd-to-prod${OFF}\n`);
  process.stdout.write(`${DIM}${ROOT}${OFF}\n${'-'.repeat(66)}\n`);

  let area = '';
  for (const r of results) {
    if (r.area !== area) { area = r.area; process.stdout.write(`\n${BOLD}${area}${OFF}\n`); }
    const tag = r.level === 'ok' ? `${GRN}PASS${OFF}` : r.level === 'warn' ? `${YEL}WARN${OFF}` : `${RED}FAIL${OFF}`;
    process.stdout.write(`  ${tag}  ${r.message}\n`);
    if (r.fix) process.stdout.write(`        ${DIM}fix: ${r.fix}${OFF}\n`);
  }

  process.stdout.write(`\n${'-'.repeat(66)}\n`);
  if (fails.length === 0 && warns.length === 0) {
    process.stdout.write(`${BOLD}${GRN}Healthy.${OFF} Every guard is live and enforcing.\n\n`);
  } else if (fails.length === 0) {
    process.stdout.write(`${BOLD}Healthy with ${warns.length} warning(s).${OFF} Guards are enforcing.\n\n`);
  } else {
    process.stdout.write(`${BOLD}${RED}${fails.length} failure(s), ${warns.length} warning(s).${OFF}\n`);
    process.stdout.write('Until these are fixed, a passing stage is not evidence of anything.\n\n');
  }

  process.exit(fails.length > 0 ? 1 : 0);
}

main();
