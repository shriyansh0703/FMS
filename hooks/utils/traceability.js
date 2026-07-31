'use strict';

/**
 * traceability.js — Coverage-matrix parsing and gap detection.
 *
 * DESIGN NOTE — why this does NOT auto-fill cells
 * -----------------------------------------------
 * prd-to-prod.md asks post-tool.js to "update traceability.md's relevant column
 * when a stage artifact is written". A hook cannot know which requirement maps
 * to which section of a design doc — only the author does. Auto-filling would
 * manufacture false coverage claims, which is strictly worse for output quality
 * than an empty cell: it makes the Stage 9 gap scan pass on a lie.
 *
 * So this module DETECTS and REPORTS instead: after a stage artifact is written
 * it tells the model exactly which cells it now owes, and at Stage 9 it hard-
 * blocks on any in-scope requirement with a hole. The nag is advisory; the
 * Stage 9 gate is not.
 */

const fs = require('fs');
const path = require('path');

/**
 * Column header keyword → the stage that fills it.
 * Matching is case-insensitive substring against the header cell text.
 */
const COLUMN_STAGES = [
  { keyword: 'hld coverage',  stages: ['hld'] },
  { keyword: 'lld coverage',  stages: ['lld_backend', 'lld_frontend', 'lld_consistency'] },
  { keyword: 'code coverage', stages: ['implementation'] },
  { keyword: 'test coverage', stages: ['test'] },
];

/**
 * Which column an artifact is responsible for populating.
 */
const ARTIFACT_COLUMN = {
  'hld.md':          'hld coverage',
  // pre-merge names, kept so an old matrix still maps correctly
  'hld-backend.md':  'hld coverage',
  'hld-frontend.md': 'hld coverage',
  'lld-backend.md':  'lld coverage',
  'lld-frontend.md': 'lld coverage',
  'lld.md':          'lld coverage',
  'test-report.md':  'test coverage',
};

/**
 * Parse the first markdown table in the traceability matrix.
 *
 * @param {string} content
 * @returns {{ok: boolean, headers: string[], rows: Array<{cells: string[], lineIndex: number}>, reason?: string}}
 */
function parseMatrix(content) {
  const lines = content.split('\n');
  let headerIdx = -1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    const next = (lines[i + 1] || '').trim();
    // A header row followed by a separator row (|---|---|)
    if (/^\|.*\|$/.test(line) && /^\|[\s:|-]+\|$/.test(next)) {
      headerIdx = i;
      break;
    }
  }

  if (headerIdx === -1) {
    return { ok: false, headers: [], rows: [], reason: 'No markdown table found' };
  }

  const splitRow = (line) =>
    line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());

  const headers = splitRow(lines[headerIdx]);
  const rows = [];

  for (let i = headerIdx + 2; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!/^\|.*\|$/.test(line)) break; // table ended
    rows.push({ cells: splitRow(line), lineIndex: i });
  }

  return { ok: true, headers, rows };
}

/**
 * Find the index of a column whose header contains the given keyword.
 * @param {string[]} headers
 * @param {string} keyword
 * @returns {number} -1 if absent
 */
function columnIndex(headers, keyword) {
  const k = keyword.toLowerCase();
  return headers.findIndex((h) => h.toLowerCase().includes(k));
}

/**
 * Is a cell effectively empty? Treats "—", "-", "n/a", and "" as empty.
 * @param {string} cell
 * @returns {boolean}
 */
function isEmptyCell(cell) {
  if (cell === undefined || cell === null) return true;
  const c = cell.trim().toLowerCase();
  return c === '' || c === '-' || c === '—' || c === 'n/a' || c === 'tbd';
}

/**
 * Which columns are in scope and therefore must be filled before Stage 10.
 * @param {string} scope 'backend'|'frontend'|'fullstack'
 * @returns {string[]} column keywords
 */
function requiredColumns(scope) {
  // Every scope needs HLD, LLD, Code and Test coverage — scope only decides
  // WHICH sub-stage artifact fills the HLD/LLD cell, not whether it's needed.
  return ['hld coverage', 'lld coverage', 'code coverage', 'test coverage'];
}

