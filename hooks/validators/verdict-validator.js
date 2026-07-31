'use strict';

/**
 * verdict-validator.js — Enforces prd-to-prod.md Rule #6:
 * "Verdict gates are hard-blocking. A CHANGES_REQUESTED verdict cannot be
 *  worked around by proceeding anyway."
 *
 * Previously this rule was prose with nothing parsing a verdict. Now: before a
 * downstream artifact may be written, the review that gates it must exist on
 * disk and carry a passing verdict.
 */

const fs = require('fs');
const path = require('path');
const { extractVerdict, VERDICTS } = require('../utils/artifact-schema');

/**
 * Which review artifact gates the production of each stage's artifact.
 * Keys are canonical stage keys (see utils/stage-keys.js).
 */
const GATING_REVIEW = {
  hld:             'prd-review.md',
  lld_backend:     'hld-review.md',
  lld_frontend:    'hld-review.md',
  lld_consistency: 'hld-review.md',
  planning:        'lld-review.md',
  test:            'review.md',
};

/**
 * Verdicts that permit downstream work to begin.
 * APPROVED_WITH_CONDITIONS passes because the reference workflow's reviewers
 * legitimately emit it ("Ready with Conditions") — the conditions are carried
 * forward as mandatory inputs to the next stage, not as a blocker.
 */
const PASSING = new Set([VERDICTS.APPROVED, VERDICTS.APPROVED_WITH_CONDITIONS]);

/**
 * Check whether the gating review for a stage permits writing its artifact.
 *
 * @param {string} stageKey Canonical stage key of the artifact being written
 * @param {string} artifactDir Absolute path to .ai/artifacts
 * @returns {{valid: boolean, errors: string[], skipped: boolean}}
 *   `skipped` is true when this stage has no gating review (nothing to check).
 */
function validateVerdictGate(stageKey, artifactDir) {
  const errors = [];
  const reviewFile = GATING_REVIEW[stageKey];

  if (!reviewFile) {
    return { valid: true, errors, skipped: true };
  }

  const reviewPath = path.join(artifactDir, reviewFile);

  if (!fs.existsSync(reviewPath)) {
    errors.push(
      `Gating review "${reviewFile}" does not exist. Stage "${stageKey}" cannot ` +
      `begin until that review has been produced and approved.`
    );
    return { valid: false, errors, skipped: false };
  }

  let content;
  try {
    content = fs.readFileSync(reviewPath, 'utf8');
  } catch (err) {
    errors.push(`Could not read gating review "${reviewFile}": ${err.message}`);
    return { valid: false, errors, skipped: false };
  }

  const verdict = extractVerdict(content);

  if (!verdict.found) {
    errors.push(
      `Gating review "${reviewFile}" carries no machine-readable verdict. ` +
      `Add a line "**Verdict:** APPROVED" (or CHANGES_REQUESTED) before proceeding.`
    );
    return { valid: false, errors, skipped: false };
  }

  if (!verdict.canonical) {
    errors.push(
      `Gating review "${reviewFile}" has non-canonical verdict "${verdict.raw}". ` +
      `Use exactly one of: ${Object.keys(VERDICTS).join(', ')}.`
    );
    return { valid: false, errors, skipped: false };
  }

  if (!PASSING.has(verdict.verdict)) {
    errors.push(
      `Gating review "${reviewFile}" verdict is ${verdict.verdict}. Rule #6 makes ` +
      `this hard-blocking — resolve the findings and re-run the review before ` +
      `starting stage "${stageKey}".`
    );
    return { valid: false, errors, skipped: false };
  }

  return { valid: true, errors, skipped: false };
}

module.exports = {
  validateVerdictGate,
  GATING_REVIEW,
  PASSING,
};
