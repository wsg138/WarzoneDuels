/**
 * EARS Validator — REQ-072
 *
 * Validates requirement entries in Markdown format against four canonical EARS patterns:
 * - Ubiquitous: `THE SYSTEM SHALL <response>`
 * - Event-driven: `WHEN <event> THE SYSTEM SHALL <response>`
 * - State-driven: `WHILE <state> THE SYSTEM SHALL <response>`
 * - Unwanted: `IF <unwanted> THEN THE SYSTEM SHALL <response>`
 *
 * The optional Feature pattern (`WHERE …`) is accepted without validation.
 *
 * Design decision: Node.js module for simpler regex + structured output,
 * matching existing Node test infrastructure. Exposes validate(text, filename)
 * returning { ok, errors[] } and CLI shim for shell integration.
 */

import fs from 'node:fs';
import path from 'node:path';

/**
 * Validate EARS requirement entries in Markdown text.
 *
 * @param {string} text - The Markdown content to validate
 * @param {string} filename - The filename (for error reporting)
 * @returns {{ ok: boolean, errors: Array<{id: string, file: string, line: number, reason: string}> }}
 */
export function validate(text, filename) {
  const errors = [];
  const lines = text.split('\n');

  // Pattern to match REQ headers: ### REQ-NNN — title
  const reqHeaderPattern = /^###\s+REQ-(\d+)\s/;

  let currentReqId = null;
  let currentReqLine = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const headerMatch = line.match(reqHeaderPattern);

    if (headerMatch) {
      if (currentReqId) {
        errors.push({id: currentReqId, file: filename, line: currentReqLine, reason: 'Missing EARS clause'});
      }
      // New REQ header found
      currentReqId = `REQ-${headerMatch[1]}`;
      currentReqLine = i + 1; // 1-based line numbering
      continue;
    }

    // Skip empty lines and headers
    if (!currentReqId || line.trim() === '' || line.startsWith('#')) {
      continue;
    }

    // We found the first non-empty, non-header line after a REQ header
    // This is the EARS clause to validate
    const clause = line.trim();

    // Strip leading pattern labels like **Ubiquitous.** or **Event-driven.**
    const cleanedClause = clause
      .replace(/^\*\*(Ubiquitous|Event-driven|State-driven|Unwanted|Feature)\.\*\*\s*/, '')
      .trim();

    // Check if it's a Feature pattern or WHERE clause (accepted without validation)
    if (cleanedClause.startsWith('WHERE ') || line.includes('**Feature.**')) {
      // Valid — no further validation needed
      currentReqId = null;
      currentReqLine = null;
      continue;
    }

    // Validate against the four canonical patterns
    const isValid =
      // Ubiquitous: THE SYSTEM SHALL …
      /^THE SYSTEM SHALL\s+/.test(cleanedClause) ||
      // Event-driven: WHEN … THE SYSTEM SHALL …
      /^WHEN\s+.+\s+THE SYSTEM SHALL\s+/.test(cleanedClause) ||
      // State-driven: WHILE … THE SYSTEM SHALL …
      /^WHILE\s+.+\s+THE SYSTEM SHALL\s+/.test(cleanedClause) ||
      // Unwanted: IF … THEN THE SYSTEM SHALL …
      /^IF\s+.+\s+THEN THE SYSTEM SHALL\s+/.test(cleanedClause);

    if (!isValid) {
      errors.push({
        id: currentReqId,
        file: filename,
        line: currentReqLine,
        reason: 'Clause does not match any canonical EARS pattern (Ubiquitous, Event-driven, State-driven, Unwanted)',
      });
    }

    // Mark as processed so we don't validate again
    currentReqId = null;
    currentReqLine = null;
  }

  if (currentReqId) {
    errors.push({id: currentReqId, file: filename, line: currentReqLine, reason: 'Missing EARS clause'});
  }

  return {
    ok: errors.length === 0,
    errors,
  };
}

/**
 * CLI shim: read a file and validate it.
 * Exit 0 on success, 1 on validation failure.
 * Print errors to stderr in format: filename:line: REQ-XXX: reason
 */
if (process.argv[1] && (import.meta.url === `file://${process.argv[1]}` || process.argv[1].endsWith('ears.mjs'))) {
  const filename = process.argv[2];

  if (!filename) {
    console.error('Usage: node ears.mjs <path>');
    process.exit(1);
  }

  try {
    const text = fs.readFileSync(filename, 'utf8');
    const result = validate(text, filename);

    if (!result.ok) {
      for (const error of result.errors) {
        console.error(`${error.file}:${error.line}: ${error.id}: ${error.reason}`);
      }
      process.exit(1);
    }
    process.exit(0);
  } catch (err) {
    console.error(`Error reading ${filename}: ${err.message}`);
    process.exit(1);
  }
}
