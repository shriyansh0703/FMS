'use strict';

/**
 * config.js — Central configuration for the workflow guard hooks.
 *
 * Contains all constants: stage definitions, skill locks, dependency graph,
 * artifact paths, and valid status transitions. Supports scope, sub-stages (3a, 3b, 5a, 5b, 5c),
 * and split PRD index files.
 */

const path = require('path');

const WORKSPACE_ROOT = path.resolve(__dirname, '..', '..');

// Stage & sub-stage keys
const STAGES = [
  'requirement',
  'prd_review',
  'hld',
  'hld_review',
  'lld_backend',
  'lld_frontend',
  'lld_consistency',
  'lld_review',
  'planning',
  'implementation',
  'review',
  'test',
  'security_review',
];

const STAGE_INDEX = Object.fromEntries(STAGES.map((s, i) => [s, i]));

const STAGE_NAMES = {
  requirement:     'Stage 1 — Requirement Analysis',
  prd_review:      'Stage 2 — PRD Review',
  hld:             'Stage 3 — High-Level Design (Unified System)',
  hld_review:      'Stage 4 — HLD Review',
  lld_backend:     'Stage 5a — Low-Level Design (Backend)',
  lld_frontend:    'Stage 5b — Low-Level Design (Frontend)',
  lld_consistency: 'Stage 5c — LLD Consistency Pass',
  lld_review:      'Stage 6 — LLD Review',
  planning:        'Stage 7 — Planning',
  implementation:  'Stage 8 — Implementation',
  review:          'Stage 9 — Code & Architecture Review',
  test:            'Stage 10 — QA Testing & Browser Validation',
  security_review: 'Stage 11 — Security Review',
};

// Skill mappings per sub-stage
const SKILL_MAP = {
  requirement:     ['prd-generator-split'],
  prd_review:      ['prd-reviewing'],
  hld:             ['system-hld-designer'],
  hld_review:      ['hld-reviewer'],
  lld_backend:     ['backend-lld-architect'],
  lld_frontend:    ['frontend-lld-designer'],
  lld_consistency: [], // orchestrator pass
  lld_review:      ['frontend-lld-review', 'lld-reviewer'],
  planning:        ['edited-plan-skill'],
  implementation:  ['trading-platform-coding'],
  review:          ['code-reviewer'],
  test:            ['full-stack-test-suite'],
  security_review: ['security-review'],
};

const ARTIFACT_DIR = path.join(WORKSPACE_ROOT, '.ai', 'artifacts');
const SPECS_DIR = path.join(WORKSPACE_ROOT, 'docs', 'specs');
const STAGES_DIR = path.join(WORKSPACE_ROOT, '.ai', 'stages');

/**
 * Every directory an artifact may legitimately be written to.
 *
 * The locked skills are intentionally left unmodified, so they write wherever
 * their own instructions say. Rather than forcing a canonical path into the
 * skills, the guards recognise an artifact by NAME within any of these roots.
 * `.ai/artifacts` remains the preferred location (see CLAUDE.md), but a skill
 * writing to `.ai/stages/architecture/hld-backend.md` is still tracked,
 * checksummed and validated instead of silently bypassing every guard.
 */
const ARTIFACT_SEARCH_DIRS = [ARTIFACT_DIR, STAGES_DIR, SPECS_DIR];

const STAGE_ARTIFACTS = {
  requirement:     ['requirements.md'],
  prd_review:      ['prd-review.md'],
  hld:             ['hld.md', 'tech-stack.md'],
  hld_review:      ['hld-review.md'],
  lld_backend:     ['lld-backend.md'],
  lld_frontend:    ['lld-frontend.md'],
  lld_consistency: ['lld.md'],
  lld_review:      ['lld-review.md'],
  planning:        ['planning.md', 'tasks.json'],
  implementation:  [],
  review:          ['review.md'],
  test:            ['test-report.md', 'browser-report.md'],
  security_review: ['security-review.md'],
};

