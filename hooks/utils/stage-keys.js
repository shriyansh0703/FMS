'use strict';

/**
 * stage-keys.js — Single source of truth for stage identity.
 *
 * WHY THIS EXISTS
 * ---------------
 * workflow-state.json stores `currentStage` as a NUMBER (4) and `approvedStages`
 * as a MIXED array ([1, 2, "3a"]). config.js keys everything by NAME ('hld_review').
 * The result was that `STAGE_ARTIFACTS[state.currentStage]` evaluated to
 * `undefined`, and `|| []` silently swallowed it — so the "required artifact
 * exists" check in stop.js never executed a single time.
 *
 * Every stage comparison in the hooks now goes through canonicalKey() so a
 * number, a display label, and a sub-stage id all collapse to one canonical key.
 */

const CANONICAL = [
  { key: 'requirement',     num: 1,  sub: null, label: 'Stage 1 — Requirement Analysis' },
  { key: 'prd_review',      num: 2,  sub: null, label: 'Stage 2 — PRD Review' },
  { key: 'hld',             num: 3,  sub: null, label: 'Stage 3 — High-Level Design (Unified System)' },
  { key: 'hld_review',      num: 4,  sub: null, label: 'Stage 4 — HLD Review' },
  { key: 'lld_backend',     num: 5,  sub: '5a', label: 'Stage 5a — Low-Level Design (Backend)' },
  { key: 'lld_frontend',    num: 5,  sub: '5b', label: 'Stage 5b — Low-Level Design (Frontend)' },
  { key: 'lld_consistency', num: 5,  sub: '5c', label: 'Stage 5c — LLD Consistency Pass' },
  { key: 'lld_review',      num: 6,  sub: null, label: 'Stage 6 — LLD Review' },
  { key: 'planning',        num: 7,  sub: null, label: 'Stage 7 — Planning' },
  { key: 'implementation',  num: 8,  sub: null, label: 'Stage 8 — Implementation' },
  { key: 'review',          num: 9,  sub: null, label: 'Stage 9 — Code & Architecture Review' },
  { key: 'test',            num: 10, sub: null, label: 'Stage 10 — QA Testing & Browser Validation' },
  { key: 'security_review', num: 11, sub: null, label: 'Stage 11 — Security Review' },
];

const BY_KEY = Object.fromEntries(CANONICAL.map((s) => [s.key, s]));
const BY_SUB = Object.fromEntries(CANONICAL.filter((s) => s.sub).map((s) => [s.sub, s]));

/**
 * Stages a bare number cannot identify on its own. Only stage 5 is split now
 * (5a/5b/5c); stage 3 was merged into a single unified HLD, so a bare 3 is
 * unambiguous.
 */
const AMBIGUOUS_NUMS = new Set([5]);

/**
 * Collapse any stage identifier to its canonical key.
 *
 * Accepts: 'hld_review' | 4 | '4' | '3a' | 'Stage 3a — ...'
 *
 * @param {string|number} value
 * @param {string} [scope] 'backend'|'frontend'|'fullstack' — disambiguates bare 3 / 5
 * @returns {string|null} canonical key, or null if unresolvable
 */
function canonicalKey(value, scope) {
  if (value === null || value === undefined) return null;

  // Already canonical
  if (typeof value === 'string' && BY_KEY[value]) return value;

  // Sub-stage id ('3a', '5c')
  if (typeof value === 'string' && BY_SUB[value]) return BY_SUB[value].key;

  // Numeric or numeric-string
  const asNum = typeof value === 'number' ? value : Number(String(value).trim());
  if (Number.isInteger(asNum)) {
    if (AMBIGUOUS_NUMS.has(asNum)) {
      // Stage 5 only. Resolve via scope; backend wins for fullstack since 5a
      // runs before 5b.
      if (scope === 'frontend') return 'lld_frontend';
      if (scope === 'backend' || scope === 'fullstack') return 'lld_backend';
      return null; // genuinely ambiguous without scope — caller must handle
    }
    const match = CANONICAL.find((s) => s.num === asNum && !s.sub);
    return match ? match.key : null;
  }

  // Display label
  if (typeof value === 'string') {
    const match = CANONICAL.find((s) => s.label === value.trim());
    if (match) return match.key;
  }

  return null;
}

/**
 * Compare two stage identifiers of any form for equality.
 * @param {string|number} a
 * @param {string|number} b
 * @param {string} [scope]
 * @returns {boolean}
 */
function sameStage(a, b, scope) {
  const ka = canonicalKey(a, scope);
  const kb = canonicalKey(b, scope);
  return ka !== null && ka === kb;
}

/**
 * Does a list of stage identifiers (in any mixed form) contain the given stage?
 * Replaces the unreliable `approvedStages.includes(currentStage)`.
 *
 * @param {Array<string|number>} list
 * @param {string|number} value
 * @param {string} [scope]
 * @returns {boolean}
 */
function listIncludesStage(list, value, scope) {
  if (!Array.isArray(list)) return false;
  const target = canonicalKey(value, scope);
  if (target === null) return false;
  return list.some((entry) => canonicalKey(entry, scope) === target);
}

/**
 * Human-readable label for any stage identifier.
 * @param {string|number} value
 * @param {string} [scope]
 * @returns {string}
 */
function stageLabel(value, scope) {
  const key = canonicalKey(value, scope);
  return key && BY_KEY[key] ? BY_KEY[key].label : String(value);
}

/**
 * The sub-stage id ('3a') for a stage, or null.
 * @param {string|number} value
 * @param {string} [scope]
 * @returns {string|null}
 */
function subStageOf(value, scope) {
  const key = canonicalKey(value, scope);
  return key && BY_KEY[key] ? BY_KEY[key].sub : null;
}

/**
 * Ordering index — lets callers ask "is X downstream of Y".
 * @param {string|number} value
 * @param {string} [scope]
 * @returns {number} -1 if unresolvable
 */
function stageOrder(value, scope) {
  const key = canonicalKey(value, scope);
  return key ? CANONICAL.findIndex((s) => s.key === key) : -1;
}

/**
 * Which sub-stages are in scope. Used so a skipped sub-stage is never treated
 * as a missing or stale artifact.
 *
 * @param {string} scope 'backend'|'frontend'|'fullstack'
 * @returns {Set<string>} canonical keys that must run
 */
function inScopeStages(scope) {
  // The unified HLD (stage 3) and its review always run, whatever the scope.
  const always = [
    'requirement', 'prd_review', 'hld', 'hld_review', 'lld_review',
    'planning', 'implementation', 'review', 'test', 'security_review',
  ];
  const keys = new Set(always);
  if (scope === 'backend' || scope === 'fullstack') {
    keys.add('lld_backend');
  }
  if (scope === 'frontend' || scope === 'fullstack') {
    keys.add('lld_frontend');
  }
  if (scope === 'fullstack') {
    keys.add('lld_consistency');
  }
  return keys;
}

module.exports = {
  CANONICAL,
  canonicalKey,
  sameStage,
  listIncludesStage,
  stageLabel,
  subStageOf,
  stageOrder,
  inScopeStages,
};
