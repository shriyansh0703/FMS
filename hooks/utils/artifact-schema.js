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
 * Natural-language verdicts the locked reviewer skills actually emit, mapped onto
 * the canonical set.
 *
 * WHY THIS EXISTS
 * ---------------
 * The gate originally accepted only the three SCREAMING_CASE tokens. But
 * `hld-reviewer`, `lld-reviewer` and `frontend-lld-review` all emit
 * "Ready for Implementation / Ready with Conditions / Not Ready", and
 * hld-reviewer's rubric additionally uses "Approve / Approve with required
 * changes / Do not build from this yet". Under the strict-only rule, every
 * hld-review.md and lld-review.md write would have been DENIED as non-canonical
 * — the gate would have blocked the pipeline instead of guarding it.
 *
 * Skills are treated as immutable, so the parser adapts to them rather than the
 * reverse. Keys are compared uppercased with runs of non-alphanumerics collapsed
 * to single underscores.
 */
const VERDICT_ALIASES = {
  // -> APPROVED
  READY_FOR_IMPLEMENTATION: VERDICTS.APPROVED,
  APPROVE: VERDICTS.APPROVED,
  PASS: VERDICTS.APPROVED,
  READY: VERDICTS.APPROVED,

  // -> APPROVED_WITH_CONDITIONS
  READY_WITH_CONDITIONS: VERDICTS.APPROVED_WITH_CONDITIONS,
  APPROVE_WITH_REQUIRED_CHANGES: VERDICTS.APPROVED_WITH_CONDITIONS,
  APPROVED_WITH_REQUIRED_CHANGES: VERDICTS.APPROVED_WITH_CONDITIONS,
  CONDITIONAL_PASS: VERDICTS.APPROVED_WITH_CONDITIONS,

  // -> CHANGES_REQUESTED
  NOT_READY: VERDICTS.CHANGES_REQUESTED,
  DO_NOT_BUILD_FROM_THIS_YET: VERDICTS.CHANGES_REQUESTED,
  DO_NOT_BUILD: VERDICTS.CHANGES_REQUESTED,
  FAIL: VERDICTS.CHANGES_REQUESTED,
  REJECTED: VERDICTS.CHANGES_REQUESTED,
  BLOCKED: VERDICTS.CHANGES_REQUESTED,
};

/**
 * Normalize any verdict string to a canonical value, or null if unrecognised.
 * @param {string} raw
 * @returns {string|null}
 */