/**
 * Scan the matrix for in-scope requirements with missing coverage.
 * This backs the Stage 9 hard gate in prd-to-prod.md.
 *
 * @param {string} traceabilityPath Absolute path to traceability.md
 * @param {string} scope
 * @param {string[]} [onlyColumns] Restrict the scan (e.g. skip test coverage pre-Stage-10)
 * @returns {{ok: boolean, gaps: Array<{reqId: string, column: string}>, errors: string[]}}
 */
function findGaps(traceabilityPath, scope, onlyColumns) {
  const errors = [];
  const gaps = [];

  if (!fs.existsSync(traceabilityPath)) {
    return { ok: false, gaps, errors: [`traceability.md not found at ${traceabilityPath}`] };
  }

  let content;
  try {
    content = fs.readFileSync(traceabilityPath, 'utf8');
  } catch (err) {
    return { ok: false, gaps, errors: [`Could not read traceability.md: ${err.message}`] };
  }

  const parsed = parseMatrix(content);
  if (!parsed.ok) {
    return { ok: false, gaps, errors: [`traceability.md: ${parsed.reason}`] };
  }

  const cols = (onlyColumns && onlyColumns.length)
    ? onlyColumns
    : requiredColumns(scope);

  const reqIdIdx = columnIndex(parsed.headers, 'req');

  for (const row of parsed.rows) {
    const reqId = reqIdIdx >= 0 ? row.cells[reqIdIdx] : row.cells[0];
    if (!reqId || !/REQ-?\d+/i.test(reqId)) continue; // not a requirement row

    for (const colKeyword of cols) {
      const idx = columnIndex(parsed.headers, colKeyword);
      if (idx === -1) {
        // Column absent entirely — report once per column, not per row.
        if (!errors.includes(`Missing column: ${colKeyword}`)) {
          errors.push(`Missing column: ${colKeyword}`);
        }
        continue;
      }
      if (isEmptyCell(row.cells[idx])) {
        gaps.push({ reqId: reqId.trim(), column: colKeyword });
      }
    }
  }

  return { ok: errors.length === 0, gaps, errors };
}

/**
 * After a stage artifact is written, report which traceability cells the author
 * now owes. Advisory — surfaced to the model as additionalContext.
 *
 * @param {string} artifactName
 * @param {string} traceabilityPath
 * @returns {{owed: number, column: string|null, reqIds: string[]}}
 */
function pendingCells(artifactName, traceabilityPath) {
  const column = ARTIFACT_COLUMN[artifactName] || null;
  if (!column || !fs.existsSync(traceabilityPath)) {
    return { owed: 0, column, reqIds: [] };
  }

  let content;
  try {
    content = fs.readFileSync(traceabilityPath, 'utf8');
  } catch {
    return { owed: 0, column, reqIds: [] };
  }

  const parsed = parseMatrix(content);
  if (!parsed.ok) return { owed: 0, column, reqIds: [] };

  const idx = columnIndex(parsed.headers, column);
  if (idx === -1) return { owed: 0, column, reqIds: [] };

  const reqIdIdx = columnIndex(parsed.headers, 'req');
  const reqIds = [];

  for (const row of parsed.rows) {
    const reqId = (reqIdIdx >= 0 ? row.cells[reqIdIdx] : row.cells[0]) || '';
    if (!/REQ-?\d+/i.test(reqId)) continue;
    if (isEmptyCell(row.cells[idx])) reqIds.push(reqId.trim());
  }

  return { owed: reqIds.length, column, reqIds };
}

/**
 * Count requirement rows in the matrix — used to detect a Stage 1 matrix that
 * was never populated.
 * @param {string} traceabilityPath
 * @returns {number}
 */
function countRequirements(traceabilityPath) {
  if (!fs.existsSync(traceabilityPath)) return 0;
  try {
    const parsed = parseMatrix(fs.readFileSync(traceabilityPath, 'utf8'));
    if (!parsed.ok) return 0;
    const reqIdIdx = columnIndex(parsed.headers, 'req');
    return parsed.rows.filter((r) => {
      const id = (reqIdIdx >= 0 ? r.cells[reqIdIdx] : r.cells[0]) || '';
      return /REQ-?\d+/i.test(id);
    }).length;
  } catch {
    return 0;
  }
}

module.exports = {
  parseMatrix,
  columnIndex,
  isEmptyCell,
  requiredColumns,
  findGaps,
  pendingCells,
  countRequirements,
  ARTIFACT_COLUMN,
  COLUMN_STAGES,
};
