'use strict';

/**
 * artifact-schema.js — Structural depth requirements per artifact.
 *
 * WHY THIS EXISTS
 * ---------------
 * prd-to-prod.md's "ZERO LAZINESS & ZERO SHORTCUTS POLICY" was prose only. An
 * LLM that drifts shallow produced a thin artifact and nothing objected. This
 * module turns that policy into a write-time gate: an artifact missing its
 * mandatory sections, or padded with placeholders, is DENIED before it lands.
 *
 * TUNING
 * ------
 * `minLines` values are FLOORS that catch lazy output, not targets. They are
 * deliberately well below what the reference run produced (requirements.md was
 * 686 lines against a floor of 120). Raise them per-team if shallow artifacts
 * still get through; lower them if a genuinely small feature is being blocked.
 *
 * Section matching is case-insensitive substring matching against heading lines,
 * so "## 4. Database Schema Design (PostgreSQL)" satisfies "database schema".
 * Each entry in `requiredSections` is an ALTERNATIVES array — any one matches.
 */

const validateMarkdownStructure = require('../validators/markdown-validator');

/**
 * Canonical verdict vocabulary. Reviewers MUST emit one of these verbatim so
 * the gate is machine-checkable instead of prose-inferred.
 */
const VERDICTS = {
  APPROVED: 'APPROVED',
  APPROVED_WITH_CONDITIONS: 'APPROVED_WITH_CONDITIONS',
  CHANGES_REQUESTED: 'CHANGES_REQUESTED',
};

/**
 * High-confidence placeholder markers. Kept deliberately narrow: a false
 * positive here blocks legitimate work, which erodes trust in the gate faster
 * than a missed placeholder does.
 */
const PLACEHOLDER_PATTERNS = [
  { re: /\bTBD\b/i,                       label: 'TBD' },
  { re: /<\s*placeholder\s*>/i,           label: '<placeholder>' },
  { re: /\[\s*(fill in|your .*here)\s*\]/i, label: '[fill in]' },
  { re: /\bLorem ipsum\b/i,               label: 'Lorem ipsum' },
  { re: /\bXXXX+\b/,                      label: 'XXXX' },
  { re: /^\s*(\/\/|#)\s*\.\.\.\s*(rest|etc|and so on|remaining)/im, label: '"// ... rest" elision' },
  { re: /\b(implementation|details?|schema)\s+(omitted|elided|left as an exercise)\b/i, label: 'omitted implementation' },
];

/**
 * Per-artifact structural contract.
 *
 * enforceNoPlaceholders is true only for design/planning artifacts, where
 * prd-to-prod.md explicitly forbids "TBD" parameters and placeholder schemas.
 * Review artifacts are exempt because they legitimately quote defective text.
 */
const SCHEMAS = {
  'requirements.md': {
    minLines: 120,
    enforceNoPlaceholders: false, // a PRD may legitimately list open questions
    requiredSections: [
      ['functional requirement'],
      ['non-functional requirement', 'nfr'],
      ['user persona', 'persona'],
      ['user flow', 'flow'],
      ['edge case'],
      ['acceptance criteria', 'success metric', 'success criteria'],
      ['scope'],
    ],
    requireScopeField: true,
  },

  'prd-review.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['verdict'],
      ['finding', 'summary', 'issue'],
    ],
    requireVerdict: true,
  },

  'hld-backend.md': {
    minLines: 150,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['system context', 'context & boundaries', 'executive context'],
      ['component'],
      ['sequence diagram', 'sequence'],
      ['data model', 'database schema', 'schema design'],
      ['api specification', 'api spec', 'interface'],
      ['edge case', 'failure mode'],
      ['scalability', 'sla', 'performance'],
    ],
    requireDiagram: true,
  },

  'hld-frontend.md': {
    minLines: 150,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['system context', 'context & boundaries', 'executive context'],
      ['component'],
      ['sequence diagram', 'sequence', 'interaction'],
      ['state management', 'state'],
      ['api contract', 'api specification', 'integration'],
      ['edge case', 'failure mode'],
      ['accessibility', 'responsive', 'performance'],
    ],
    requireDiagram: true,
  },

  'tech-stack.md': {
    minLines: 40,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['architecture overview', 'overview'],
      ['infrastructure', 'topology', 'runtime'],
    ],
  },

  'hld-review.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['verdict'],
      ['traceability', 'requirement coverage', 'coverage'],
    ],
    requireVerdict: true,
  },

  'lld-backend.md': {
    minLines: 200,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['data model', 'schema'],
      ['api', 'endpoint', 'signature'],
      ['request', 'response'],
      ['error', 'exception'],
      ['validation', 'business rule'],
    ],
  },

  'lld-frontend.md': {
    minLines: 200,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['component spec', 'component'],
      ['state management', 'state'],
      ['type', 'model', 'interface'],
      ['api contract', 'api'],
      ['error', 'edge case'],
    ],
  },

  'lld.md': {
    minLines: 40,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['consistency', 'agreement', 'discrepanc'],
      ['api contract', 'api'],
    ],
  },

  'lld-review.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['verdict'],
      ['finding', 'issue', 'drift'],
    ],
    requireVerdict: true,
  },

  'planning.md': {
    minLines: 60,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['task'],
      ['dependenc'],
    ],
  },

  'review.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['verdict'],
      ['finding', 'issue'],
    ],
    requireVerdict: true,
  },

  'test-report.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['result', 'summary'],
      ['acceptance criteria', 'coverage'],
    ],
  },

  'browser-report.md': {
    minLines: 20,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['result', 'summary', 'scenario'],
    ],
  },

  'traceability.md': {
    minLines: 5,
    enforceNoPlaceholders: false,
    requiredSections: [],
    requireTable: true,
  },
};

