'use strict';

/**
 * hook-io.js — Claude Code hook I/O adapter.
 *
 * The ONLY module that knows about Claude Code's wire format. Every hook entry
 * point goes through here, so porting to another runtime means changing this
 * file alone.
 *
 * Claude Code PreToolUse/PostToolUse input (stdin, JSON):
 *   {
 *     "session_id": "...",
 *     "transcript_path": "...",
 *     "cwd": "/abs/path",
 *     "hook_event_name": "PreToolUse",
 *     "tool_name": "Write" | "Edit" | "NotebookEdit" | "Bash",
 *     "tool_input": { ... },
 *     "tool_response": { ... }   // PostToolUse only
 *   }
 *
 * Stop input adds:
 *   { "stop_hook_active": true|false }
 *
 * Output shapes differ per event — see the respond* functions below.
 */

const fs = require('fs');
const path = require('path');

// ---------------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------------

/**
 * Read all of stdin as a string.
 * @returns {Promise<string>}
 */
function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

/**
 * Read and parse the hook payload from stdin.
 * @returns {Promise<{ok: boolean, input: Object|null, error: string|null}>}
 */
async function readInput() {
  try {
    const raw = await readStdin();
    if (!raw || !raw.trim()) {
      return { ok: false, input: null, error: 'Empty stdin' };
    }
    return { ok: true, input: JSON.parse(raw), error: null };
  } catch (err) {
    return { ok: false, input: null, error: err.message };
  }
}

/**
 * Extract the tool name from a Claude Code payload.
 * @param {Object} input
 * @returns {string}
 */
function getToolName(input) {
  return (input && input.tool_name) || 'unknown';
}

/**
 * Extract the tool input object from a Claude Code payload.
 * @param {Object} input
 * @returns {Object}
 */
function getToolInput(input) {
  return (input && input.tool_input) || {};
}

/**
 * Tools that write files and therefore need workflow validation.
 */
const FILE_WRITE_TOOLS = new Set(['Write', 'Edit', 'NotebookEdit', 'MultiEdit']);

/**
 * @param {string} toolName
 * @returns {boolean}
 */
function isFileWriteTool(toolName) {
  return FILE_WRITE_TOOLS.has(toolName);
}

/**
 * Extract the target file path for any file-writing tool.
 *
 * Write         -> file_path, content
 * Edit          -> file_path, old_string, new_string
 * MultiEdit     -> file_path, edits[]
 * NotebookEdit  -> notebook_path, new_source
 *
 * @param {string} toolName
 * @param {Object} toolInput
 * @returns {string|null} Path as given by the tool (may be relative).
 */
function getTargetFile(toolName, toolInput) {
  if (!toolInput) return null;
  return toolInput.file_path || toolInput.notebook_path || null;
}

/**
 * Resolve the FULL post-write content of the target file.
 *
 * This matters: for `Edit`, `new_string` is only a fragment. Handing that
 * fragment to a JSON or structure validator produces false failures. So for
 * edits we read the current file from disk and apply the replacement in memory
 * to reconstruct what the file WILL look like, then validate that.
 *
 * @param {string} toolName
 * @param {Object} toolInput
 * @param {string} absolutePath
 * @returns {{content: string|null, complete: boolean, reason: string|null}}
 *   `complete` is false when we could not reconstruct the whole file — callers
 *   must then skip whole-document validations rather than fail them.
 */