const ARTIFACT_OWNER = {
  'requirements.md': 'requirement',
  'product-requirements.md': 'requirement',
  'prd-review.md': 'prd_review',
  'hld.md': 'hld',
  'tech-stack.md': 'hld',
  // Pre-merge artifacts. Kept so an old file still resolves to the
  // unified stage instead of erroring as an unknown artifact.
  'hld-backend.md': 'hld',
  'hld-frontend.md': 'hld',
  'hld-review.md': 'hld_review',
  'lld-backend.md': 'lld_backend',
  'lld-frontend.md': 'lld_frontend',
  'lld.md': 'lld_consistency',
  'lld-review.md': 'lld_review',
  'planning.md': 'planning',
  'tasks.json': 'planning',
  'review.md': 'review',
  'test-report.md': 'test',
  'browser-report.md': 'test',
  'security-review.md': 'security_review',
  // Orchestrator-maintained, appended to incrementally across the whole
  // pipeline. '*' means "any stage may write this".
  'traceability.md': '*',
};

/** Sentinel in ARTIFACT_OWNER meaning the artifact has no single owning stage. */
const ANY_STAGE = '*';

const DEPENDENCY_CHAIN = [
  'requirements.md',
  'prd-review.md',
  'hld.md',
  'tech-stack.md',
  'hld-review.md',
  'lld-backend.md',
  'lld-frontend.md',
  'lld.md',
  'lld-review.md',
  'planning.md',
  'tasks.json',
  'review.md',
  'test-report.md',
  'browser-report.md',
  'security-review.md',
];

/**
 * Artifacts that are out of scope and must never be treated as missing or
 * marked stale — a skipped sub-stage is not a defect.
 */
const OUT_OF_SCOPE_ARTIFACTS = {
  backend:  ['lld-frontend.md', 'lld.md'],
  frontend: ['lld-backend.md', 'lld.md'],
  fullstack: [],
};

/**
 * Downstream artifacts that must go STALE when `artifactName` changes.
 *
 * Now genuinely scope-aware: the `scope` parameter used to be accepted and
 * ignored, so a backend-only run would mark frontend artifacts stale even
 * though they should never exist.
 *
 * @param {string} artifactName
 * @param {string} scope 'backend'|'frontend'|'fullstack'
 * @returns {string[]}
 */
function getDownstreamArtifacts(artifactName, scope = 'fullstack') {
  const chain = [...DEPENDENCY_CHAIN];
  const idx = chain.indexOf(artifactName);
  if (idx === -1) return [];

  const excluded = new Set(OUT_OF_SCOPE_ARTIFACTS[scope] || []);
  return chain.slice(idx + 1).filter((a) => !excluded.has(a));
}

/**
 * The artifacts a given scope is actually expected to produce.
 * @param {string} scope
 * @returns {string[]}
 */
function getInScopeArtifacts(scope = 'fullstack') {
  const excluded = new Set(OUT_OF_SCOPE_ARTIFACTS[scope] || []);
  return DEPENDENCY_CHAIN.filter((a) => !excluded.has(a));
}

const VALID_TRANSITIONS = {
  not_started: ['in_progress'],
  in_progress: ['completed', 'blocked', 'not_started'],
  completed:   ['stale', 'in_progress'],
  blocked:     ['in_progress', 'not_started'],
  stale:       ['in_progress', 'not_started'],
};

const VALID_APPROVAL_STATUSES = [
  'pending',
  'approved',
  'rejected',
  'stale',
];

const STATE_DIR = path.join(WORKSPACE_ROOT, '.ai', 'state');
const PROJECT_JSON_PATH = path.join(STATE_DIR, 'project.json');
const WORKFLOW_STATE_PATH = path.join(STATE_DIR, 'workflow-state.json');
const LOG_DIR = path.join(WORKSPACE_ROOT, 'hooks', 'logs');
const LOG_FILE = path.join(LOG_DIR, 'hook-events.jsonl');

const PROTECTED_PATHS = [
  ARTIFACT_DIR,
  STATE_DIR,
  SPECS_DIR,
  STAGES_DIR,
];

