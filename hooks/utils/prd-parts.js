'use strict';

const fs = require('fs');
const path = require('path');

/**
 * Parse frontmatter parts list from a PRD index markdown file and check each part file.
 * @param {string} indexPath Absolute or relative path to the PRD index file
 * @returns {{ valid: boolean, errors: string[], parts: string[] }}
 */
function validatePrdParts(indexPath) {
  const errors = [];
  const parts = [];

  if (!fs.existsSync(indexPath)) {
    return { valid: false, errors: [`PRD index file does not exist: ${indexPath}`], parts };
  }

  let content;
  try {
    content = fs.readFileSync(indexPath, 'utf8');
  } catch (err) {
    return { valid: false, errors: [`Failed to read PRD index file: ${err.message}`], parts };
  }

  // Parse YAML frontmatter between initial --- and ---
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) {
    return { valid: true, errors: [], parts: [] };
  }

  const frontmatter = match[1];
  // Look for parts: block
  const partsMatch = frontmatter.match(/parts:\s*\n((?:\s*-\s*.*(?:\r?\n)?)*)/);
  if (!partsMatch) {
    return { valid: true, errors: [], parts: [] };
  }

  const partsLines = partsMatch[1].split(/\r?\n/);
  const specDir = path.dirname(indexPath);

  for (const line of partsLines) {
    const itemMatch = line.match(/\s*-\s*(.+)/);
    if (itemMatch) {
      const partName = itemMatch[1].trim().replace(/^['"]|['"]$/g, '');
      if (partName) {
        parts.push(partName);
        const partPath = path.isAbsolute(partName) ? partName : path.resolve(specDir, partName);
        if (!fs.existsSync(partPath)) {
          errors.push(`Missing PRD part file: ${partName} (listed in ${path.basename(indexPath)} parts:)`);
        } else {
          const stat = fs.statSync(partPath);
          if (stat.size === 0) {
            errors.push(`Empty PRD part file: ${partName} (0 bytes)`);
          }
        }
      }
    }
  }

  return { valid: errors.length === 0, errors, parts };
}

module.exports = { validatePrdParts };
