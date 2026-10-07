---
name: spear-using-spear
description: SPEAR session-start context; auto-loaded by the SessionStart hook, never invoked via slash command.
---

# SPEAR — Session Start Context

SPEAR = Spec-Proven Engineering with Architectural Requirements. Hybrid of Spec-Driven (EARS), TDD, and Hexagonal Architecture. This text is injected at every session start. Read it before acting.

## Cycle rules (NEVER TRUNCATED)

Five phases, executed in order, one task at a time:

1. `spec`    — Read/author the EARS requirement (`docs/requirements.md`) the task references.
2. `prove`   — Write a failing test that verifies the requirement (red). TDD tasks only.
3. `engine`  — Write minimum code to pass the test (green). TDD tasks only.
4. `arch`    — Enforce layer boundaries (domain <- application <- infrastructure) and framework-annotation denylist in `domain/**`.
5. `refine`  — Refactor, re-run full suite, mark task `[x]`, reset state to `idle`.

**Linear gating.** Each skill refuses to run unless the current phase equals its predecessor. Error form: `spear requires phase=<expected>; current phase=<actual>`. Phases carry a `-done` suffix on completion (e.g. `prove-done`) — that is what the successor gate checks.

**DOC/INFRA tasks skip prove/engine.** Path: `idle -> spec -> spec-done -> arch -> arch-done -> refine -> idle`. TDD path runs all five.

## Trigger matrix

| Skill | Predecessor phase | Fires when |
|---|---|---|
| spear:init   | — (idempotent)    | Bootstrapping a new SPEAR project |
| spear:spec   | `idle`            | Authoring/revising a REQ, drafting tasks |
| spear:prove  | `spec-done` (TDD) | Writing the failing test |
| spear:engine | `prove-done`      | Writing minimum code to green |
| spear:arch   | `engine-done` (TDD) or `spec-done` (DOC/INFRA) | Layer + annotation check |
| spear:refine | `arch-done`       | Refactor, close task, reset to idle |

## Principles

**Verify, don't guess.** Before writing/planning any code, verify every external fact. Evidence source order: `context7` MCP → on-disk sources (`node_modules`, `~/.gradle/caches`, site-packages) → web fetch of official docs → project codebase via grep/read/glob. Every task's `Evidence:` block must cite sources consulted; unmatched imports vs. evidence block hard-fail `prove`/`engine`/`arch`.

**Briefing contract (subagent dispatch).** Every agent dispatch MUST include: exact file paths; exact function/class signatures (pre-verified); the failing test (verbatim or file:name); acceptance criteria ("test X green; no other files changed"); forbidden actions ("no error handling not asserted by the test"); the task's `Evidence:` block.

**Task sizing.** Any task whose full briefing exceeds ~1500 tokens MUST be decomposed by `spear:spec` before dispatch.

## State machine

State is tracked in `.claude/spear-state.json` in the project root. This file is gitignored. Run the local helper with `node tools/spear/state.mjs <operation> <arguments>` from the repository root. Select the task with `state_task <taskId> <reqId>` while idle, before entering spec. Use `state_record_test <testFile> <testName> red|green` only during prove/engine, and `state_clear` only during refine. The helper validates transitions; it does not support arbitrary state-field edits. Record diagnostic details in task evidence instead.

Valid phases: `idle | spec | spec-done | prove | prove-done | engine | engine-done | arch | arch-done | refine`

## Deferral list

Defer the following (no SPEAR replacement):

- Brainstorming → use built-in ideation or brainstorming skills
- Writing plans → use plan-writing skills
- Executing plans → use subagent-driven-development
- Debugging → use systematic-debugging
- TDD (outside SPEAR projects) → use test-driven-development

Inside SPEAR projects, `spear:prove` supersedes `test-driven-development`.

## How to invoke SPEAR skills

SPEAR skills are loaded by name. Ask Codex to:

- "run spear:init" — Bootstrap a new SPEAR project
- "run spear:spec" — Author/revise requirements
- "run spear:prove" — Write the failing test
- "run spear:engine" — Write minimum implementation
- "run spear:arch" — Check layer boundaries
- "run spear:refine" — Refactor and complete task

**Always load spear-using-spear first** when entering a SPEAR project (Codex does this automatically via the SessionStart hook).

## Current phase detection

At the start of any SPEAR interaction, read `.claude/spear-state.json` from the project root (if it exists):

```bash
node -e "const fs=require('fs');const p='.claude/spear-state.json';const j=fs.existsSync(p)?JSON.parse(fs.readFileSync(p,'utf8')):{phase:'idle'};console.log('Phase:',j.phase,'Task:',j.currentTaskId||'none');"
```

If the file doesn't exist, the phase is `idle`.

## Truncation order (hook contract)

When this payload approaches 4096 bytes, the hook truncates in this order:

1. Full `tasks.md` body (replaced by count + current-task summary).
2. Historical probe results.
3. The deferral list above.

Cycle rules and the Current phase line below SHALL NEVER be truncated.

## Dynamic context (injected by SessionStart hook)

Probe results: {{PROBE_RESULTS}}
Current task: {{CURRENT_TASK}}
Current phase: {{CURRENT_PHASE}}