/**
 * Is this path subject to workflow validation?
 *
 * STAGES_DIR is included because the reference run wrote every artifact there,
 * which meant `getArtifactName()` returned null and the guards passed the write
 * straight through — no checksum, no version, no staleness cascade.
 *
 * @param {string} absolutePath
 * @returns {boolean}
 */
function isProtectedPath(absolutePath) {
  const normalized = path.resolve(absolutePath);
  return PROTECTED_PATHS.some(
    (p) => normalized === p || normalized.startsWith(p + path.sep)
  );
}

/**
 * Resolve a written file to its canonical workflow artifact name, regardless of
 * which of the recognised artifact roots it was written into.
 *
 * Returns null for anything that is not a tracked artifact — including
 * SKILL.md, README.md and templates that live alongside artifacts — so those
 * writes pass through untouched.
 *
 * @param {string} absolutePath
 * @returns {string|null} e.g. 'hld-backend.md', or null
 */
function getArtifactName(absolutePath) {
  const normalized = path.resolve(absolutePath);
  const filename = path.basename(normalized);

  // Must live under a recognised artifact root — otherwise a stray src/review.md
  // would be mistaken for the Stage 9 artifact.
  const underArtifactRoot = ARTIFACT_SEARCH_DIRS.some(
    (dir) => normalized === dir || normalized.startsWith(dir + path.sep)
  );
  if (!underArtifactRoot) return null;

  // PRD index files carry two legitimate names.
  if (filename === 'product-requirements.md' || filename === 'requirements.md') {
    return 'requirements.md';
  }

  // Split PRD feature files map onto the PRD for ownership/staleness purposes.
  if (/^product-requirements-.+\.md$/.test(filename)) {
    return 'requirements.md';
  }

  // Anything explicitly owned by a stage.
  if (Object.prototype.hasOwnProperty.call(ARTIFACT_OWNER, filename)) {
    return filename;
  }

  // traceability.md is orchestrator-maintained but still tracked.
  if (filename === 'traceability.md') return 'traceability.md';

  return null;
}

/**
 * Locate an artifact on disk across all recognised roots.
 *
 * @param {string} artifactName
 * @returns {string|null} absolute path, or null if not found
 */
function findArtifact(artifactName) {
  const fsMod = require('fs');

  for (const dir of ARTIFACT_SEARCH_DIRS) {
    // Direct hit at the root of a search dir
    const direct = path.join(dir, artifactName);
    if (fsMod.existsSync(direct)) return direct;

    // One level down (.ai/stages/architecture/hld-backend.md,
    // docs/specs/001-foo/product-requirements.md)
    if (!fsMod.existsSync(dir)) continue;
    let entries;
    try {
      entries = fsMod.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const nested = path.join(dir, entry.name, artifactName);
      if (fsMod.existsSync(nested)) return nested;

      // PRD index lives under docs/specs/<slug>/product-requirements.md
      if (artifactName === 'requirements.md') {
        const prd = path.join(dir, entry.name, 'product-requirements.md');
        if (fsMod.existsSync(prd)) return prd;
      }
    }
  }
  return null;
}

module.exports = {
  WORKSPACE_ROOT,
  STAGES,
  STAGE_INDEX,
  STAGE_NAMES,
  SKILL_MAP,
  ARTIFACT_DIR,
  SPECS_DIR,
  STAGES_DIR,
  ARTIFACT_SEARCH_DIRS,
  STAGE_ARTIFACTS,
  ARTIFACT_OWNER,
  ANY_STAGE,
  DEPENDENCY_CHAIN,
  OUT_OF_SCOPE_ARTIFACTS,
  getDownstreamArtifacts,
  getInScopeArtifacts,
  findArtifact,
  VALID_TRANSITIONS,
  VALID_APPROVAL_STATUSES,
  STATE_DIR,
  PROJECT_JSON_PATH,
  WORKFLOW_STATE_PATH,
  LOG_DIR,
  LOG_FILE,
  PROTECTED_PATHS,
  isProtectedPath,
  getArtifactName,
};