function normalizeVerdict(raw) {
  if (!raw) return null;
  const key = String(raw)
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (VERDICTS[key]) return VERDICTS[key];
  if (VERDICT_ALIASES[key]) return VERDICT_ALIASES[key];
  return null;
}

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

  // Stage 3 — the UNIFIED system HLD produced by `system-hld-designer`.
  // Covers client, services, data and infrastructure in one document, so the
  // floor is higher than either half-HLD it replaced, and the required-section
  // list spans both the client and server halves. A "unified" design that never
  // mentions the client layer is exactly the failure this catches.
  'hld.md': {
    minLines: 200,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['overview', 'goal'],
      ['requirement'],
      ['capacity', 'estimate', 'workload'],
      ['architecture'],
      ['component'],
      ['api'],
      ['data model', 'data storage', 'schema', 'partitioning'],
      ['client', 'rendering', 'offline'],
      ['caching', 'cache'],
      ['scaling', 'scale'],
      ['reliability', 'failure'],
      ['security', 'compliance'],
      ['observability', 'monitoring'],
      ['technology stack', 'tech stack', 'stack summary'],
      ['risk'],
    ],
    requireDiagram: true,
  },

  // Retained so an artifact from before the 3a/3b merge still validates rather
  // than erroring out. No longer produced by any stage.
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

  // Stage 7 — `edited-plan-skill` declares ten deliverables and states that an
  // output missing even one is "INCOMPLETE and INVALID". Seven of those are
  // marked STRICTLY MANDATORY; this contract enforces exactly those, so the
  // skill's own rule is checked at write time rather than trusted.
  'planning.md': {
    minLines: 100,
    enforceNoPlaceholders: true,
    requiredSections: [
      ['task breakdown', 'task'],
      ['dependency matrix', 'dependenc'],
      ['execution stage', 'topological'],
      ['critical path'],
      ['optimized execution', 'execution plan'],
      ['spark', 'architecture execution graph', 'execution graph'],
      ['coding agent execution rules', 'execution rules'],
    ],
    // Satisfied by either the Mermaid DAG (section 6) or the ASCII Spark DAG
    // (section 9) — the skill mandates both.
    requireDiagram: true,
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

  // Stage 8 — the two completion reports `trading-platform-coding` must persist
  // to disk. The skill declares both MANDATORY; before these entries existed the
  // claim was prose only, so a run that silently skipped Swagger generation or
  // the security scan loop advanced with nothing objecting.
  //
  // A task with no HTTP surface still writes swagger-verification.md, stating
  // `Validation status: N/A — no REST endpoints`. That is the skill's own
  // exemption, and writing it is what proves the gate was considered rather than
  // forgotten. Nothing waives the Security Report.
  'swagger-verification.md': {
    minLines: 8,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['swagger verification report', 'swagger', 'openapi'],
    ],
    requireStatusLine: {
      label: 'Validation status',
      passing: ['PASS', 'N/A'],
      hint:
        'Emit the `## Swagger Verification Report` from ' +
        'references/foundation/openapi-generation-pipeline.md Step 6. A FAIL must run ' +
        'that file\'s repair loop before the task completes; a task with no HTTP ' +
        'surface writes "Validation status: N/A — no REST endpoints".',
    },
  },

  'security-report.md': {
    minLines: 8,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['security report', 'security'],
    ],
    requireStatusLine: {
      label: 'Final status',
      passing: ['PASS'],
      hint:
        'Emit the `## Security Report` from SKILL.md\'s Mandatory Gates section. ' +
        'An unresolved Critical/High finding means the security compliance execution ' +
        'loop has not finished — fix at the root, rebuild, retest, re-scan. ' +
        '"PASS WITH USER-ACCEPTED FINDINGS" is permitted and must name the user\'s decision.',
    },
  },

  // Stage 11 — dedicated security review produced by `security-review`.
  // A review artifact: exempt from the placeholder ban because it legitimately
  // quotes defective code, but the verdict line is mandatory since it gates QA.
  'security-review.md': {
    minLines: 40,
    enforceNoPlaceholders: false,
    requiredSections: [
      ['verdict'],
      ['finding', 'vulnerab', 'issue'],
      ['scope of review', 'scope'],
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
 * Pull a `Label: value` status line out of a report body.
 *
 * The Stage 8 reports are structured plain-text blocks, not headed sections, so
 * the value lives on the same line as its label rather than under a heading.
 * Tolerates the emphasis and punctuation variants a model naturally produces:
 *   Validation status: PASS
 *   **Final status:** `PASS WITH USER-ACCEPTED FINDINGS`
 *   - Validation status — N/A — no REST endpoints
 *
 * @param {string} content
 * @param {string} label e.g. 'Validation status'
 * @returns {{found: boolean, raw: string|null}}
 */
function extractStatusLine(content, label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(
    '^[^\\S\\n]*' +                    // leading indent
    '(?:[-*+]\\s+)?' +                 // optional list bullet
    '(?:\\*\\*|__)?\\s*' +             // optional bold open
    escaped +
    '\\s*(?:\\*\\*|__)?\\s*[:\\u2014-]\\s*' + // colon, em dash or hyphen separator
    '(?:\\*\\*|__)?\\s*' +
    '[`"\'\\[]?\\s*' +                 // optional quote/backtick/bracket
    '([^\\n\\r]+)',                    // the rest of the line
    'im'
  );
  const m = content.match(re);
  if (!m) return { found: false, raw: null };
  const raw = m[1].trim().replace(/[`"'\]]+$/, '').trim();
  return { found: raw.length > 0, raw: raw || null };
}

/**
 * Does a status value start with one of the permitted tokens?
 *
 * Prefix matching, deliberately: the skills' formats append rationale after the
 * token ("PASS WITH USER-ACCEPTED FINDINGS — <decision>", "N/A — no REST
 * endpoints"), and the token is what the gate turns on.
 *
 * @param {string} raw
 * @param {string[]} passing
 * @returns {boolean}
 */
function statusIsPassing(raw, passing) {
  if (!raw) return false;
  const normalized = raw.trim().toUpperCase();
  return passing.some((token) => normalized.startsWith(token.toUpperCase()));
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

  // --- Status-line requirement (Stage 8 completion reports) ---
  if (schema.requireStatusLine) {
    const { label, passing, hint } = schema.requireStatusLine;
    const status = extractStatusLine(content, label);

    if (!status.found) {
      errors.push(
        `No "${label}:" line found. ${artifactName} is a gate report — it must state ` +
        `its outcome on a machine-readable line so the guard can enforce it. ${hint || ''}`.trim()
      );
    } else if (!statusIsPassing(status.raw, passing)) {
      errors.push(
        `"${label}: ${status.raw}" is not a passing outcome. Permitted: ` +
        `${passing.join(', ')}. ${hint || ''}`.trim()
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
  // Handles every shape the locked reviewers actually produce:
  //   **Verdict:** APPROVED
  //   **Verdict**: `Ready with Conditions`
  //   * **Status:** Ready for Implementation — **Score: 8.5/10**
  //   Final Verdict: Not Ready
  // The capture stops at an em dash, pipe, bracket or quote, so trailing score
  // and rationale text never leak into the verdict token.
  const re = new RegExp(
    '^[^\\S\\n]*' +                       // leading indent
    '(?:[-*+]\\s+)?' +                    // optional list bullet
    '(?:\\*\\*|__)?\\s*' +                // optional bold open
    '(?:final\\s+|overall\\s+)?' +        // "Final Verdict" / "Overall Status"
    '(?:verdict|status)' +
    '\\s*(?:\\*\\*|__)?\\s*:\\s*' +       // colon, either side of the bold close
    '(?:\\*\\*|__)?\\s*' +
    '[`"\'\\[]?\\s*' +                    // optional quote/backtick/bracket
    '([^\\n\\r\\u2014|`"\'\\]()*]+)',     // the verdict token itself
    'gim'
  );

  let match;
  let lastRaw = null;

  while ((match = re.exec(content)) !== null) {
    const raw = match[1].trim();
    if (!raw) continue;
    lastRaw = raw;
    const normalized = normalizeVerdict(raw);
    if (normalized) {
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
  VERDICT_ALIASES,
  normalizeVerdict,
  PLACEHOLDER_PATTERNS,
  validateArtifact,
  extractVerdict,
  extractStatusLine,
  statusIsPassing,
  extractScope,
  extractHeadings,
  hasDiagram,
  hasTable,
  findPlaceholders,
};