function resolveFullContent(toolName, toolInput, absolutePath) {
  // --- Write: content IS the whole file ---
  if (toolName === 'Write') {
    if (typeof toolInput.content === 'string') {
      return { content: toolInput.content, complete: true, reason: null };
    }
    return { content: null, complete: false, reason: 'Write had no content field' };
  }

  // --- NotebookEdit: not a workflow artifact format; skip ---
  if (toolName === 'NotebookEdit') {
    return { content: null, complete: false, reason: 'NotebookEdit is not validated' };
  }

  // --- Edit / MultiEdit: reconstruct from disk ---
  let current = '';
  try {
    if (fs.existsSync(absolutePath)) {
      current = fs.readFileSync(absolutePath, 'utf8');
    } else {
      return { content: null, complete: false, reason: 'Target file does not exist yet' };
    }
  } catch (err) {
    return { content: null, complete: false, reason: `Could not read target: ${err.message}` };
  }

  if (toolName === 'Edit') {
    const oldStr = toolInput.old_string;
    const newStr = toolInput.new_string;
    if (typeof oldStr !== 'string' || typeof newStr !== 'string') {
      return { content: null, complete: false, reason: 'Edit missing old_string/new_string' };
    }
    if (!current.includes(oldStr)) {
      // The edit will fail anyway; don't validate a reconstruction we can't trust.
      return { content: null, complete: false, reason: 'old_string not found in target' };
    }
    const replaced = toolInput.replace_all
      ? current.split(oldStr).join(newStr)
      : current.replace(oldStr, newStr);
    return { content: replaced, complete: true, reason: null };
  }

  if (toolName === 'MultiEdit') {
    const edits = Array.isArray(toolInput.edits) ? toolInput.edits : [];
    let working = current;
    for (const e of edits) {
      if (typeof e.old_string !== 'string' || typeof e.new_string !== 'string') {
        return { content: null, complete: false, reason: 'MultiEdit entry malformed' };
      }
      if (!working.includes(e.old_string)) {
        return { content: null, complete: false, reason: 'MultiEdit old_string not found' };
      }
      working = e.replace_all
        ? working.split(e.old_string).join(e.new_string)
        : working.replace(e.old_string, e.new_string);
    }
    return { content: working, complete: true, reason: null };
  }

  return { content: null, complete: false, reason: `Unhandled tool: ${toolName}` };
}

/**
 * Resolve a tool-provided path to an absolute path.
 * @param {string} targetFile
 * @param {string} workspaceRoot
 * @param {string} [cwd]
 * @returns {string}
 */
function toAbsolutePath(targetFile, workspaceRoot, cwd) {
  if (path.isAbsolute(targetFile)) return targetFile;
  return path.resolve(cwd || workspaceRoot, targetFile);
}

// ---------------------------------------------------------------------------
// Output
// ---------------------------------------------------------------------------

/**
 * Emit a PreToolUse decision.
 *
 * Claude Code expects:
 *   { "hookSpecificOutput": {
 *       "hookEventName": "PreToolUse",
 *       "permissionDecision": "allow" | "deny" | "ask",
 *       "permissionDecisionReason": "..." } }
 *
 * `deny` blocks the call and feeds the reason back to the model.
 *
 * @param {'allow'|'deny'|'ask'} decision
 * @param {string} [reason]
 */
function respondPreToolUse(decision, reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: decision,
      permissionDecisionReason: reason || '',
    },
  }) + '\n');
}

/**
 * Emit a PostToolUse result.
 *
 * `additionalContext` is surfaced to the model — used here to tell it when
 * staleness cascaded or traceability was updated, so it reacts instead of
 * silently continuing on outdated assumptions.
 *
 * @param {string} [additionalContext]
 */
function respondPostToolUse(additionalContext) {
  if (additionalContext) {
    process.stdout.write(JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'PostToolUse',
        additionalContext,
      },
    }) + '\n');
  } else {
    process.stdout.write(JSON.stringify({}) + '\n');
  }
}

/**
 * Emit a Stop decision.
 *
 * Claude Code expects `{"decision":"block","reason":"..."}` to PREVENT stopping;
 * the reason is fed to the model as its next instruction, so it must read as a
 * directive. Anything else (including `{}`) allows the stop.
 *
 * @param {boolean} block
 * @param {string} [reason]
 */
function respondStop(block, reason) {
  if (block) {
    process.stdout.write(JSON.stringify({
      decision: 'block',
      reason: reason || 'Workflow is not in a completable state.',
    }) + '\n');
  } else {
    process.stdout.write(JSON.stringify({}) + '\n');
  }
}

module.exports = {
  readInput,
  getToolName,
  getToolInput,
  isFileWriteTool,
  getTargetFile,
  resolveFullContent,
  toAbsolutePath,
  respondPreToolUse,
  respondPostToolUse,
  respondStop,
  FILE_WRITE_TOOLS,
};