/**
 * Extract heading lines from markdown, ignoring fenced code blocks so a `#`
 * comment inside a shell snippet is not mistaken for a heading.
 * @param {string} content
 * @returns {string[]}
 */
function extractHeadings(content) {
  const out = [];
  let inFence = false;
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (/^(```|~~~)/.test(trimmed)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    if (/^#{1,6}\s+/.test(trimmed)) out.push(trimmed);
  }
  return out;
}

/**
 * Strip fenced code blocks — used so placeholder scanning doesn't fire on
 * legitimate example code that a review artifact is quoting.
 * @param {string} content
 * @returns {string}
 */
function stripFences(content) {
  return content.replace(/```[\s\S]*?```/g, '').replace(/~~~[\s\S]*?~~~/g, '');
}

/**
 * Does the content contain a diagram (mermaid fence or ASCII box art)?
 * @param {string} content
 * @returns {boolean}
 */
function hasDiagram(content) {
  if (/```\s*mermaid/i.test(content)) return true;
  // ASCII diagrams: box-drawing characters or repeated arrow/pipe structure
  if (/[┌┐└┘├┤┬┴┼─│╔╗╚╝║═]/.test(content)) return true;
  const arrowLines = (content.match(/^\s*[|+][-=>\s|+]*[|+>]\s*$/gm) || []).length;
  return arrowLines >= 3;
}

/**
 * Does the content contain a markdown table?
 * @param {string} content
 * @returns {boolean}
 */
function hasTable(content) {
  return /^\s*\|.*\|\s*$/m.test(content) && /^\s*\|[\s:-]+\|/m.test(content);
}

/**
 * Find placeholder markers outside of fenced code.
 * @param {string} content
 * @returns {string[]} labels of markers found
 */
function findPlaceholders(content) {
  const prose = stripFences(content);
  const found = [];
  for (const { re, label } of PLACEHOLDER_PATTERNS) {
    if (re.test(prose)) found.push(label);
  }
  return found;
}

/**
 * Extract the declared `scope:` field from frontmatter or body.
 * @param {string} content
 * @returns {string|null}
 */
function extractScope(content) {
  const m = content.match(/^\s*scope\s*:\s*[`"']?(backend|frontend|fullstack)[`"']?\s*$/im);
  return m ? m[1].toLowerCase() : null;
}

/**
 * Validate an artifact's structure against its schema.
 *
 * @param {string} artifactName e.g. 'hld-backend.md'
 * @param {string} content Full post-write file content
 * @returns {{valid: boolean, errors: string[], warnings: string[]}}
 */
function validateArtifact(artifactName, content) {
  const errors = [];
  const warnings = [];

  const schema = SCHEMAS[artifactName];
  if (!schema) {
    // Unknown artifact — nothing to enforce, but don't fail it.
    return { valid: true, errors, warnings };
  }

  if (typeof content !== 'string') {
    return { valid: false, errors: ['Artifact content is not readable as text'], warnings };
  }

  // --- Depth floor ---
  const lineCount = content.split('\n').length;
  if (lineCount < schema.minLines) {
    errors.push(
      `Artifact is ${lineCount} lines; the ZERO-SHORTCUTS floor for ${artifactName} is ` +
      `${schema.minLines}. Expand the missing detail rather than lowering the bar.`
    );
  }

  // --- Required sections (case-insensitive, alternatives) ---
  const headings = extractHeadings(content).map((h) => h.toLowerCase());
  for (const alternatives of schema.requiredSections || []) {
    const satisfied = alternatives.some((alt) =>
      headings.some((h) => h.includes(alt.toLowerCase()))
    );
    if (!satisfied) {
      errors.push(`Missing required section: one of [${alternatives.join(' | ')}]`);
    }
  }

  // --- Exact-heading pass via the shared markdown validator ---
  if (schema.requiredHeadingsExact && schema.requiredHeadingsExact.length) {
    const res = validateMarkdownStructure(content, schema.requiredHeadingsExact);
    if (!res.valid) errors.push(...res.errors);
  }

  // --- Placeholders ---
  const placeholders = findPlaceholders(content);
  if (placeholders.length) {
    const msg = `Placeholder markers present: ${placeholders.join(', ')}`;
    if (schema.enforceNoPlaceholders) {
      errors.push(
        `${msg}. prd-to-prod.md forbids placeholder/TBD content in design artifacts — ` +
        `specify the real value.`
      );
    } else {
      warnings.push(msg);
    }
  }

  // --- Diagram requirement ---
  if (schema.requireDiagram && !hasDiagram(content)) {
    errors.push(
      'No diagram found. HLD artifacts must include sequence/component diagrams ' +
      '(mermaid fence or ASCII) — "skipping architectural diagrams is prohibited".'
    );
  }

  // --- Table requirement ---
  if (schema.requireTable && !hasTable(content)) {
    errors.push('No markdown table found; this artifact is defined as a table.');
  }

  // --- Verdict requirement ---
  if (schema.requireVerdict) {
    const verdict = extractVerdict(content);
    if (!verdict.found) {
      errors.push(
        'No machine-readable verdict found. Review artifacts must contain a line ' +
        `matching "**Verdict:** <${Object.keys(VERDICTS).join(' | ')}>" so the ` +
        'hard-blocking verdict gate can enforce it.'
      );
    } else if (!verdict.canonical) {
      errors.push(
        `Verdict "${verdict.raw}" is not canonical. Use exactly one of: ` +
        `${Object.keys(VERDICTS).join(', ')}.`
      );
    }
  }

  // --- Scope field requirement ---
  if (schema.requireScopeField && !extractScope(content)) {
    errors.push(
      'No `scope:` field found. Stage 1 must declare scope: backend | frontend | fullstack ' +
      '— it is the hard switch gating stages 3 and 5.'
    );
  }

  return { valid: errors.length === 0, errors, warnings };
}

/**
 * Parse the verdict out of a review artifact.
 *
 * Accepts the canonical form and tolerates the spacing/emphasis variants that
 * reviewers naturally produce:
 *   **Verdict:** APPROVED
 *   **Status:** `CHANGES_REQUESTED`
 *   Final Verdict: APPROVED_WITH_CONDITIONS
 *
 * @param {string} content
 * @returns {{found: boolean, canonical: boolean, verdict: string|null, raw: string|null}}
 */
function extractVerdict(content) {
  const re = /^[^\S\n]*(?:\*\*)?(?:final\s+)?(?:verdict|status)(?:\*\*)?\s*:\s*(?:\*\*)?[`"']?\s*([A-Za-z_ ]+?)\s*[`"']?(?:\*\*)?\s*(?:—|-|$)/gim;
  let match;
  let lastRaw = null;

  while ((match = re.exec(content)) !== null) {
    const raw = match[1].trim();
    lastRaw = raw;
    const normalized = raw.toUpperCase().replace(/\s+/g, '_');
    if (VERDICTS[normalized]) {
      return { found: true, canonical: true, verdict: normalized, raw };
    }
  }

  if (lastRaw) {
    return { found: true, canonical: false, verdict: null, raw: lastRaw };
  }
  return { found: false, canonical: false, verdict: null, raw: null };
}

module.exports = {
  SCHEMAS,
  VERDICTS,
  PLACEHOLDER_PATTERNS,
  validateArtifact,
  extractVerdict,
  extractScope,
  extractHeadings,
  hasDiagram,
  hasTable,
  findPlaceholders,
};
